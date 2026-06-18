package com.herrderlocken.frogportnetworks.block;

import com.herrderlocken.frogportnetworks.blockentity.NetworkPortBlockEntity;
import com.herrderlocken.frogportnetworks.network.NetworkManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * NetworkPortBlock — Item-Schnittstelle ins Netzwerk. Stellt den Netz-Speicher als
 * IItemHandler bereit (siehe {@link NetworkPortBlockEntity}), damit Funnel/Trichter/
 * Chutes/Packager Items ein- und auslagern können. Verbindet sich automatisch per DHCP;
 * Rechtsklick verbindet neu und meldet den Status im Chat.
 */
public class NetworkPortBlock extends BaseEntityBlock {

    public static final MapCodec<NetworkPortBlock> CODEC = simpleCodec(NetworkPortBlock::new);

    public NetworkPortBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkPortBlockEntity(pos, state);
    }

    /** Beim Platzieren direkt versuchen, sich ins Netz einzuklinken. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof NetworkPortBlockEntity port) {
            port.tryAutoConnect();
        }
    }

    /** Rechtsklick: GUI öffnen (Netz-Status, I/O-Modus, Filter). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof NetworkPortBlockEntity port) {
            if (!port.isConnected()) port.tryAutoConnect();
            serverPlayer.openMenu(port, buf -> {
                buf.writeBlockPos(pos);
                port.writeToBuffer(buf);
            });
        }
        return InteractionResult.SUCCESS;
    }

    /** Beim Entfernen die belegte IP im Netzwerk wieder freigeben. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            NetworkManager.unregisterDevice(pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
