package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.menu.NetworkBridgeMenu;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.network.NetworkManager;
import com.herrderlocken.frogportnetworks.network.RoutingManager;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * NetworkBridgeBlockEntity — Cross-Network-Routing. Verschiebt periodisch Items von einer
 * Quell-IP zu einer Ziel-IP (beides müssen {@link NetworkStorage}-Geräte sein: NAS,
 * Terminal oder Network Port), unabhängig von Kabeln/Subnetzen — die IPs werden global
 * über den {@link NetworkManager} aufgelöst. Regel: „bewege bis zu {@link #amount} ×
 * {@link #filter}, solange das Ziel weniger als {@link #threshold} davon hat".
 */
public class NetworkBridgeBlockEntity extends BlockEntity implements MenuProvider {

    /** Transfer-Intervall in Ticks. */
    public static final int INTERVAL = 20;

    private IPAddress srcIp;
    private IPAddress dstIp;
    private ItemStack filter = ItemStack.EMPTY; // leer = beliebiges Item
    private int amount = 64;
    private int threshold = 0;  // 0 = ohne Limit (immer senden)
    private boolean enabled = true;

    private int tickCounter;
    // Status (für die GUI, via ContainerData synchronisiert)
    private boolean srcResolved, dstResolved;
    private int lastMoved;

    public NetworkBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_BRIDGE.get(), pos, state);
    }

    // === Transfer-Logik ===

    public void serverTick() {
        if (level == null || level.isClientSide || !enabled) return;
        if (++tickCounter < INTERVAL) return;
        tickCounter = 0;

        BlockPos srcPos = srcIp == null ? null : NetworkManager.getPosition(srcIp);
        BlockPos dstPos = dstIp == null ? null : NetworkManager.getPosition(dstIp);
        NetworkStorage src = storageAt(srcPos);
        NetworkStorage dst = storageAt(dstPos);
        boolean sOk = src != null, dOk = dst != null;
        if (sOk != srcResolved || dOk != dstResolved) {
            srcResolved = sOk;
            dstResolved = dOk;
            setChanged();
        }
        if (src == null || dst == null || src == dst) return;

        // Cross-Network-Transfer nur, wenn Quelle und Ziel physisch über Kabel/Gateways verbunden sind.
        if (!RoutingManager.reachableStorages(level, srcPos, RoutingManager.adjacentCableColors(level, srcPos))
                .contains(dstPos)) {
            return;
        }

        ItemStack proto = filter;
        if (proto.isEmpty()) {
            proto = firstItem(src);            // "beliebig" → erstes verfügbares Item
            if (proto.isEmpty()) return;
        }

        long want = amount;
        if (!filter.isEmpty() && threshold > 0) {
            long have = countIn(dst, proto);
            if (have >= threshold) return;     // Ziel hat schon genug
            want = Math.min(want, threshold - have);
        }
        if (want <= 0) return;

        long avail = src.extract(proto, want, true);
        if (avail <= 0) return;
        long canInsert = dst.insert(proto, avail, true);
        long move = Math.min(avail, canInsert);
        if (move <= 0) return;

        long moved = src.extract(proto, move, false);
        long inserted = dst.insert(proto, moved, false);
        if (inserted < moved) src.insert(proto, moved - inserted, false); // Rest zurücklegen
        lastMoved = (int) Math.min(inserted, Integer.MAX_VALUE);
        setChanged();
    }

    @Nullable
    private NetworkStorage storageAt(@Nullable BlockPos p) {
        if (p == null || level == null) return null;
        return level.getBlockEntity(p) instanceof NetworkStorage ns ? ns : null;
    }

    private static long countIn(NetworkStorage storage, ItemStack proto) {
        long total = 0;
        for (DiskEntry e : storage.snapshot().entries()) {
            if (ItemStack.isSameItemSameComponents(e.item(), proto)) total += e.count();
        }
        return total;
    }

    private static ItemStack firstItem(NetworkStorage storage) {
        for (DiskEntry e : storage.snapshot().entries()) {
            if (!e.item().isEmpty() && e.count() > 0) return e.item();
        }
        return ItemStack.EMPTY;
    }

    // === Config (von der GUI gesetzt) ===

    public void applyConfig(IPAddress src, IPAddress dst, ItemStack filter, int amount, int threshold, boolean enabled) {
        this.srcIp = src;
        this.dstIp = dst;
        this.filter = filter == null || filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1);
        this.amount = Math.max(1, amount);
        this.threshold = Math.max(0, threshold);
        this.enabled = enabled;
        this.tickCounter = INTERVAL; // beim nächsten Tick sofort auswerten
        setChanged();
    }

    public IPAddress getSrcIp() { return srcIp; }
    public IPAddress getDstIp() { return dstIp; }
    public ItemStack getFilter() { return filter; }
    public int getAmount() { return amount; }
    public int getThreshold() { return threshold; }
    public boolean isEnabled() { return enabled; }
    public boolean isSrcResolved() { return srcResolved; }
    public boolean isDstResolved() { return dstResolved; }
    public int getLastMoved() { return lastMoved; }

    // === Menu ===

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.frogportnetworks.network_bridge");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new NetworkBridgeMenu(containerId, playerInv, this);
    }

    /** Config in den Öffnungs-Buffer (gegen Sync-Race). */
    public void writeToBuffer(RegistryFriendlyByteBuf buf) {
        writeIp(buf, srcIp);
        writeIp(buf, dstIp);
        buf.writeVarInt(amount);
        buf.writeVarInt(threshold);
        buf.writeBoolean(enabled);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, filter);
    }

    private static void writeIp(RegistryFriendlyByteBuf buf, IPAddress ip) {
        buf.writeBoolean(ip != null);
        for (int i = 0; i < 4; i++) buf.writeVarInt(ip == null ? 0 : ip.getOctet(i));
    }

    // === Persistenz ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (srcIp != null) tag.putString("src", srcIp.toString());
        if (dstIp != null) tag.putString("dst", dstIp.toString());
        tag.putInt("amount", amount);
        tag.putInt("threshold", threshold);
        tag.putBoolean("enabled", enabled);
        if (!filter.isEmpty()) tag.put("filter", filter.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.srcIp = tag.contains("src") ? IPAddress.parse(tag.getString("src")) : null;
        this.dstIp = tag.contains("dst") ? IPAddress.parse(tag.getString("dst")) : null;
        if (tag.contains("amount")) this.amount = tag.getInt("amount");
        if (tag.contains("threshold")) this.threshold = tag.getInt("threshold");
        if (tag.contains("enabled")) this.enabled = tag.getBoolean("enabled");
        this.filter = tag.contains("filter")
                ? ItemStack.parseOptional(registries, tag.getCompound("filter"))
                : ItemStack.EMPTY;
    }
}
