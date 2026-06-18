package com.herrderlocken.frogportnetworks.storage;

import java.util.List;

/**
 * Von BlockEntities implementiert, die der GUI eine Liste von {@link DeviceSnapshot}
 * liefern (für Tabs). Das Terminal liefert ein NAS pro Tab, der Network Monitor ein
 * Subnetz pro Tab. So kann {@code RequestNetworkSnapshotPacket} beide bedienen.
 */
public interface DeviceListProvider {
    List<DeviceSnapshot> buildDevices();
}
