package com.herrderlocken.frogportnetworks.storage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * StorageSnapshot — eine zusammengefasste Sicht auf einen Speicher (eine NAS oder
 * das ganze Netz). Enthält die aggregierten Einträge plus Kapazitäts-Eckdaten für
 * die Anzeige. Wird vom Server an die offene GUI geschickt.
 */
public record StorageSnapshot(List<DiskEntry> entries, long usedItems, long maxItems,
                              int usedTypes, int maxTypes) {

    public static final StorageSnapshot EMPTY = new StorageSnapshot(List.of(), 0, 0, 0, 0);

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageSnapshot> STREAM_CODEC = StreamCodec.composite(
            DiskEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), StorageSnapshot::entries,
            ByteBufCodecs.VAR_LONG, StorageSnapshot::usedItems,
            ByteBufCodecs.VAR_LONG, StorageSnapshot::maxItems,
            ByteBufCodecs.VAR_INT, StorageSnapshot::usedTypes,
            ByteBufCodecs.VAR_INT, StorageSnapshot::maxTypes,
            StorageSnapshot::new);
}
