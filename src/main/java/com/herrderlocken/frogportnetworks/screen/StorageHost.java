package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import net.minecraft.core.BlockPos;

/**
 * Von Storage-GUIs (NAS, später Terminal) implementiert, damit eingehende
 * {@link StorageSnapshot}-Pakete die richtige offene GUI aktualisieren.
 */
public interface StorageHost {
    BlockPos storagePos();
    void onSnapshot(StorageSnapshot snapshot);
}
