package com.herrderlocken.frogportnetworks.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * DeviceSnapshot — ein einzelnes NAS im Netz (Position + IP + sein Inhalt). Das
 * Terminal schickt eine Liste davon; daraus baut die GUI die Tabs (pro IP) und den
 * aggregierten "All"-Tab. Die {@code pos} dient als Ziel für Entnahmen aus genau
 * diesem Gerät.
 */
public record DeviceSnapshot(BlockPos pos, String ip, StorageSnapshot snapshot) {

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceSnapshot> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DeviceSnapshot::pos,
            ByteBufCodecs.STRING_UTF8, DeviceSnapshot::ip,
            StorageSnapshot.STREAM_CODEC, DeviceSnapshot::snapshot,
            DeviceSnapshot::new);
}
