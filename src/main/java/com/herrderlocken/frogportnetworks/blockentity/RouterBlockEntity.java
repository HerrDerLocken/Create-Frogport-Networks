package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.Config;
import com.herrderlocken.frogportnetworks.menu.RouterMenu;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.network.NetworkManager;
import com.herrderlocken.frogportnetworks.network.RoutingTable;
import com.herrderlocken.frogportnetworks.network.SubnetMask;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RouterBlockEntity — Create-Kinetik-Verbraucher. Der Router braucht Rotation:
 * er verbraucht Stress ({@link #calculateStressApplied()}) und liefert nur dann
 * ein Netz, wenn er sich dreht und nicht überlastet ist ({@link #isPowered()}).
 * Läuft er an / bleibt er stehen, werden die erreichbaren Geräte automatisch
 * neu verbunden bzw. getrennt ({@link #propagateNetworkState()}).
 */
public class RouterBlockEntity extends KineticBlockEntity implements MenuProvider {

    /** Stress, den der Router von der Welle abzieht (SU). */
    public static final float STRESS_IMPACT = 8f;

    private IPAddress ipAddress;
    private SubnetMask subnetMask;
    private int cidrPrefix = 24;
    private final RoutingTable routingTable = new RoutingTable();

    private int dhcpPoolStart;
    private int dhcpPoolEnd;
    private boolean dhcpEnabled = true;

    /** Letzter bekannter Strom-Zustand, um Flanken (an↔aus) zu erkennen. */
    private boolean wasPowered;

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTER.get(), pos, state);
        this.ipAddress = new IPAddress(10, 0, 0, 1);
        this.subnetMask = SubnetMask.fromCIDR(cidrPrefix);
        this.dhcpPoolStart = Config.DHCP_POOL_START.getAsInt();
        this.dhcpPoolEnd = Config.DHCP_POOL_END.getAsInt();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Keine Zusatz-Behaviours — der Router ist ein reiner Stress-Verbraucher.
    }

    // === Create-Kinetik ===

    @Override
    public float calculateStressApplied() {
        this.lastStressApplied = STRESS_IMPACT;
        return STRESS_IMPACT;
    }

    /** Router liefert nur dann ein Netz, wenn er sich dreht und nicht überlastet ist. */
    public boolean isPowered() {
        return Math.abs(getSpeed()) > 0 && !isOverStressed();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        propagateNetworkState();
    }

    /**
     * Reagiert auf eine Strom-Flanke: läuft der Router an, werden noch nicht
     * verbundene Geräte (per-DHCP) neu verbunden; bleibt er stehen, werden alle
     * verbundenen Geräte getrennt.
     */
    private void propagateNetworkState() {
        if (level == null || level.isClientSide) return;
        boolean powered = isPowered();
        if (powered == wasPowered) return;
        wasPowered = powered;

        for (BlockPos p : reachableDevices()) {
            if (level.getBlockEntity(p) instanceof AbstractNetworkDeviceBlockEntity dev) {
                if (powered) {
                    if (!dev.isConnected()) dev.requestDhcp();
                } else if (dev.isConnected()) {
                    dev.disconnect();
                }
            }
        }
    }

    /** Alle über angrenzende Kabel (jede Farbe) erreichbaren Geräte-Positionen. */
    private Set<BlockPos> reachableDevices() {
        Set<BlockPos> result = new HashSet<>();
        int maxHops = Config.CABLE_MAX_LENGTH.getAsInt();
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(dir)) instanceof NetworkCableBlockEntity cable) {
                for (DyeColor c : cable.getColors()) {
                    result.addAll(NetworkManager.discoverOnColor(level, worldPosition, c, maxHops));
                }
            }
        }
        return result;
    }

    // === Menu ===

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.frogportnetworks.router");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new RouterMenu(containerId, playerInv, this);
    }

    /** Schreibt die aktuellen Einstellungen in den Menu-Öffnungs-Buffer (gegen Sync-Race). */
    public void writeToBuffer(FriendlyByteBuf buf) {
        buf.writeVarInt(ipAddress.getOctet(0));
        buf.writeVarInt(ipAddress.getOctet(1));
        buf.writeVarInt(ipAddress.getOctet(2));
        buf.writeVarInt(ipAddress.getOctet(3));
        buf.writeVarInt(cidrPrefix);
        buf.writeVarInt(dhcpEnabled ? 1 : 0);
        buf.writeVarInt(dhcpPoolStart);
        buf.writeVarInt(dhcpPoolEnd);
    }

    /**
     * Vergibt die NIEDRIGSTE freie Adresse aus dem Pool. Ohne Rotation (kein Strom)
     * vergibt der Router KEINE Adresse — das Netz ist dann aus.
     */
    public IPAddress assignDHCP() {
        if (!dhcpEnabled || !isPowered()) return null;
        for (int host = dhcpPoolStart; host <= dhcpPoolEnd; host++) {
            IPAddress candidate = new IPAddress(
                    ipAddress.getOctet(0), ipAddress.getOctet(1), ipAddress.getOctet(2), host);
            if (!NetworkManager.isIPTaken(candidate)) {
                return candidate;
            }
        }
        return null; // Pool erschöpft
    }

    public IPAddress getIpAddress() { return ipAddress; }
    public void setIpAddress(IPAddress ip) {
        this.ipAddress = ip;
        setChanged();
        if (level != null && !level.isClientSide) NetworkManager.registerDevice(worldPosition, ip);
    }

    /** Eigene IP beim Laden registrieren, damit sie nicht als "frei" gilt (DHCP/statisch). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && ipAddress != null) {
            NetworkManager.registerDevice(worldPosition, ipAddress);
        }
    }

    public SubnetMask getSubnetMask() { return subnetMask; }
    public int getCidrPrefix() { return cidrPrefix; }
    public void setCidrPrefix(int prefix) {
        this.cidrPrefix = prefix;
        this.subnetMask = SubnetMask.fromCIDR(prefix);
        setChanged();
    }
    public RoutingTable getRoutingTable() { return routingTable; }
    public boolean isDhcpEnabled() { return dhcpEnabled; }
    public void setDhcpEnabled(boolean enabled) { this.dhcpEnabled = enabled; setChanged(); }
    public int getDhcpPoolStart() { return dhcpPoolStart; }
    public void setDhcpPoolStart(int start) { this.dhcpPoolStart = start; setChanged(); }
    public int getDhcpPoolEnd() { return dhcpPoolEnd; }
    public void setDhcpPoolEnd(int end) { this.dhcpPoolEnd = end; setChanged(); }

    // === Persistenz (SmartBlockEntity nutzt write/read statt save-/loadAdditional) ===

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("ip", ipAddress.toString());
        tag.putInt("cidr", cidrPrefix);
        tag.putInt("dhcpStart", dhcpPoolStart);
        tag.putInt("dhcpEnd", dhcpPoolEnd);
        tag.putBoolean("dhcpEnabled", dhcpEnabled);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("ip")) this.ipAddress = IPAddress.parse(tag.getString("ip"));
        if (tag.contains("cidr")) {
            this.cidrPrefix = tag.getInt("cidr");
            this.subnetMask = SubnetMask.fromCIDR(cidrPrefix);
        }
        if (tag.contains("dhcpStart")) this.dhcpPoolStart = tag.getInt("dhcpStart");
        if (tag.contains("dhcpEnd")) this.dhcpPoolEnd = tag.getInt("dhcpEnd");
        if (tag.contains("dhcpEnabled")) this.dhcpEnabled = tag.getBoolean("dhcpEnabled");
    }
}
