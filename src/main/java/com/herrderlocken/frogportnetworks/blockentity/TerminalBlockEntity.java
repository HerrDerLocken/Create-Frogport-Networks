package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.Config;
import com.herrderlocken.frogportnetworks.menu.TerminalMenu;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.network.NetworkManager;
import com.herrderlocken.frogportnetworks.network.SubnetMask;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * TerminalBlockEntity — Netzwerk-Schnittstelle, die sich per DHCP eine IP holt.
 *
 * Beim Verbinden sucht das Terminal über die angrenzenden Kabel (farb-bewusst) einen
 * Router und fordert dort eine IP an ({@link RouterBlockEntity#assignDHCP()}). Gespeichert
 * werden die zugewiesene IP, Gateway (= Router-IP), Subnetz (CIDR) und die Netz-Farbe.
 */
public class TerminalBlockEntity extends BlockEntity implements MenuProvider {

    private IPAddress ipAddress;
    private IPAddress gateway;
    private int cidrPrefix = 24;
    private DyeColor networkColor = DyeColor.WHITE;
    private boolean connected;
    /** Vom Spieler gewähltes Netz (Farbe). null = automatisch (erstes erreichbares). */
    private DyeColor preferredColor;

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL.get(), pos, state);
    }

    // === DHCP ===

    private record RouterMatch(RouterBlockEntity router, DyeColor color) {}

    /**
     * Sucht über angrenzende Kabel (farb-bewusst) einen Router. Hat der Spieler ein
     * Netz gewählt ({@link #preferredColor}), wird NUR diese Farbe versucht, sonst die
     * erste erreichbare.
     */
    private RouterMatch findRouter() {
        int maxHops = Config.CABLE_MAX_LENGTH.getAsInt();
        Iterable<DyeColor> colors = preferredColor != null ? List.of(preferredColor) : adjacentCableColors();
        for (DyeColor color : colors) {
            for (BlockPos devicePos : NetworkManager.discoverOnColor(level, worldPosition, color, maxHops)) {
                if (level.getBlockEntity(devicePos) instanceof RouterBlockEntity router) {
                    return new RouterMatch(router, color);
                }
            }
        }
        return null;
    }

    /** Setzt das bevorzugte Netz (Farbe) und verbindet darauf neu. */
    public void selectNetwork(DyeColor color) {
        this.preferredColor = color;
        requestDhcp();
    }

    /** Bitmaske der Farben aller angrenzenden Kabel (für die GUI-Auswahl). */
    public int availableColorMask() {
        int mask = 0;
        if (level != null) {
            for (DyeColor c : adjacentCableColors()) mask |= (1 << c.getId());
        }
        return mask;
    }

    /** Holt sich automatisch eine freie IP per DHCP. */
    public boolean requestDhcp() {
        if (level == null || level.isClientSide) return false;
        NetworkManager.unregisterDevice(worldPosition); // alte IP zuerst freigeben

        RouterMatch match = findRouter();
        if (match == null) return fail();
        IPAddress assigned = match.router().assignDHCP();
        if (assigned == null) return fail();
        return apply(match, assigned);
    }

    /** Verbindet mit einer manuell gewählten, freien IP im Subnetz des Routers. */
    public boolean requestStatic(IPAddress manualIp) {
        if (level == null || level.isClientSide) return false;

        RouterMatch match = findRouter();
        if (match == null) return fail(); // kein Netz erreichbar → trennen

        // Erst prüfen, dann übernehmen — eine ungültige Eingabe lässt die bestehende
        // Verbindung unangetastet (eigene aktuelle IP zählt nicht als "belegt").
        SubnetMask mask = SubnetMask.fromCIDR(match.router().getCidrPrefix());
        if (!manualIp.isInSameSubnet(match.router().getIpAddress(), mask)) return false;     // falsches Subnetz
        if (NetworkManager.isIPTaken(manualIp) && !manualIp.equals(ipAddress)) return false; // schon belegt

        NetworkManager.unregisterDevice(worldPosition); // alte IP freigeben, dann neue setzen
        return apply(match, manualIp);
    }

    private boolean apply(RouterMatch match, IPAddress ip) {
        this.ipAddress = ip;
        this.gateway = match.router().getIpAddress();
        this.cidrPrefix = match.router().getCidrPrefix();
        this.networkColor = match.color();
        this.connected = true;
        NetworkManager.registerDevice(worldPosition, ip);
        syncToClient();
        return true;
    }

    private boolean fail() {
        this.connected = false;
        syncToClient();
        return false;
    }

    /** Farben aller direkt angrenzenden Kabel. */
    private Set<DyeColor> adjacentCableColors() {
        EnumSet<DyeColor> colors = EnumSet.noneOf(DyeColor.class);
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(dir)) instanceof NetworkCableBlockEntity cable) {
                colors.addAll(cable.getColors());
            }
        }
        return colors;
    }

    private void syncToClient() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Beim Laden die belegte IP wieder im NetworkManager registrieren (Registry ist nicht persistent). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && connected && ipAddress != null) {
            NetworkManager.registerDevice(worldPosition, ipAddress);
        }
    }

    // === Getter (für das Menu) ===

    public boolean isConnected() { return connected; }
    public IPAddress getIpAddress() { return ipAddress; }
    public IPAddress getGateway() { return gateway; }
    public int getCidrPrefix() { return cidrPrefix; }
    public DyeColor getNetworkColor() { return networkColor; }
    public boolean hasIPAddress() { return ipAddress != null; }

    // === Menu ===

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.frogportnetworks.terminal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new TerminalMenu(containerId, playerInv, this);
    }

    /** Aktuelle Werte in den Öffnungs-Buffer (verhindert Sync-Race in der GUI). */
    public void writeToBuffer(FriendlyByteBuf buf) {
        buf.writeVarInt(connected ? 1 : 0);
        for (int i = 0; i < 4; i++) buf.writeVarInt(ipAddress == null ? 0 : ipAddress.getOctet(i));
        for (int i = 0; i < 4; i++) buf.writeVarInt(gateway == null ? 0 : gateway.getOctet(i));
        buf.writeVarInt(cidrPrefix);
        buf.writeVarInt(networkColor.getId());
        buf.writeVarInt(availableColorMask());
    }

    // === Persistenz ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("connected", connected);
        tag.putInt("cidr", cidrPrefix);
        tag.putString("color", networkColor.getSerializedName());
        if (preferredColor != null) tag.putString("preferred", preferredColor.getSerializedName());
        if (ipAddress != null) tag.putString("ip", ipAddress.toString());
        if (gateway != null) tag.putString("gateway", gateway.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.connected = tag.getBoolean("connected");
        if (tag.contains("cidr")) this.cidrPrefix = tag.getInt("cidr");
        if (tag.contains("color")) this.networkColor = DyeColor.byName(tag.getString("color"), DyeColor.WHITE);
        this.preferredColor = tag.contains("preferred") ? DyeColor.byName(tag.getString("preferred"), null) : null;
        this.ipAddress = tag.contains("ip") ? IPAddress.parse(tag.getString("ip")) : null;
        this.gateway = tag.contains("gateway") ? IPAddress.parse(tag.getString("gateway")) : null;
    }
}
