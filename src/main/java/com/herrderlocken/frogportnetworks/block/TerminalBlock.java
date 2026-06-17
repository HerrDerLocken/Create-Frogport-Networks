package com.herrderlocken.frogportnetworks.block;

import com.herrderlocken.frogportnetworks.blockentity.TerminalBlockEntity;
import com.herrderlocken.frogportnetworks.network.NetworkManager;
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

/**
 * TerminalBlock — der Zugangspunkt für den Spieler zum Netzwerk.
 *
 * Über die Terminal-GUI kann der Spieler:
 * - Alle Items sehen die im Netzwerk verfügbar sind (über alle NAS)
 * - Items anfordern (werden über das Netzwerk zum Terminal geroutet)
 * - Items einlagern (werden zum nächsten NAS mit freiem Platz geschickt)
 * - Netzwerkstatus einsehen (verbundene Geräte, Routen)
 *
 * Das Terminal selbst hat KEIN Inventar — es ist nur eine Schnittstelle.
 * Alle Items liegen physisch in den NAS-Blöcken.
 */
public class TerminalBlock extends BaseEntityBlock {


    public static final MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    public TerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalBlockEntity(pos, state);
    }

    /** Beim Entfernen die belegte IP im Netzwerk wieder freigeben. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            NetworkManager.unregisterDevice(pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
            // Beim ersten Öffnen automatisch verbinden, danach manuell per Button erneuern.
            if (!terminal.isConnected()) {
                terminal.requestDhcp();
            }
            serverPlayer.openMenu(terminal, buf -> {
                buf.writeBlockPos(pos);
                terminal.writeToBuffer(buf);
            });
        }
        return InteractionResult.SUCCESS;
    }
}
