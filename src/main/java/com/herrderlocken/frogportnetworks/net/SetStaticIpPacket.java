package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.TerminalBlockEntity;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * SetStaticIpPacket — Client → Server: Terminal soll eine manuell eingegebene IP nutzen.
 * Der Server prüft (Subnetz + frei) und übernimmt sie via {@link TerminalBlockEntity#requestStatic}.
 */
public record SetStaticIpPacket(BlockPos pos, int ip0, int ip1, int ip2, int ip3) implements CustomPacketPayload {

    public static final Type<SetStaticIpPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "set_static_ip"));

    public static final StreamCodec<FriendlyByteBuf, SetStaticIpPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeVarInt(p.ip0());
                        buf.writeVarInt(p.ip1());
                        buf.writeVarInt(p.ip2());
                        buf.writeVarInt(p.ip3());
                    },
                    buf -> new SetStaticIpPacket(buf.readBlockPos(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetStaticIpPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            BlockPos pos = packet.pos();
            if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            for (int o : new int[]{packet.ip0(), packet.ip1(), packet.ip2(), packet.ip3()}) {
                if (o < 0 || o > 255) return;
            }

            BlockEntity be = serverPlayer.level().getBlockEntity(pos);
            if (be instanceof TerminalBlockEntity terminal) {
                boolean ok = terminal.requestStatic(
                        new IPAddress(packet.ip0(), packet.ip1(), packet.ip2(), packet.ip3()));
                CreateFrogportNetworks.LOGGER.info("Terminal at {} static IP request: {}", pos,
                        ok ? "ok " + terminal.getIpAddress() : "rejected (subnet/taken/no router)");
            }
        });
    }
}
