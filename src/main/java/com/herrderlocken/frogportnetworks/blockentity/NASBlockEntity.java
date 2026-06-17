package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.Config;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NASBlockEntity — Netzwerk-Speicher für Items.
 *
 * Verwendet NonNullList<ItemStack> als Inventar — das ist Minecraft's
 * Standard-Container für Item-Slots. ContainerHelper kümmert sich um
 * die NBT-Serialisierung des gesamten Inventars in einem Schritt.
 *
 * Die IP-Adresse wird entweder per DHCP vom Router vergeben
 * oder manuell über die GUI gesetzt.
 */
public class NASBlockEntity extends BlockEntity {

    private IPAddress ipAddress;
    private NonNullList<ItemStack> inventory;

    public NASBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NAS.get(), pos, state);
        this.inventory = NonNullList.withSize(Config.NAS_SLOT_COUNT.getAsInt(), ItemStack.EMPTY);
    }

    // === Inventar-Zugriff ===

    public NonNullList<ItemStack> getInventory() { return inventory; }

    public ItemStack getItem(int slot) { return inventory.get(slot); }

    public void setItem(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        setChanged();
    }

    /**
     * Zählt wie viele freie Slots verfügbar sind.
     * Wird vom Terminal genutzt um zu entscheiden wohin eingelagert wird.
     */
    public int getFreeSlotCount() {
        int count = 0;
        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) count++;
        }
        return count;
    }

    /**
     * Droppt alle Items wenn der Block abgebaut wird.
     * Wird von NASBlock.playerWillDestroy() aufgerufen.
     */
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level,
                        pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        inventory.clear();
    }

    // === Netzwerk ===

    public IPAddress getIpAddress() { return ipAddress; }

    public void setIpAddress(IPAddress ip) {
        this.ipAddress = ip;
        setChanged();
    }

    public boolean hasIPAddress() { return ipAddress != null; }

    // === NBT ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ipAddress != null) tag.putString("ip", ipAddress.toString());
        ContainerHelper.saveAllItems(tag, inventory, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ip")) this.ipAddress = IPAddress.parse(tag.getString("ip"));
        this.inventory = NonNullList.withSize(Config.NAS_SLOT_COUNT.getAsInt(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, inventory, registries);
    }
}
