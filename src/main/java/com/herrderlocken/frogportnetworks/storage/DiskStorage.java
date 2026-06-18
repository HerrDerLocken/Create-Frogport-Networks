package com.herrderlocken.frogportnetworks.storage;

import com.herrderlocken.frogportnetworks.item.StorageDiskItem;
import com.herrderlocken.frogportnetworks.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * DiskStorage — Lese-/Schreiboperationen auf einer einzelnen Speicher-Disk
 * (ItemStack mit {@link DiskTier} + {@link DiskContents}-Component).
 *
 * Kapazität: maxItems (Gesamtmenge) und maxTypes (verschiedene Item-Typen) der Stufe.
 */
public final class DiskStorage {

    private DiskStorage() {}

    public static boolean isDisk(ItemStack stack) {
        return stack.getItem() instanceof StorageDiskItem;
    }

    public static DiskTier tier(ItemStack disk) {
        return disk.getItem() instanceof StorageDiskItem d ? d.getTier() : null;
    }

    public static DiskContents contents(ItemStack disk) {
        return disk.getOrDefault(ModDataComponents.DISK_CONTENTS.get(), DiskContents.EMPTY);
    }

    private static int indexOf(List<DiskEntry> entries, ItemStack proto) {
        for (int i = 0; i < entries.size(); i++) {
            if (ItemStack.isSameItemSameComponents(entries.get(i).item(), proto)) return i;
        }
        return -1;
    }

    /** Lagert bis zu {@code amount} ein; gibt die eingelagerte Menge zurück. */
    public static long insert(ItemStack disk, ItemStack proto, long amount, boolean simulate) {
        DiskTier tier = tier(disk);
        if (tier == null || amount <= 0 || proto.isEmpty()) return 0;

        DiskContents c = contents(disk);
        long free = tier.maxItems() - c.totalCount();
        if (free <= 0) return 0;

        int idx = indexOf(c.entries(), proto);
        if (idx < 0 && c.typeCount() >= tier.maxTypes()) return 0; // kein Typ-Platz mehr

        long toInsert = Math.min(amount, free);
        if (toInsert <= 0) return 0;

        if (!simulate) {
            List<DiskEntry> list = new ArrayList<>(c.entries());
            if (idx < 0) {
                list.add(new DiskEntry(proto.copyWithCount(1), toInsert));
            } else {
                list.set(idx, new DiskEntry(list.get(idx).item(), list.get(idx).count() + toInsert));
            }
            disk.set(ModDataComponents.DISK_CONTENTS.get(), new DiskContents(list));
        }
        return toInsert;
    }

    /** Entnimmt bis zu {@code amount}; gibt die entnommene Menge zurück. */
    public static long extract(ItemStack disk, ItemStack proto, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        DiskContents c = contents(disk);
        int idx = indexOf(c.entries(), proto);
        if (idx < 0) return 0;

        long avail = c.entries().get(idx).count();
        long taken = Math.min(amount, avail);
        if (taken <= 0) return 0;

        if (!simulate) {
            List<DiskEntry> list = new ArrayList<>(c.entries());
            long left = avail - taken;
            if (left <= 0) list.remove(idx);
            else list.set(idx, new DiskEntry(list.get(idx).item(), left));
            disk.set(ModDataComponents.DISK_CONTENTS.get(), new DiskContents(list));
        }
        return taken;
    }
}
