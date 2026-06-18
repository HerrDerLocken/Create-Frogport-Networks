package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.NetworkBridgeBlockEntity;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * UpdateBridgePacket — Client → Server: setzt die komplette Konfiguration einer
 * {@link NetworkBridgeBlockEntity} (Quell-/Ziel-IP, Filter, Menge, Schwellwert, An/Aus).
 */
public record UpdateBridgePacket(BlockPos pos, boolean hasSrc, int[] src, boolean hasDst, int[] dst,
                                 int amount, int threshold, boolean enabled, ItemStack filter)
        implements CustomPacketPayload {

    public static final Type<UpdateBridgePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "update_bridge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateBridgePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeBoolean(p.hasSrc());
                        for (int i = 0; i < 4; i++) buf.writeVarInt(p.src()[i]);
                        buf.writeBoolean(p.hasDst());
                        for (int i = 0; i < 4; i++) buf.writeVarInt(p.dst()[i]);
                        buf.writeVarInt(p.amount());
                        buf.writeVarInt(p.threshold());
                        buf.writeBoolean(p.enabled());
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.filter());
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        boolean hasSrc = buf.readBoolean();
                        int[] src = {buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
                        boolean hasDst = buf.readBoolean();
                        int[] dst = {buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
                        int amount = buf.readVarInt();
                        int threshold = buf.readVarInt();
                        boolean enabled = buf.readBoolean();
                        ItemStack filter = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                        return new UpdateBridgePacket(pos, hasSrc, src, hasDst, dst, amount, threshold, enabled, filter);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UpdateBridgePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = packet.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof NetworkBridgeBlockEntity bridge)) return;

            IPAddress src = packet.hasSrc() ? toIp(packet.src()) : null;
            IPAddress dst = packet.hasDst() ? toIp(packet.dst()) : null;
            bridge.applyConfig(src, dst, packet.filter(), packet.amount(), packet.threshold(), packet.enabled());
        });
    }

    @org.jetbrains.annotations.Nullable
    private static IPAddress toIp(int[] o) {
        for (int v : o) if (v < 0 || v > 255) return null;
        return new IPAddress(o[0], o[1], o[2], o[3]);
    }
}
