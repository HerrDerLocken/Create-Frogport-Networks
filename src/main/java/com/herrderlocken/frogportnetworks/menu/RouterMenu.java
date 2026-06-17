package com.herrderlocken.frogportnetworks.menu;

import com.herrderlocken.frogportnetworks.blockentity.RouterBlockEntity;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RouterMenu extends AbstractContainerMenu {

    public static final int DATA_SIZE = 8;

    private final ContainerData data;
    private final BlockPos blockPos;

    // Server-Konstruktor
    public RouterMenu(int containerId, Inventory playerInv, RouterBlockEntity entity) {
        super(ModMenuTypes.ROUTER_MENU.get(), containerId);
        this.blockPos = entity.getBlockPos();

        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> entity.getIpAddress().getOctet(0);
                    case 1 -> entity.getIpAddress().getOctet(1);
                    case 2 -> entity.getIpAddress().getOctet(2);
                    case 3 -> entity.getIpAddress().getOctet(3);
                    case 4 -> entity.getCidrPrefix();
                    case 5 -> entity.isDhcpEnabled() ? 1 : 0;
                    case 6 -> entity.getDhcpPoolStart();
                    case 7 -> entity.getDhcpPoolEnd();
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
    public RouterMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.ROUTER_MENU.get(), containerId);
        this.blockPos = extraData.readBlockPos();

        // Initialwerte direkt aus dem Öffnungs-Buffer lesen (siehe RouterBlockEntity#writeToBuffer).
        // So hat der Client die echten Werte bereits bei der Konstruktion — bevor RouterScreen.init()
        // läuft — und umgeht das ContainerData-Sync-Race, das sonst Defaults (0.0.0.0) anzeigte.
        SimpleContainerData seeded = new SimpleContainerData(DATA_SIZE);
        for (int i = 0; i < DATA_SIZE; i++) {
            seeded.set(i, extraData.readVarInt());
        }
        this.data = seeded;
        addDataSlots(this.data);
    }

    public int getIpOctet(int index) { return data.get(index); }
    public String getIpString() {
        return data.get(0) + "." + data.get(1) + "." + data.get(2) + "." + data.get(3);
    }
    public int getCidrPrefix() { return data.get(4); }
    public boolean isDhcpEnabled() { return data.get(5) == 1; }
    public int getDhcpPoolStart() { return data.get(6); }
    public int getDhcpPoolEnd() { return data.get(7); }
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
