package com.herrderlocken.frogportnetworks.storage;

import net.minecraft.util.StringRepresentable;

/**
 * DiskTier — Kapazitätsstufen einer Speicher-Disk.
 *
 * {@code maxTypes} = wie viele unterschiedliche Item-Typen die Disk fassen kann,
 * {@code maxItems} = Gesamtzahl an Items (über alle Typen). So bleibt die Lagerung
 * sehr kompakt: ein Block voller Disks ersetzt riesige Kisten-/Frogport-Lager.
 */
public enum DiskTier implements StringRepresentable {
    K16("16k", 64, 16_384),
    K64("64k", 64, 65_536),
    K256("256k", 128, 262_144),
    M1("1m", 256, 1_048_576);

    private final String id;
    private final int maxTypes;
    private final long maxItems;

    DiskTier(String id, int maxTypes, long maxItems) {
        this.id = id;
        this.maxTypes = maxTypes;
        this.maxItems = maxItems;
    }

    public String id() { return id; }
    public int maxTypes() { return maxTypes; }
    public long maxItems() { return maxItems; }

    @Override
    public String getSerializedName() { return id; }
}
