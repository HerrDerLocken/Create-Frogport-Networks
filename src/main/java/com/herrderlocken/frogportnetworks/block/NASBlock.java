package com.herrderlocken.frogportnetworks.block;

import com.herrderlocken.frogportnetworks.blockentity.NASBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.mojang.serialization.MapCodec;
/**
 * NASBlock — Network Attached Storage.
 *
 * Speichert Items und macht sie über das Netzwerk verfügbar.
 * Andere Geräte (Terminals) können Items aus dem NAS anfordern,
 * solange eine gültige Netzwerkroute existiert.
 *
 * Jeder NAS hat eine eigene IP-Adresse und ein Item-Inventar.
 * Rechtsklick öffnet das NAS-Inventar direkt (wie eine Kiste).
 */
public class NASBlock extends BaseEntityBlock {

    public static final MapCodec<NASBlock> CODEC = simpleCodec(NASBlock::new);
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    public NASBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NASBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // TODO: NAS-GUI öffnen (Phase 2)
            // Zeigt Inventar + Netzwerk-Info (IP, verbundene Geräte)
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Wenn der Block abgebaut wird, sollen die gespeicherten Items droppen.
     * Ohne diesen Override würden die Items im NAS verloren gehen!
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof NASBlockEntity nasEntity) {
                nasEntity.dropContents(level, pos);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
