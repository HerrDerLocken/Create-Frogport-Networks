package com.herrderlocken.frogportnetworks.menu;

import com.herrderlocken.frogportnetworks.blockentity.NetworkBridgeBlockEntity;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * NetworkBridgeMenu — Konfigurations-GUI der Network Bridge: Quell-/Ziel-IP, Item-Filter,
 * Menge, Schwellwert, An/Aus. Config wird über den Öffnungs-Buffer geladen und per Paket
 * zurückgeschickt; Live-Status (aufgelöst? zuletzt bewegt?) via {@link ContainerData}.
 */
public class NetworkBridgeMenu extends AbstractContainerMenu {

    public static final int DATA_SIZE = 3; // srcResolved, dstResolved, lastMoved

    public static final int WINDOW_W = 200;
    public static final int SLOT = 18;
    public static final int GRID_X = 19;
    public static final int INV_Y = 112;

    private final ContainerData data;
    private final BlockPos blockPos;

    // Config (für die Screen-Initialisierung)
    private final int[] srcOctets = new int[4];
    private final int[] dstOctets = new int[4];
    private boolean hasSrc, hasDst, enabled;
    private int amount, threshold;
    private ItemStack filter = ItemStack.EMPTY;

    // Server-Konstruktor
    public NetworkBridgeMenu(int containerId, Inventory playerInv, NetworkBridgeBlockEntity entity) {
        super(ModMenuTypes.NETWORK_BRIDGE_MENU.get(), containerId);
        this.blockPos = entity.getBlockPos();
        readConfigFromEntity(entity);

        this.data = new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> entity.isSrcResolved() ? 1 : 0;
                    case 1 -> entity.isDstResolved() ? 1 : 0;
                    case 2 -> entity.getLastMoved();
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
    public NetworkBridgeMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        super(ModMenuTypes.NETWORK_BRIDGE_MENU.get(), containerId);
        this.blockPos = extraData.readBlockPos();
        this.hasSrc = extraData.readBoolean();
        for (int i = 0; i < 4; i++) srcOctets[i] = extraData.readVarInt();
        this.hasDst = extraData.readBoolean();
        for (int i = 0; i < 4; i++) dstOctets[i] = extraData.readVarInt();
        this.amount = extraData.readVarInt();
        this.threshold = extraData.readVarInt();
        this.enabled = extraData.readBoolean();
        this.filter = ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData);

        this.data = new SimpleContainerData(DATA_SIZE);
        layout(playerInv);
        addDataSlots(this.data);
    }

    private void readConfigFromEntity(NetworkBridgeBlockEntity e) {
        IPAddress s = e.getSrcIp(), d = e.getDstIp();
        hasSrc = s != null;
        hasDst = d != null;
        for (int i = 0; i < 4; i++) {
            srcOctets[i] = s == null ? 0 : s.getOctet(i);
            dstOctets[i] = d == null ? 0 : d.getOctet(i);
        }
        amount = e.getAmount();
        threshold = e.getThreshold();
        enabled = e.isEnabled();
        filter = e.getFilter();
    }

    private void layout(Inventory playerInv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, GRID_X + col * SLOT + 1, INV_Y + row * SLOT + 1));
            }
        }
        int hotbarY = INV_Y + 3 * SLOT + 4;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, GRID_X + col * SLOT + 1, hotbarY + 1));
        }
    }

    public int getInvY() { return INV_Y; }

    // === Getter (für den Screen) ===
    public BlockPos getBlockPos() { return blockPos; }
    public int getSrcOctet(int i) { return srcOctets[i]; }
    public int getDstOctet(int i) { return dstOctets[i]; }
    public boolean hasSrc() { return hasSrc; }
    public boolean hasDst() { return hasDst; }
    public int getAmount() { return amount; }
    public int getThreshold() { return threshold; }
    public boolean isEnabled() { return enabled; }
    public ItemStack getFilter() { return filter; }

    public boolean isSrcResolved() { return data.get(0) == 1; }
    public boolean isDstResolved() { return data.get(1) == 1; }
    public int getLastMoved() { return data.get(2); }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY; // keine Speicher-Slots
    }
}
