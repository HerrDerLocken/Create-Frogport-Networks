package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.client.ClientStorage;
import com.herrderlocken.frogportnetworks.storage.DeviceSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * NetworkSnapshotPacket — Server → Client: die netzweite Geräteliste fürs Terminal
 * (jedes NAS mit IP + Inhalt). Daraus baut die GUI die Tabs.
 */
public record NetworkSnapshotPacket(BlockPos pos, List<DeviceSnapshot> devices) implements CustomPacketPayload {

    public static final Type<NetworkSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "network_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSnapshotPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NetworkSnapshotPacket::pos,
            DeviceSnapshot.STREAM_CODEC.apply(ByteBufCodecs.list()), NetworkSnapshotPacket::devices,
            NetworkSnapshotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(NetworkSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStorage.applyNetworkSnapshot(packet.pos(), packet.devices()));
    }
}
