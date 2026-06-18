package com.herrderlocken.frogportnetworks.client;

import com.herrderlocken.frogportnetworks.screen.NetworkStorageHost;
import com.herrderlocken.frogportnetworks.screen.StorageHost;
import com.herrderlocken.frogportnetworks.storage.DeviceSnapshot;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Client-seitige Annahme von Speicher-Snapshots: leitet sie an die offene
 * Storage-GUI weiter, falls deren Position passt.
 */
public final class ClientStorage {

    private ClientStorage() {}

    public static void applySnapshot(BlockPos pos, StorageSnapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof StorageHost host && pos.equals(host.storagePos())) {
            host.onSnapshot(snapshot);
        }
    }

    public static void applyNetworkSnapshot(BlockPos pos, List<DeviceSnapshot> devices) {
        if (Minecraft.getInstance().screen instanceof NetworkStorageHost host && pos.equals(host.networkPos())) {
            host.onNetworkSnapshot(devices);
        }
    }
}
