package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.ComputerBlockEntity;
import com.herrderlocken.frogportnetworks.blockentity.TerminalBlockEntity;
import com.herrderlocken.frogportnetworks.craft.CraftEngine;
import com.herrderlocken.frogportnetworks.network.RoutingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * RequestCraftablesPacket — Client → Server: das Terminal will die Liste der GERADE craftbaren
 * Items (für den Craftbar-Index). Wird nur befüllt, wenn ein Computer erreichbar ist.
 */
public record RequestCraftablesPacket(BlockPos terminalPos) implements CustomPacketPayload {

    public static final Type<RequestCraftablesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "request_craftables"));

    public static final StreamCodec<FriendlyByteBuf, RequestCraftablesPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeBlockPos(p.terminalPos()),
                    buf -> new RequestCraftablesPacket(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestCraftablesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = packet.terminalPos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof TerminalBlockEntity terminal)) return;

            // Nur wenn ein Computer erreichbar ist, lohnt der (teure) Craftbar-Scan.
            boolean hasComputer = false;
            for (BlockPos p : RoutingManager.reachableStorages(player.level(), pos, terminal.getNetworkColor())) {
                if (player.level().getBlockEntity(p) instanceof ComputerBlockEntity) { hasComputer = true; break; }
            }
            List<net.minecraft.world.item.ItemStack> craftables = hasComputer
                    ? CraftEngine.craftableResults(player.level(), terminal)
                    : List.of();
            ModNetworking.sendCraftables(player, pos, craftables);
        });
    }
}
