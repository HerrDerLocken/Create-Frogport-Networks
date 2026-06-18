package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.NetworkPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * SetPortFilterPacket — Client → Server: einen Filter-Slot (Whitelist) eines Network Ports
 * setzen oder leeren. Die Anzahl wird ignoriert (nur der Item-Typ zählt).
 */
public record SetPortFilterPacket(BlockPos pos, int index, ItemStack stack) implements CustomPacketPayload {

    public static final Type<SetPortFilterPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "set_port_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPortFilterPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetPortFilterPacket::pos,
            ByteBufCodecs.VAR_INT, SetPortFilterPacket::index,
            ItemStack.OPTIONAL_STREAM_CODEC, SetPortFilterPacket::stack,
            SetPortFilterPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetPortFilterPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = packet.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof NetworkPortBlockEntity port) {
                port.setFilter(packet.index(), packet.stack());
            }
        });
    }
}
