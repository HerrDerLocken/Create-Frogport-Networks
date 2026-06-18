package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.menu.NASMenu;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.herrderlocken.frogportnetworks.storage.DiskContents;
import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.herrderlocken.frogportnetworks.storage.DiskStorage;
import com.herrderlocken.frogportnetworks.storage.DiskTier;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * NASBlockEntity — Disk-Laufwerk (Network Attached Storage).
 *
 * Holt sich wie das Terminal eine IP (siehe {@link AbstractNetworkDeviceBlockEntity})
 * und ist darüber im Netz adressierbar. Statt eines festen Slot-Grids enthält es
 * {@link #DISK_SLOTS} Disk-Plätze; jede {@link com.herrderlocken.frogportnetworks.item.StorageDiskItem}
 * trägt ihre Items im NBT. Der NAS stellt die Summe seiner Disks als
 * {@link NetworkStorage} bereit (für die eigene GUI und später das Terminal).
 */
public class NASBlockEntity extends AbstractNetworkDeviceBlockEntity implements NetworkStorage {

    public static final int DISK_SLOTS = 6;

    /** Disk-Plätze (je 1 Disk). */
    private final SimpleContainer disks = new SimpleContainer(DISK_SLOTS) {
        @Override
        public int getMaxStackSize() { return 1; }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) { return DiskStorage.isDisk(stack); }
    };

    public NASBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NAS.get(), pos, state);
        disks.addListener(c -> setChanged());
    }

    public SimpleContainer getDisks() { return disks; }

    // === NetworkStorage ===

    @Override
    public StorageSnapshot snapshot() {
        List<DiskEntry> merged = new ArrayList<>();
        long usedItems = 0, maxItems = 0;
        int maxTypes = 0;
        for (int i = 0; i < disks.getContainerSize(); i++) {
            ItemStack disk = disks.getItem(i);
            DiskTier tier = DiskStorage.tier(disk);
            if (tier == null) continue;
            maxItems += tier.maxItems();
            maxTypes += tier.maxTypes();
            DiskContents c = DiskStorage.contents(disk);
            usedItems += c.totalCount();
            for (DiskEntry e : c.entries()) addMerged(merged, e);
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
        if (proto.isEmpty() || amount <= 0) return 0;
        long inserted = 0;
        // 1. Pass: in Disks, die den Typ schon führen (konsolidieren)
        for (int i = 0; i < disks.getContainerSize() && inserted < amount; i++) {
            ItemStack disk = disks.getItem(i);
            if (DiskStorage.tier(disk) == null) continue;
            if (containsType(disk, proto)) inserted += DiskStorage.insert(disk, proto, amount - inserted, simulate);
        }
        // 2. Pass: in beliebige Disks mit Platz (ggf. neuer Typ)
        for (int i = 0; i < disks.getContainerSize() && inserted < amount; i++) {
            ItemStack disk = disks.getItem(i);
            if (DiskStorage.tier(disk) == null) continue;
            inserted += DiskStorage.insert(disk, proto, amount - inserted, simulate);
        }
        if (inserted > 0 && !simulate) disks.setChanged();
        return inserted;
    }

    @Override
    public long extract(ItemStack proto, long amount, boolean simulate) {
        if (proto.isEmpty() || amount <= 0) return 0;
        long extracted = 0;
        for (int i = 0; i < disks.getContainerSize() && extracted < amount; i++) {
            ItemStack disk = disks.getItem(i);
            if (DiskStorage.tier(disk) == null) continue;
            extracted += DiskStorage.extract(disk, proto, amount - extracted, simulate);
        }
        if (extracted > 0 && !simulate) disks.setChanged();
        return extracted;
    }

    private static boolean containsType(ItemStack disk, ItemStack proto) {
        for (DiskEntry e : DiskStorage.contents(disk).entries()) {
            if (ItemStack.isSameItemSameComponents(e.item(), proto)) return true;
        }
        return false;
    }

    /** Droppt die Disks beim Abbau (Items bleiben in den Disks erhalten). */
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, disks);
    }

    // === Menu ===

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.frogportnetworks.nas");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new NASMenu(containerId, playerInv, this);
    }

    /** Netz-Status in den Öffnungs-Buffer (für die GUI). */
    public void writeToBuffer(FriendlyByteBuf buf) {
        writeNetworkToBuffer(buf);
    }

    // === Persistenz ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); // Netz-Status
        tag.put("disks", disks.createTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); // Netz-Status
        if (tag.contains("disks")) {
            disks.fromTag(tag.getList("disks", Tag.TAG_COMPOUND), registries);
        }
    }
}
