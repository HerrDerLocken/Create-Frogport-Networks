package com.herrderlocken.frogportnetworks.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * CableType — Kupfer, Gold, Glasfaser.
 *
 * Die Typen unterscheiden sich gameplay-seitig in Reichweite (max. Hops bei der
 * Netzwerk-Suche) und Tempo (simulierte Paket-Verzögerung in Ticks). Ein dickerer
 * "Kern"-Radius beim Rendering macht sie zusätzlich optisch unterscheidbar.
 */
public enum CableType implements StringRepresentable {
    //     id          maxLength  delayTicks  coreRadius(px)  baseColor(RGB)
    COPPER("copper",   16,        4,          2.0f,           0xA85327),  // rötliches Kupfer-Braun
    GOLD("gold",       32,        2,          2.0f,           0xFFD21A),  // kräftiges Gelb-Gold
    FIBER("fiber",     64,        1,          1.5f,           0xD8EEFA);  // helles Glasfaser-Weißblau

    public static final Codec<CableType> CODEC = StringRepresentable.fromEnum(CableType::values);
    public static final StreamCodec<ByteBuf, CableType> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(CableType::byId, CableType::ordinal);

    private static final CableType[] BY_ID = values();

    private final String id;
    private final int maxLength;
    private final int packetDelayTicks;
    private final float coreRadius;
    /** Basis-Farbe des Kabelmantels (RGB) — der Netz-Kanal wird als Linie obendrauf gezeigt. */
    private final int baseColor;

    CableType(String id, int maxLength, int packetDelayTicks, float coreRadius, int baseColor) {
        this.id = id;
        this.maxLength = maxLength;
        this.packetDelayTicks = packetDelayTicks;
        this.coreRadius = coreRadius;
        this.baseColor = baseColor;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public int getPacketDelayTicks() {
        return packetDelayTicks;
    }

    public float getCoreRadius() {
        return coreRadius;
    }

    public int getBaseColor() {
        return baseColor;
    }

    public static CableType byId(int ordinal) {
        return BY_ID[Math.floorMod(ordinal, BY_ID.length)];
    }

    public static CableType byName(String name) {
        for (CableType t : BY_ID) {
            if (t.id.equals(name)) return t;
        }
        return COPPER;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
