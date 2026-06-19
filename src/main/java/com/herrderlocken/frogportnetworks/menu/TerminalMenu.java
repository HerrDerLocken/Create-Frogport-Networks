package com.herrderlocken.frogportnetworks.menu;

import com.herrderlocken.frogportnetworks.blockentity.TerminalBlockEntity;
import com.herrderlocken.frogportnetworks.net.ModNetworking;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * TerminalMenu — Spieler-Inventar + Netz-Status. Der eigentliche, netzweite
 * Speicherinhalt ist virtuell (über {@link com.herrderlocken.frogportnetworks.storage.DeviceSnapshot}-
 * Pakete, Tabs in der GUI). Shift-Klick eines Items lagert es ins Netz ein
 * (aggregiert über alle NAS, {@link NetworkStorage}).
 */
public class TerminalMenu extends AbstractContainerMenu {

    public static final int DATA_SIZE = 12;

    public static final int WINDOW_W = 200;
    public static final int SLOT = 18;
    public static final int GRID_X = 19;
    public static final int CONTROLS_Y = 84;  // Suche + Sortierung + Gruppierung
    public static final int TABS_Y = 102;
    public static final int BROWSER_COLS = 9;
    public static final int BROWSER_ROWS = 5;
    public static final int BROWSER_X = GRID_X;
    public static final int BROWSER_Y = 132; // Platz für Controls (84) + Tab-Leiste (102) + Craft/Kapazität
    public static final int PLAYER_GAP = 10;

    private final ContainerData data;
    private final BlockPos blockPos;
    /** Serverseitige Speicherquelle (null auf dem Client). */
    private final NetworkStorage storage;
    /** Serverseitiges Terminal (für Level-Zugriff beim gezielten Einlagern); null auf Client. */
    private final TerminalBlockEntity terminalEntity;
    /** Gewählter Tab: Terminal-Pos = "All" (Aggregat), sonst die Ziel-NAS-Pos. */
    private BlockPos targetScope;

    // Server-Konstruktor
    public TerminalMenu(int containerId, Inventory playerInv, TerminalBlockEntity entity) {
        super(ModMenuTypes.TERMINAL_MENU.get(), containerId);
        this.blockPos = entity.getBlockPos();
        this.storage = entity;
        this.terminalEntity = entity;
        this.targetScope = entity.getBlockPos();

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
    public TerminalMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.TERMINAL_MENU.get(), containerId);
        this.blockPos = extraData.readBlockPos();
        SimpleContainerData seeded = new SimpleContainerData(DATA_SIZE);
        for (int i = 0; i < DATA_SIZE; i++) seeded.set(i, extraData.readVarInt());
        this.data = seeded;
        this.storage = null;
        this.terminalEntity = null;
        this.targetScope = this.blockPos;

        layout(playerInv);
        addDataSlots(this.data);
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

    // === Status-Getter (für den Screen) ===

    public boolean isConnected() { return data.get(0) == 1; }
    public int getIpOctet(int index) { return data.get(1 + index); }
    public String getIpString() {
        return data.get(1) + "." + data.get(2) + "." + data.get(3) + "." + data.get(4);
    }
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

    /** Vom {@link com.herrderlocken.frogportnetworks.net.SelectScopePacket} gesetzt. */
    public void setTargetScope(BlockPos scope) { this.targetScope = scope; }

    /** Ziel-Speicher für Einlagerung: bestimmtes NAS (Tab) oder das Aggregat ("All"). */
    private NetworkStorage depositTarget() {
        if (terminalEntity != null && targetScope != null && !targetScope.equals(blockPos)
                && terminalEntity.getLevel() != null
                && terminalEntity.getLevel().getBlockEntity(targetScope) instanceof NetworkStorage ns) {
            return ns;
        }
        return storage;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        // Alle Slots sind Spieler-Slots → Item ins Netz einlagern (nur serverseitig).
        if (storage == null) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        long inserted = depositTarget().insert(stack, stack.getCount(), false);
        if (inserted <= 0) return ItemStack.EMPTY;
        stack.shrink((int) inserted);
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (player instanceof ServerPlayer sp) {
            ModNetworking.sendNetworkSnapshot(sp, blockPos, ((TerminalBlockEntity) storage).buildDevices());
        }
        return ItemStack.EMPTY;
    }
}
