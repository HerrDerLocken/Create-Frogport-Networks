package com.herrderlocken.frogportnetworks.storage;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * DiskContents — der gespeicherte Inhalt einer Disk (Liste von {@link DiskEntry}).
 * Wird als DataComponent direkt auf dem Disk-Item abgelegt, sodass die Disk ihre
 * Items beim Herausnehmen/Transport mitnimmt.
 */
public record DiskContents(List<DiskEntry> entries) {

    public static final DiskContents EMPTY = new DiskContents(List.of());

    public static final Codec<DiskContents> CODEC =
            DiskEntry.CODEC.listOf().xmap(DiskContents::new, DiskContents::entries);

    public static final StreamCodec<RegistryFriendlyByteBuf, DiskContents> STREAM_CODEC =
            DiskEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(DiskContents::new, DiskContents::entries);

    public long totalCount() {
        long t = 0;
        for (DiskEntry e : entries) t += e.count();
        return t;
    }

    public int typeCount() { return entries.size(); }
}
