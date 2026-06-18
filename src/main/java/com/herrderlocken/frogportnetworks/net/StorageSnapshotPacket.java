package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.client.ClientStorage;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * StorageSnapshotPacket — Server → Client: aktueller (aggregierter) Speicher-Inhalt
 * für die offene Storage-GUI.
 */
public record StorageSnapshotPacket(BlockPos pos, StorageSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<StorageSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "storage_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageSnapshotPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StorageSnapshotPacket::pos,
            StorageSnapshot.STREAM_CODEC, StorageSnapshotPacket::snapshot,
            StorageSnapshotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(StorageSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStorage.applySnapshot(packet.pos(), packet.snapshot()));
    }
}
