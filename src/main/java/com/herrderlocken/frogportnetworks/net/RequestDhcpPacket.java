package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * RequestDhcpPacket — Client → Server: "Terminal soll sich (neu) per DHCP verbinden".
 * Wird vom Connect/Renew-Button der Terminal-GUI gesendet.
 */
public record RequestDhcpPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RequestDhcpPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "request_dhcp"));

    public static final StreamCodec<FriendlyByteBuf, RequestDhcpPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBlockPos(packet.pos()),
                    buf -> new RequestDhcpPacket(buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestDhcpPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            BlockPos pos = packet.pos();
            if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

            BlockEntity be = serverPlayer.level().getBlockEntity(pos);
            if (be instanceof TerminalBlockEntity terminal) {
                boolean ok = terminal.requestDhcp();
                CreateFrogportNetworks.LOGGER.info("Terminal at {} DHCP request: {}", pos,
                        ok ? "connected " + terminal.getIpAddress() : "no router found");
            }
        });
    }
}
