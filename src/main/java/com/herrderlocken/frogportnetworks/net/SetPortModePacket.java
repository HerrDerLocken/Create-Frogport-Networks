package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.NetworkPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * SetPortModePacket — Client → Server: I/O-Modus eines Network Ports setzen
 * (0 = beides, 1 = nur rein, 2 = nur raus).
 */
public record SetPortModePacket(BlockPos pos, int mode) implements CustomPacketPayload {

    public static final Type<SetPortModePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "set_port_mode"));

    public static final StreamCodec<FriendlyByteBuf, SetPortModePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeVarInt(p.mode());
                    },
                    buf -> new SetPortModePacket(buf.readBlockPos(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetPortModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = packet.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof NetworkPortBlockEntity port) {
                port.setMode(NetworkPortBlockEntity.Mode.byId(packet.mode()));
            }
        });
    }
}
