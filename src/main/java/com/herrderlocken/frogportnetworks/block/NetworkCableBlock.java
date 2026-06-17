package com.herrderlocken.frogportnetworks.block;

import com.herrderlocken.frogportnetworks.blockentity.NetworkCableBlockEntity;
import com.herrderlocken.frogportnetworks.network.CableType;
import com.herrderlocken.frogportnetworks.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NetworkCableBlock — mehrteilige, flach an Flächen klebende Kabel.
 *
 * Die Daten liegen in der {@link NetworkCableBlockEntity}; gerendert wird per
 * BlockEntityRenderer (Render-Shape INVISIBLE). Färben (Rechtsklick mit Farbstoff)
 * färbt die ganze verbundene Strecke; Einzel-Strang-Abbau passiert über
 * {@code BlockEvent.BreakEvent} (siehe Haupt-Mod-Klasse).
 */
public class NetworkCableBlock extends BaseEntityBlock {

    public static final MapCodec<NetworkCableBlock> CODEC = simpleCodec(NetworkCableBlock::new);

    private static final VoxelShape FALLBACK = Block.box(6.5, 0, 6.5, 9.5, 2.5, 9.5);

    public NetworkCableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkCableBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof NetworkCableBlockEntity be) {
            return be.getShape();
        }
        return FALLBACK;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    // === Färben ===

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye)
                || !(level.getBlockEntity(pos) instanceof NetworkCableBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Den exakten Trefferpunkt der GUI verwenden → trifft zuverlässig den anvisierten Strang.
        DyeColor target = pickStrandAt(be, hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if (target == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (target == dye.getDyeColor()) return ItemInteractionResult.CONSUME;

        if (!level.isClientSide) {
            floodRecolor(level, pos, target, dye.getDyeColor());
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Färbt die komplette zusammenhängende Strecke einer Farbe um. */
    private static void floodRecolor(Level level, BlockPos start, DyeColor from, DyeColor to) {
        if (from == to) return;
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> run = new ArrayList<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (!(level.getBlockEntity(p) instanceof NetworkCableBlockEntity be) || !be.hasSegment(from)) continue;
            run.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (visited.add(n)
                        && level.getBlockEntity(n) instanceof NetworkCableBlockEntity nbe
                        && nbe.hasSegment(from)) {
                    queue.add(n);
                }
            }
        }
        for (BlockPos p : run) {
            if (level.getBlockEntity(p) instanceof NetworkCableBlockEntity be) {
                be.recolor(from, to);
            }
        }
    }

    // === Sub-Hit-Picking (welchen Strang visiert der Spieler an?) ===

    /** Strang, den der Spieler anvisiert: Blickstrahl auf die Gesamt-Form clippen, dann per Punkt wählen. */
    @Nullable
    public static DyeColor pickStrandLookingAt(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof NetworkCableBlockEntity be)) return null;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.blockInteractionRange() + 1.0));
        BlockHitResult clip = be.getShape().clip(eye, end, pos);
        if (clip == null) return null;
        return pickStrandAt(be, clip.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
    }

    /** Strang am gegebenen lokalen Punkt (0..1), per kleinster Punkt-zu-Form-Distanz. */
    @Nullable
    public static DyeColor pickStrandAt(NetworkCableBlockEntity be, Vec3 local) {
        DyeColor best = null;
        double bestDist = Double.MAX_VALUE;
        for (DyeColor color : be.getColors()) {
            for (AABB box : be.getColorShape(color).toAabbs()) {
                double d = distSqToAabb(local, box);
                if (d < bestDist) {
                    bestDist = d;
                    best = color;
                }
            }
        }
        return best;
    }

    private static double distSqToAabb(Vec3 p, AABB box) {
        double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Beim Abbau des ganzen Blocks (letzter Strang / Creative-Pick) alle Stränge droppen. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof NetworkCableBlockEntity be) {
            for (DyeColor color : be.getColors()) {
                Block.popResource(level, pos, ModItems.cableStack(be.getType(color), 1));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public static boolean isNetworkDevice(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof NetworkCableBlock
                || state.getBlock() instanceof RouterBlock
                || state.getBlock() instanceof NASBlock
                || state.getBlock() instanceof TerminalBlock;
    }
}
