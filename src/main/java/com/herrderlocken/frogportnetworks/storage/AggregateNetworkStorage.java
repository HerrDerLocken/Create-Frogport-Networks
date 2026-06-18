package com.herrderlocken.frogportnetworks.storage;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Bündelt mehrere {@link NetworkStorage}-Quellen (z.B. alle erreichbaren NAS) zu einer
 * einzigen Sicht — für Verbraucher wie die Craft-Engine, die das gesamte erreichbare Netz
 * als einen Speicher behandeln wollen.
 */
public class AggregateNetworkStorage implements NetworkStorage {

    private final List<NetworkStorage> sources;

    public AggregateNetworkStorage(List<NetworkStorage> sources) {
        this.sources = sources;
    }

    @Override
    public StorageSnapshot snapshot() {
        List<DiskEntry> merged = new ArrayList<>();
        long usedItems = 0, maxItems = 0;
        int maxTypes = 0;
        for (NetworkStorage s : sources) {
            StorageSnapshot snap = s.snapshot();
            usedItems += snap.usedItems();
            maxItems += snap.maxItems();
            maxTypes += snap.maxTypes();
            for (DiskEntry e : snap.entries()) addMerged(merged, e);
        }
        return new StorageSnapshot(merged, usedItems, maxItems, merged.size(), maxTypes);
    }

    private static void addMerged(List<DiskEntry> merged, DiskEntry e) {
        for (int i = 0; i < merged.size(); i++) {
            if (ItemStack.isSameItemSameComponents(merged.get(i).item(), e.item())) {
                merged.set(i, new DiskEntry(merged.get(i).item(), merged.get(i).count() + e.count()));
                return;
            }
        }
        merged.add(e);
    }

    @Override
    public long insert(ItemStack proto, long amount, boolean simulate) {
        long inserted = 0;
        for (NetworkStorage s : sources) {
            if (inserted >= amount) break;
            inserted += s.insert(proto, amount - inserted, simulate);
        }
        return inserted;
    }

    @Override
    public long extract(ItemStack proto, long amount, boolean simulate) {
        long extracted = 0;
        for (NetworkStorage s : sources) {
            if (extracted >= amount) break;
            extracted += s.extract(proto, amount - extracted, simulate);
        }
        return extracted;
    }
}
