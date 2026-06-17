package com.herrderlocken.frogportnetworks.block;

import com.herrderlocken.frogportnetworks.blockentity.RouterBlockEntity;
import com.mojang.serialization.MapCodec;
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

public class RouterBlock extends BaseEntityBlock {

    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);

    public RouterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
    }

    /**
     * Rechtsklick → GUI öffnen.
     *
     * serverPlayer.openMenu() macht zwei Dinge gleichzeitig:
     * 1. Server-seitig: erstellt das RouterMenu über MenuProvider.createMenu()
     * 2. Client-seitig: schickt ein Paket das den Client anweist
     *    den passenden Screen zu öffnen (über MenuScreens-Registrierung)
     *
     * Der zweite Parameter (buf -> buf.writeBlockPos) schreibt Extra-Daten
     * in den Netzwerk-Buffer, die der Client-Konstruktor von RouterMenu liest.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RouterBlockEntity routerEntity) {
                serverPlayer.openMenu(routerEntity, buf -> {
                    buf.writeBlockPos(pos);
                    routerEntity.writeToBuffer(buf);
                });
            }
        }
        return InteractionResult.SUCCESS;
    }
}
