package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * RequestStorageSnapshotPacket — Client → Server: "Schick mir den aktuellen
 * Speicher-Inhalt" (beim Öffnen der GUI und beim Refresh).
 */
public record RequestStorageSnapshotPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RequestStorageSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "request_storage_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, RequestStorageSnapshotPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBlockPos(p.pos()),
                    buf -> new RequestStorageSnapshotPacket(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestStorageSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            BlockPos pos = packet.pos();
            if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

            BlockEntity be = serverPlayer.level().getBlockEntity(pos);
            if (be instanceof NetworkStorage storage) {
                ModNetworking.sendSnapshot(serverPlayer, pos, storage.snapshot());
            }
        });
    }
}
