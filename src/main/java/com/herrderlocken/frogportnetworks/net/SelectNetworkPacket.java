package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.AbstractNetworkDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * SelectNetworkPacket — Client → Server: Terminal soll das Netz (Kabelfarbe) wechseln.
 * Wird genutzt, wenn am Terminal mehrere Kabelfarben anliegen.
 */
public record SelectNetworkPacket(BlockPos pos, int colorId) implements CustomPacketPayload {

    public static final Type<SelectNetworkPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "select_network"));

    public static final StreamCodec<FriendlyByteBuf, SelectNetworkPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeVarInt(p.colorId());
                    },
                    buf -> new SelectNetworkPacket(buf.readBlockPos(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SelectNetworkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            BlockPos pos = packet.pos();
            if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            if (packet.colorId() < 0 || packet.colorId() > 15) return;

            BlockEntity be = serverPlayer.level().getBlockEntity(pos);
            if (be instanceof AbstractNetworkDeviceBlockEntity device) {
                device.selectNetwork(DyeColor.byId(packet.colorId()));
            }
        });
    }
}
