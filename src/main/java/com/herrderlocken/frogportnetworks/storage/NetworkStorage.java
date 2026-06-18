package com.herrderlocken.frogportnetworks.storage;

import net.minecraft.world.item.ItemStack;

/**
 * NetworkStorage — eine Speicherquelle, auf die die GUI (NAS direkt oder Terminal
 * übers Netz) lesend/schreibend zugreift. Mengen sind {@code long}, weil Disks sehr
 * viel fassen.
 */
public interface NetworkStorage {

    /** Zusammengefasste Sicht für die Anzeige. */
    StorageSnapshot snapshot();

    /** Lagert bis zu {@code amount} des Prototyps ein; gibt die tatsächlich eingelagerte Menge zurück. */
    long insert(ItemStack proto, long amount, boolean simulate);

    /** Entnimmt bis zu {@code amount} des Prototyps; gibt die tatsächlich entnommene Menge zurück. */
    long extract(ItemStack proto, long amount, boolean simulate);
}
