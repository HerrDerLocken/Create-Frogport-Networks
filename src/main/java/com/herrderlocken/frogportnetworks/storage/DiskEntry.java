package com.herrderlocken.frogportnetworks.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Ein gespeicherter Eintrag auf einer Disk: ein Item-Typ (Prototyp mit Anzahl 1)
 * plus die tatsächlich gelagerte Menge ({@code count}, kann sehr groß sein).
 */
public record DiskEntry(ItemStack item, long count) {

    public static final Codec<DiskEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.SINGLE_ITEM_CODEC.fieldOf("item").forGetter(DiskEntry::item),
            Codec.LONG.fieldOf("count").forGetter(DiskEntry::count)
    ).apply(i, DiskEntry::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiskEntry> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, DiskEntry::item,
            ByteBufCodecs.VAR_LONG, DiskEntry::count,
            DiskEntry::new);
}
