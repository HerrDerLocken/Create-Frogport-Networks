package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.menu.NetworkPortMenu;
import com.herrderlocken.frogportnetworks.network.RoutingManager;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * NetworkPortBlockEntity — Schnittstelle zwischen physischem Item-Transport und dem
 * Netz-Speicher. Holt sich wie das Terminal eine IP ({@link AbstractNetworkDeviceBlockEntity})
 * und stellt als {@link NetworkStorage} eine AGGREGIERTE Sicht über alle NAS im selben
 * (Farb-)Netz bereit. Über die {@link IItemHandler}-Capability ({@link NetworkPortItemHandler})
 * können Funnel/Trichter/Chutes/Packager Items in den Netz-Speicher schieben oder daraus ziehen.
 *
 * Per GUI konfigurierbar: I/O-{@link Mode} (rein/raus/beides) und ein optionaler Item-{@link #filter}.
 */
public class NetworkPortBlockEntity extends AbstractNetworkDeviceBlockEntity
        implements NetworkStorage, IHaveGoggleInformation {

    /** Betriebsmodus der Item-Schnittstelle. */
    public enum Mode {
        BOTH, INSERT, EXTRACT;
        public static Mode byId(int id) {
            Mode[] v = values();
            return id >= 0 && id < v.length ? v[id] : BOTH;
        }
        public boolean allowsInsert() { return this == BOTH || this == INSERT; }
        public boolean allowsExtract() { return this == BOTH || this == EXTRACT; }
    }

    public static final int FILTER_SLOTS = 9;

    private final NetworkPortItemHandler itemHandler = new NetworkPortItemHandler(this);
    private Mode mode = Mode.BOTH;
    /** Whitelist (leer = alles erlaubt); je Eintrag ein Item-Typ (Anzahl ignoriert). */
    private final NonNullList<ItemStack> filter = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);

    /** Snapshot-Cache: erspart Funnel-/Trichter-Abfragen (mehrfach pro Tick) ein BFS je Aufruf. */
    private StorageSnapshot cachedSnapshot;
    private long cacheTick = -1;

    public NetworkPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_PORT.get(), pos, state);
    }

    public IItemHandler getItemHandler() { return itemHandler; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; setChanged(); }

    public NonNullList<ItemStack> getFilter() { return filter; }

    public void setFilter(int index, ItemStack stack) {
        if (index < 0 || index >= FILTER_SLOTS) return;
        filter.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        setChanged();
    }

    /** true, wenn der Filter leer ist oder das Item in der Whitelist steht. */
    public boolean filterAllows(ItemStack stack) {
        boolean any = false;
        for (ItemStack f : filter) {
            if (f.isEmpty()) continue;
            any = true;
            if (ItemStack.isSameItem(f, stack)) return true;
        }
        return !any; // kein Filter gesetzt → alles erlaubt
    }

    /** Verbindet sich automatisch (DHCP), falls noch nicht verbunden. */
    public void tryAutoConnect() {
        if (level == null || level.isClientSide || connected) return;
        requestDhcp();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        tryAutoConnect();
    }

    /** Aktueller Speicher-Snapshot, höchstens einmal pro Tick neu berechnet. */
    public StorageSnapshot cachedSnapshot() {
        long now = level == null ? 0 : level.getGameTime();
        if (cachedSnapshot == null || now != cacheTick) {
            cachedSnapshot = snapshot();
            cacheTick = now;
        }
        return cachedSnapshot;
    }

    /** Alle erreichbaren NAS — multi-hop über Gateways gekoppelte Netze, nach IP sortiert. */
    private List<NASBlockEntity> reachableNAS() {
        List<NASBlockEntity> list = new ArrayList<>();
        if (level == null || !connected) return list;
        for (BlockPos p : RoutingManager.reachableStorages(level, worldPosition, networkColor)) {
            if (level.getBlockEntity(p) instanceof NASBlockEntity nas) list.add(nas);
        }
        list.sort(Comparator.comparing(NetworkPortBlockEntity::ipOf));
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

    // === Schraubenbrillen-Info (Create) ===

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.frogportnetworks.network_port").forGoggles(tooltip);
        if (connected && ipAddress != null) {
            CreateLang.translate("goggles.frogportnetworks.ip", ipAddress.toString())
                    .style(ChatFormatting.GRAY).forGoggles(tooltip);
            CreateLang.translate("goggles.frogportnetworks.mode",
                            Component.translatable("gui.frogportnetworks.mode." + mode.name().toLowerCase()))
                    .style(ChatFormatting.GRAY).forGoggles(tooltip);
        } else {
            CreateLang.translate("goggles.frogportnetworks.disconnected")
                    .style(ChatFormatting.RED).forGoggles(tooltip);
        }
        return true;
    }

    // === Menu ===

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.frogportnetworks.network_port");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new NetworkPortMenu(containerId, playerInv, this);
    }

    /** Netz-Status (12) + Modus (1) + Filter in den Menu-Öffnungs-Buffer (gegen Sync-Race). */
    public void writeToBuffer(RegistryFriendlyByteBuf buf) {
        writeNetworkToBuffer(buf);
        buf.writeVarInt(mode.ordinal());
        buf.writeVarInt(filter.size());
        for (ItemStack f : filter) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, f);
    }

    // === Persistenz (Netz-Status via super; zusätzlich Modus + Filter) ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("mode", mode.ordinal());
        tag.put("filter", ContainerHelper.saveAllItems(new CompoundTag(), filter, true, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.mode = Mode.byId(tag.getInt("mode"));
        // filter ist eine fixed-size NonNullList → nur set(), kein clear()/add()!
        for (int i = 0; i < FILTER_SLOTS; i++) filter.set(i, ItemStack.EMPTY);
        if (tag.contains("filter")) {
            ContainerHelper.loadAllItems(tag.getCompound("filter"), filter, registries);
        }
    }
}
