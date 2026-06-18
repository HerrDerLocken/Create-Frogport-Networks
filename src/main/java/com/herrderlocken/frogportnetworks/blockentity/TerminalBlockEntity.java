package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.menu.TerminalMenu;
import com.herrderlocken.frogportnetworks.network.RoutingManager;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.herrderlocken.frogportnetworks.storage.DeviceListProvider;
import com.herrderlocken.frogportnetworks.storage.DeviceSnapshot;
import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TerminalBlockEntity — Netzwerk-Schnittstelle. Verbindet sich (DHCP/statisch, siehe
 * {@link AbstractNetworkDeviceBlockEntity}) und stellt als {@link NetworkStorage} eine
 * AGGREGIERTE Sicht über alle NAS-Laufwerke im selben (Farb-)Netz bereit. Die GUI
 * zeigt zusätzlich pro NAS einen Tab (siehe {@link #buildDevices()}).
 */
public class TerminalBlockEntity extends AbstractNetworkDeviceBlockEntity implements NetworkStorage, DeviceListProvider {

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL.get(), pos, state);
    }

    /** Alle erreichbaren NAS — multi-hop über Gateways gekoppelte Netze, nach IP sortiert. */
    private List<NASBlockEntity> reachableNAS() {
        List<NASBlockEntity> list = new ArrayList<>();
        if (level == null || !connected) return list;
        for (BlockPos p : RoutingManager.reachableStorages(level, worldPosition, networkColor)) {
            if (level.getBlockEntity(p) instanceof NASBlockEntity nas) list.add(nas);
        }
        list.sort(Comparator.comparing(TerminalBlockEntity::ipOf));
        return list;
    }

    private static String ipOf(NASBlockEntity nas) {
        return nas.getIpAddress() == null ? "—" : nas.getIpAddress().toString();
    }

    // === NetworkStorage (aggregiert über alle NAS) ===

    @Override
    public StorageSnapshot snapshot() {
        List<DiskEntry> merged = new ArrayList<>();
        long usedItems = 0, maxItems = 0;
        int maxTypes = 0;
        for (NASBlockEntity nas : reachableNAS()) {
            StorageSnapshot s = nas.snapshot();
            usedItems += s.usedItems();
            maxItems += s.maxItems();
            maxTypes += s.maxTypes();
            for (DiskEntry e : s.entries()) addMerged(merged, e);
        }
        return new StorageSnapshot(merged, usedItems, maxItems, merged.size(), maxTypes);
    }

    static void addMerged(List<DiskEntry> merged, DiskEntry e) {
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
        for (NASBlockEntity nas : reachableNAS()) {
            if (inserted >= amount) break;
            inserted += nas.insert(proto, amount - inserted, simulate);
        }
        return inserted;
    }

    @Override
    public long extract(ItemStack proto, long amount, boolean simulate) {
        long extracted = 0;
        for (NASBlockEntity nas : reachableNAS()) {
            if (extracted >= amount) break;
            extracted += nas.extract(proto, amount - extracted, simulate);
        }
        return extracted;
    }

    /** Pro NAS ein {@link DeviceSnapshot} (für die Tabs). */
    public List<DeviceSnapshot> buildDevices() {
        List<DeviceSnapshot> devices = new ArrayList<>();
        for (NASBlockEntity nas : reachableNAS()) {
            devices.add(new DeviceSnapshot(nas.getBlockPos(), ipOf(nas), nas.snapshot()));
        }
        return devices;
    }

    // === Menu ===

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.frogportnetworks.terminal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new TerminalMenu(containerId, playerInv, this);
    }

    /** Aktuelle Werte in den Öffnungs-Buffer (verhindert Sync-Race in der GUI). */
    public void writeToBuffer(FriendlyByteBuf buf) {
        writeNetworkToBuffer(buf);
    }
}
