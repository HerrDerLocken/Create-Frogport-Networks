package com.herrderlocken.frogportnetworks.menu;

import com.herrderlocken.frogportnetworks.blockentity.ComputerBlockEntity;
import com.herrderlocken.frogportnetworks.item.ComputerUpgradeItem;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * ComputerMenu — Upgrade-Slots ({@link ComputerBlockEntity#UPGRADE_SLOTS}) + Spieler-Inventar.
 * Status (RPM, SU-Impact, Strom/Überlastung) via {@link ContainerData} für den Screen.
 */
public class ComputerMenu extends AbstractContainerMenu {

    public static final int DATA_SIZE = 4; // rpm, stress, powered, overstressed

    public static final int WINDOW_W = 200;
    public static final int SLOT = 18;
    public static final int GRID_X = 19;

    public static final int UPGRADES = ComputerBlockEntity.UPGRADE_SLOTS;
    public static final int UPGRADES_Y = 44;
    public static final int UPGRADES_X = (WINDOW_W - UPGRADES * SLOT) / 2;
    public static final int PLAYER_GAP = 12;

    private final ContainerData data;
    private final BlockPos blockPos;
    private final Container upgrades;

    // Server-Konstruktor
    public ComputerMenu(int containerId, Inventory playerInv, ComputerBlockEntity entity) {
        super(ModMenuTypes.COMPUTER_MENU.get(), containerId);
        this.blockPos = entity.getBlockPos();
        this.upgrades = entity.getUpgrades();
        this.data = new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> entity.getRpm();
                    case 1 -> entity.getStressImpact();
                    case 2 -> entity.isPowered() ? 1 : 0;
                    case 3 -> entity.isOverStressed() ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_SIZE; }
        };
        layout(playerInv);
        addDataSlots(this.data);
    }

    // Client-Konstruktor
    public ComputerMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.COMPUTER_MENU.get(), containerId);
        this.blockPos = extraData.readBlockPos();
        this.upgrades = new SimpleContainer(UPGRADES);
        this.data = new SimpleContainerData(DATA_SIZE);
        layout(playerInv);
        addDataSlots(this.data);
    }

    private void layout(Inventory playerInv) {
        for (int i = 0; i < UPGRADES; i++) {
            addSlot(new UpgradeSlot(upgrades, i, UPGRADES_X + i * SLOT + 1, UPGRADES_Y + 1));
        }
        int invY = getInvY();
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

    public int getInvY() { return UPGRADES_Y + SLOT + PLAYER_GAP; }

    private static class UpgradeSlot extends Slot {
        UpgradeSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof ComputerUpgradeItem; }
        @Override public int getMaxStackSize() { return 16; }
    }

    public BlockPos getBlockPos() { return blockPos; }
    public int getRpm() { return data.get(0); }
    public int getStressImpact() { return data.get(1); }
    public boolean isPowered() { return data.get(2) == 1; }
    public boolean isOverStressed() { return data.get(3) == 1; }

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
        int upgEnd = UPGRADES;
        int playerStart = UPGRADES;
        int playerEnd = UPGRADES + 36;

        if (slotIndex < upgEnd) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            if (!(stack.getItem() instanceof ComputerUpgradeItem)) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, 0, upgEnd, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }
}
