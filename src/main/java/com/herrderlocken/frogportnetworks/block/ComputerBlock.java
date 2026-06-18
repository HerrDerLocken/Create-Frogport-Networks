package com.herrderlocken.frogportnetworks.block;

import com.herrderlocken.frogportnetworks.blockentity.ComputerBlockEntity;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ComputerBlock — Create-Kinetik-Block (braucht Rotation entlang seiner Achse wie der Router).
 * Rechtsklick öffnet die GUI (Upgrade-Slots + Status). Verbraucht Stress je nach Upgrades.
 */
public class ComputerBlock extends RotatedPillarKineticBlock implements IBE<ComputerBlockEntity> {

    public static final MapCodec<ComputerBlock> CODEC = simpleCodec(ComputerBlock::new);

    public ComputerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends RotatedPillarKineticBlock> codec() { return CODEC; }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Class<ComputerBlockEntity> getBlockEntityClass() { return ComputerBlockEntity.class; }

    @Override
    public BlockEntityType<? extends ComputerBlockEntity> getBlockEntityType() { return ModBlockEntities.COMPUTER.get(); }

    /** Beim Entfernen die Upgrades droppen. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
            computer.dropUpgrades(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            withBlockEntityDo(level, pos, computer -> serverPlayer.openMenu(computer, buf -> buf.writeBlockPos(pos)));
        }
        return InteractionResult.SUCCESS;
    }
}
