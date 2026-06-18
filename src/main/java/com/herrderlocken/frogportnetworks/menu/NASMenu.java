package com.herrderlocken.frogportnetworks.menu;

import com.herrderlocken.frogportnetworks.blockentity.NASBlockEntity;
import com.herrderlocken.frogportnetworks.net.ModNetworking;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import com.herrderlocken.frogportnetworks.storage.DiskStorage;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * NASMenu — Disk-Bays + Spieler-Inventar + Netz-Status.
 *
 * Die Disk-Plätze sind echte Slots (Vanilla-Sync). Der eigentliche Speicherinhalt
 * (virtuelles, scrollbares Raster) wird nicht über Slots, sondern über
 * {@link com.herrderlocken.frogportnetworks.storage.StorageSnapshot}-Pakete
 * dargestellt. Shift-Klick eines Items aus dem Spieler-Inventar lagert es ins Netz
 * ein (über {@link NetworkStorage}); Disks wandern in die Bays.
 */
public class NASMenu extends AbstractContainerMenu {

    public static final int DATA_SIZE = 12;

    public static final int WINDOW_W = 200;
    public static final int SLOT = 18;
    public static final int GRID_X = 19;

    public static final int DRIVES = NASBlockEntity.DISK_SLOTS;
    public static final int DRIVES_Y = 92;
    public static final int DRIVES_X = (WINDOW_W - DRIVES * SLOT) / 2; // 6 Bays zentriert

    public static final int BROWSER_COLS = 9;
    public static final int BROWSER_ROWS = 4;
    public static final int BROWSER_X = GRID_X;
    public static final int BROWSER_Y = DRIVES_Y + SLOT + 8;
    public static final int PLAYER_GAP = 10;

    private final ContainerData data;
    private final BlockPos blockPos;
    private final Container diskContainer;
    /** Serverseitige Speicherquelle (null auf dem Client). */
    private final NetworkStorage storage;

    // Server-Konstruktor
    public NASMenu(int containerId, Inventory playerInv, NASBlockEntity entity) {
        super(ModMenuTypes.NAS_MENU.get(), containerId);
        this.blockPos = entity.getBlockPos();
        this.diskContainer = entity.getDisks();
        this.storage = entity;

        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                var ip = entity.getIpAddress();
                var gw = entity.getGateway();
                return switch (index) {
                    case 0 -> entity.isConnected() ? 1 : 0;
                    case 1, 2, 3, 4 -> ip == null ? 0 : ip.getOctet(index - 1);
                    case 5, 6, 7, 8 -> gw == null ? 0 : gw.getOctet(index - 5);
                    case 9 -> entity.getCidrPrefix();
                    case 10 -> entity.getNetworkColor().getId();
                    case 11 -> entity.availableColorMask();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() { return DATA_SIZE; }
        };

        layout(playerInv);
        addDataSlots(this.data);
    }

    // Client-Konstruktor
    public NASMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.NAS_MENU.get(), containerId);
        this.blockPos = extraData.readBlockPos();

        SimpleContainerData seeded = new SimpleContainerData(DATA_SIZE);
        for (int i = 0; i < DATA_SIZE; i++) seeded.set(i, extraData.readVarInt());
        this.data = seeded;
        this.diskContainer = new SimpleContainer(DRIVES);
        this.storage = null;

        layout(playerInv);
        addDataSlots(this.data);
    }

    private void layout(Inventory playerInv) {
        for (int i = 0; i < DRIVES; i++) {
            addSlot(new DiskSlot(diskContainer, i, DRIVES_X + i * SLOT + 1, DRIVES_Y + 1));
        }
        int invY = BROWSER_Y + BROWSER_ROWS * SLOT + PLAYER_GAP;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, GRID_X + col * SLOT + 1, invY + row * SLOT + 1));
            }
        }
        int hotbarY = invY + 3 * SLOT + 4;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, GRID_X + col * SLOT + 1, hotbarY + 1));
        }
    }

    public int getInvY() { return BROWSER_Y + BROWSER_ROWS * SLOT + PLAYER_GAP; }

    /** Disk-Bay: akzeptiert nur Disks, je 1 Stück. */
    private static class DiskSlot extends Slot {
        DiskSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return DiskStorage.isDisk(stack); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public int getMaxStackSize(ItemStack stack) { return 1; }
    }

    // === Status-Getter (für den Screen) ===

    public boolean isConnected() { return data.get(0) == 1; }
    public String getIpString() {
        return data.get(1) + "." + data.get(2) + "." + data.get(3) + "." + data.get(4);
    }
    public int getIpOctet(int index) { return data.get(1 + index); }
    public String getGatewayString() {
        return data.get(5) + "." + data.get(6) + "." + data.get(7) + "." + data.get(8);
    }
    public int getCidrPrefix() { return data.get(9); }
    public DyeColor getNetworkColor() { return DyeColor.byId(data.get(10)); }

    public java.util.List<DyeColor> getAvailableColors() {
        int mask = data.get(11);
        java.util.List<DyeColor> colors = new java.util.ArrayList<>();
        for (DyeColor c : DyeColor.values()) if ((mask & (1 << c.getId())) != 0) colors.add(c);
        return colors;
    }

    public BlockPos getBlockPos() { return blockPos; }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int diskEnd = DRIVES;
        int playerStart = DRIVES;
        int playerEnd = DRIVES + 36;

        if (slotIndex < diskEnd) {
            // Bay → Spieler
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            return original;
        }

        // Spieler-Inventar
        if (DiskStorage.isDisk(stack)) {
            if (!moveItemStackTo(stack, 0, diskEnd, false)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            return original;
        }

        // Normales Item → ins Netz einlagern (nur serverseitig; Client hat keine Quelle)
        if (storage == null) return ItemStack.EMPTY;
        long inserted = storage.insert(stack, stack.getCount(), false);
        if (inserted <= 0) return ItemStack.EMPTY;
        stack.shrink((int) inserted);
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (player instanceof ServerPlayer sp) {
            ModNetworking.sendSnapshot(sp, blockPos, storage.snapshot());
        }
        return ItemStack.EMPTY;
    }
}
