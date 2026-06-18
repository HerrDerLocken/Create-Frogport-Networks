package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.storage.DeviceSnapshot;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Von netzweiten Storage-GUIs (Terminal) implementiert, damit eingehende
 * {@link com.herrderlocken.frogportnetworks.net.NetworkSnapshotPacket} die richtige
 * offene GUI mit der Geräteliste aktualisieren.
 */
public interface NetworkStorageHost {
    BlockPos networkPos();
    void onNetworkSnapshot(List<DeviceSnapshot> devices);
}
