package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Stellt den netzweiten Speicher (erreichbar über den {@link NetworkPortBlockEntity})
 * als {@link IItemHandler} dar, damit Create-/Vanilla-Automation (Funnel, Trichter,
 * Chute, Packager) Items rein- und rausschieben kann.
 *
 * Die Slots sind virtuell: pro vorhandenem Item-Typ ein Slot (aus dem
 * {@link com.herrderlocken.frogportnetworks.storage.StorageSnapshot}) plus EIN leerer
 * Slot am Ende für reine Einlagerung. Mengen werden auf die Stackgröße gedeckelt,
 * sodass sich der Port für Maschinen wie ein normales Inventar verhält.
 */
public class NetworkPortItemHandler implements IItemHandler {

    private final NetworkPortBlockEntity port;

    public NetworkPortItemHandler(NetworkPortBlockEntity port) {
        this.port = port;
    }

    private List<DiskEntry> entries() {
        return port.cachedSnapshot().entries();
    }

    @Override
    public int getSlots() {
        return entries().size() + 1; // +1 leerer Slot für Einlagerung neuer Typen
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        List<DiskEntry> e = entries();
        if (slot < 0 || slot >= e.size()) return ItemStack.EMPTY;
        DiskEntry entry = e.get(slot);
        ItemStack s = entry.item().copy();
        // Echte (ungedeckelte) Menge, damit Creates Logistik (Stock Ticker / Packager-
        // InventorySummary) den tatsächlichen Bestand sieht und nicht nur 64 pro Typ.
        s.setCount((int) Math.min(entry.count(), Integer.MAX_VALUE));
        return s;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return stack;
        if (!port.getMode().allowsInsert() || !port.filterAllows(stack)) return stack;
        long inserted = port.insert(stack, stack.getCount(), simulate);
        if (inserted >= stack.getCount()) return ItemStack.EMPTY;
        ItemStack remainder = stack.copy();
        remainder.shrink((int) inserted);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        List<DiskEntry> e = entries();
        if (slot < 0 || slot >= e.size() || amount <= 0) return ItemStack.EMPTY;
        if (!port.getMode().allowsExtract()) return ItemStack.EMPTY;
        ItemStack proto = e.get(slot).item();
        if (!port.filterAllows(proto)) return ItemStack.EMPTY;
        int want = Math.min(amount, proto.getMaxStackSize());
        long extracted = port.extract(proto, want, simulate);
        if (extracted <= 0) return ItemStack.EMPTY;
        ItemStack out = proto.copy();
        out.setCount((int) extracted);
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return port.getMode().allowsInsert() && port.filterAllows(stack);
    }
}
