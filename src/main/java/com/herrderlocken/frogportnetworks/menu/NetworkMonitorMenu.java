package com.herrderlocken.frogportnetworks.menu;

import com.herrderlocken.frogportnetworks.blockentity.NetworkMonitorBlockEntity;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * NetworkMonitorMenu — reine Anzeige-GUI (globale Item-Übersicht). Hat nur das
 * Spieler-Inventar; der netzweite Inhalt wird virtuell über
 * {@link com.herrderlocken.frogportnetworks.storage.DeviceSnapshot}-Pakete dargestellt
 * (Subnetz-Tabs + Suche, read-only).
 */
public class NetworkMonitorMenu extends AbstractContainerMenu {

    public static final int WINDOW_W = 200;
    public static final int SLOT = 18;
    public static final int GRID_X = 19;

    public static final int SEARCH_Y = 28;
    public static final int TABS_Y = 46;
    public static final int BROWSER_COLS = 9;
    public static final int BROWSER_ROWS = 5;
    public static final int BROWSER_X = GRID_X;
    public static final int BROWSER_Y = 76; // Platz für Tab-Leiste (46, +14) + Kapazitätszeile
    public static final int PLAYER_GAP = 10;

    private final BlockPos blockPos;

    // Server-Konstruktor
    public NetworkMonitorMenu(int containerId, Inventory playerInv, NetworkMonitorBlockEntity entity) {
        super(ModMenuTypes.NETWORK_MONITOR_MENU.get(), containerId);
        this.blockPos = entity.getBlockPos();
        layout(playerInv);
    }

    // Client-Konstruktor
    public NetworkMonitorMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.NETWORK_MONITOR_MENU.get(), containerId);
        this.blockPos = extraData.readBlockPos();
        layout(playerInv);
    }

    private void layout(Inventory playerInv) {
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

    public int getInvY() { return BROWSER_Y + BROWSER_ROWS * SLOT + PLAYER_GAP; }

    public BlockPos getBlockPos() { return blockPos; }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY; // reine Anzeige
    }
}
