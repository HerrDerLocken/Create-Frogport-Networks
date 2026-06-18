package com.herrderlocken.frogportnetworks.block;

import com.herrderlocken.frogportnetworks.blockentity.RouterBlockEntity;
import com.herrderlocken.frogportnetworks.network.NetworkManager;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * RouterBlock — jetzt ein Create-Kinetik-Block: er sitzt auf einer Welle (Achse wie
 * eine Encased-Welle) und VERBRAUCHT Stress ({@link RouterBlockEntity#calculateStressApplied()}).
 * Ohne Rotation liefert der Router kein Netz mehr (DHCP/IP-Vergabe aus, siehe
 * {@link RouterBlockEntity#isPowered()}).
 */
public class RouterBlock extends RotatedPillarKineticBlock implements IBE<RouterBlockEntity> {

    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);

    public RouterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends RotatedPillarKineticBlock> codec() {
        return CODEC;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    /**
     * Eine Welle kann nur entlang der Router-Achse andocken. KineticBlock liefert
     * hier standardmäßig immer {@code false} (= keine Verbindung), darum müssen wir
     * das wie Creates Wellen-Blöcke selbst überschreiben.
     */
    @Override
    public boolean hasShaftTowards(net.minecraft.world.level.LevelReader level, BlockPos pos,
                                   BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Class<RouterBlockEntity> getBlockEntityClass() {
        return RouterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RouterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ROUTER.get();
    }

    /** Beim Entfernen die Router-IP im Netzwerk wieder freigeben. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            NetworkManager.unregisterDevice(pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Rechtsklick (ohne Item) → Router-GUI öffnen. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            withBlockEntityDo(level, pos, routerEntity -> serverPlayer.openMenu(routerEntity, buf -> {
                buf.writeBlockPos(pos);
                routerEntity.writeToBuffer(buf);
            }));
        }
        return InteractionResult.SUCCESS;
    }
}
