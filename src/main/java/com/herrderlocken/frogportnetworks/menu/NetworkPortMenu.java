package com.herrderlocken.frogportnetworks.menu;

import com.herrderlocken.frogportnetworks.blockentity.NetworkPortBlockEntity;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * NetworkPortMenu — Konfigurations-GUI des Network Ports: Netz-Status (DHCP/statisch/
 * Netzwahl), I/O-Modus und Item-Filter. Es gibt keine Speicher-Slots; die Filter-Plätze
 * sind virtuelle "Ghost"-Slots (im Screen gezeichnet, nicht als echte {@link Slot}).
 */
public class NetworkPortMenu extends AbstractContainerMenu {

    /** 12 Netz-Status-Werte + Modus (Index 12). */
    public static final int DATA_SIZE = 13;

    public static final int WINDOW_W = 200;
    public static final int SLOT = 18;
    public static final int GRID_X = 19;

    public static final int FILTER_COLS = NetworkPortBlockEntity.FILTER_SLOTS;
    public static final int FILTER_X = GRID_X;
    public static final int FILTER_Y = 108;
    public static final int PLAYER_GAP = 12;

    private final ContainerData data;
    private final BlockPos blockPos;
    /** Client-Kopie des Filters (für die Ghost-Slots). */
    private final NonNullList<ItemStack> filter =
            NonNullList.withSize(NetworkPortBlockEntity.FILTER_SLOTS, ItemStack.EMPTY);

    // Server-Konstruktor
    public NetworkPortMenu(int containerId, Inventory playerInv, NetworkPortBlockEntity entity) {
        super(ModMenuTypes.NETWORK_PORT_MENU.get(), containerId);
        this.blockPos = entity.getBlockPos();
        for (int i = 0; i < filter.size(); i++) filter.set(i, entity.getFilter().get(i));

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
                    case 12 -> entity.getMode().ordinal();
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
    public NetworkPortMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        super(ModMenuTypes.NETWORK_PORT_MENU.get(), containerId);
        this.blockPos = extraData.readBlockPos();

        SimpleContainerData seeded = new SimpleContainerData(DATA_SIZE);
        for (int i = 0; i < DATA_SIZE; i++) seeded.set(i, extraData.readVarInt());
        this.data = seeded;

        int count = extraData.readVarInt();
        for (int i = 0; i < count && i < filter.size(); i++) {
            filter.set(i, ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
        }

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

    public int getInvY() { return FILTER_Y + SLOT + PLAYER_GAP; }

    // === Status-Getter (für den Screen) ===

    public boolean isConnected() { return data.get(0) == 1; }
    public int getIpOctet(int index) { return data.get(1 + index); }
    public String getIpString() {
        return data.get(1) + "." + data.get(2) + "." + data.get(3) + "." + data.get(4);
    }
    public int getCidrPrefix() { return data.get(9); }
    public DyeColor getNetworkColor() { return DyeColor.byId(data.get(10)); }
    public NetworkPortBlockEntity.Mode getMode() { return NetworkPortBlockEntity.Mode.byId(data.get(12)); }

    public java.util.List<DyeColor> getAvailableColors() {
        int mask = data.get(11);
        java.util.List<DyeColor> colors = new java.util.ArrayList<>();
        for (DyeColor c : DyeColor.values()) if ((mask & (1 << c.getId())) != 0) colors.add(c);
        return colors;
    }

    public ItemStack getFilter(int index) {
        return index >= 0 && index < filter.size() ? filter.get(index) : ItemStack.EMPTY;
    }

    /** Optimistisches Client-Update, damit der Ghost-Slot sofort reagiert. */
    public void setFilterClient(int index, ItemStack stack) {
        if (index >= 0 && index < filter.size()) filter.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public BlockPos getBlockPos() { return blockPos; }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // Keine Speicher-Slots — Shift-Klick bewegt nichts.
        return ItemStack.EMPTY;
    }
}
