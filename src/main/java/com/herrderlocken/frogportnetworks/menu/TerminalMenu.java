package com.herrderlocken.frogportnetworks.menu;

import com.herrderlocken.frogportnetworks.blockentity.TerminalBlockEntity;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * TerminalMenu — überträgt den Netzwerkstatus (verbunden, IP, Gateway, Subnetz, Farbe)
 * vom Server zur GUI. Reine Anzeige; die ContainerData wird live synchronisiert, damit
 * sich die Anzeige nach einem "Verbinden/Erneuern" sofort aktualisiert.
 */
public class TerminalMenu extends AbstractContainerMenu {

    public static final int DATA_SIZE = 12;

    private final ContainerData data;
    private final BlockPos blockPos;

    // Server-Konstruktor
    public TerminalMenu(int containerId, Inventory playerInv, TerminalBlockEntity entity) {
        super(ModMenuTypes.TERMINAL_MENU.get(), containerId);
        this.blockPos = entity.getBlockPos();

        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                IPAddress ip = entity.getIpAddress();
                IPAddress gw = entity.getGateway();
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
        addDataSlots(this.data);
    }

    // Client-Konstruktor
    public TerminalMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.TERMINAL_MENU.get(), containerId);
        this.blockPos = extraData.readBlockPos();
        SimpleContainerData seeded = new SimpleContainerData(DATA_SIZE);
        for (int i = 0; i < DATA_SIZE; i++) {
            seeded.set(i, extraData.readVarInt());
        }
        this.data = seeded;
        addDataSlots(this.data);
    }

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

    /** Farben der angrenzenden Kabel (für die Netz-Auswahl), nach Id sortiert. */
    public java.util.List<DyeColor> getAvailableColors() {
        int mask = data.get(11);
        java.util.List<DyeColor> colors = new java.util.ArrayList<>();
        for (DyeColor c : DyeColor.values()) {
            if ((mask & (1 << c.getId())) != 0) colors.add(c);
        }
        return colors;
    }

    public BlockPos getBlockPos() { return blockPos; }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
