package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.menu.NetworkMonitorMenu;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.network.NetworkManager;
import com.herrderlocken.frogportnetworks.network.SubnetMask;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.herrderlocken.frogportnetworks.storage.DeviceListProvider;
import com.herrderlocken.frogportnetworks.storage.DeviceSnapshot;
import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NetworkMonitorBlockEntity — globale Übersicht. Im Gegensatz zum Terminal ist der
 * Monitor an KEIN Kabel/Netz gebunden: er listet ALLE im {@link NetworkManager}
 * registrierten NAS dieser Welt auf und gruppiert sie nach Subnetz (Gateway/CIDR).
 * Pro Subnetz liefert er einen {@link DeviceSnapshot} (Label = Subnetz-Adresse,
 * Inhalt = über alle NAS des Subnetzes gemergt). Reine Anzeige (read-only).
 */
public class NetworkMonitorBlockEntity extends BlockEntity implements MenuProvider, DeviceListProvider {

    public NetworkMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_MONITOR.get(), pos, state);
    }

    @Override
    public List<DeviceSnapshot> buildDevices() {
        // Subnetz-Label -> gemergte Einträge + Kapazität
        Map<String, List<DiskEntry>> bySubnet = new LinkedHashMap<>();
        Map<String, long[]> capBySubnet = new LinkedHashMap<>(); // [usedItems, maxItems, maxTypes]
        if (level == null) return List.of();

        for (BlockPos p : NetworkManager.allDevicePositions()) {
            if (!(level.getBlockEntity(p) instanceof NASBlockEntity nas)) continue;
            String key = subnetLabel(nas);
            StorageSnapshot s = nas.snapshot();
            List<DiskEntry> merged = bySubnet.computeIfAbsent(key, k -> new ArrayList<>());
            long[] cap = capBySubnet.computeIfAbsent(key, k -> new long[3]);
            cap[0] += s.usedItems();
            cap[1] += s.maxItems();
            cap[2] += s.maxTypes();
            for (DiskEntry e : s.entries()) addMerged(merged, e);
        }

        List<DeviceSnapshot> out = new ArrayList<>();
        List<String> keys = new ArrayList<>(bySubnet.keySet());
        keys.sort(String::compareTo);
        for (String key : keys) {
            List<DiskEntry> merged = bySubnet.get(key);
            long[] cap = capBySubnet.get(key);
            StorageSnapshot snap = new StorageSnapshot(merged, cap[0], cap[1], merged.size(), (int) cap[2]);
            out.add(new DeviceSnapshot(worldPosition, key, snap));
        }
        return out;
    }

    /** Subnetz-Adresse "a.b.c.0/cidr" eines NAS, oder "offline" wenn nicht verbunden. */
    private static String subnetLabel(NASBlockEntity nas) {
        IPAddress ip = nas.getIpAddress();
        if (!nas.isConnected() || ip == null) return "offline";
        int cidr = nas.getCidrPrefix();
        SubnetMask mask = SubnetMask.fromCIDR(cidr);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) sb.append('.');
            sb.append(ip.getOctet(i) & mask.getOctet(i));
        }
        return sb.append('/').append(cidr).toString();
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
    public Component getDisplayName() {
        return Component.translatable("gui.frogportnetworks.network_monitor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new NetworkMonitorMenu(containerId, playerInv, this);
    }
}
