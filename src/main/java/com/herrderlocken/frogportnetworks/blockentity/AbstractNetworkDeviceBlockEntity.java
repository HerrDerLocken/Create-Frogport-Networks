package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.Config;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.network.NetworkManager;
import com.herrderlocken.frogportnetworks.network.SubnetMask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Gemeinsame Basis für alle Netzwerkgeräte, die sich eine IP holen (Terminal, NAS).
 *
 * Kapselt die DHCP-/Statisch-/Netz-Auswahl-Logik, die Registrierung im
 * {@link NetworkManager} und die NBT-/Buffer-Serialisierung des Netz-Status.
 * Jedes Gerät sucht über die angrenzenden Kabel (farb-bewusst) einen Router und
 * fordert dort eine IP an. Unterklassen ergänzen ihre Eigenheiten (z.B. das
 * NAS-Inventar) und liefern Menu/DisplayName (siehe {@link MenuProvider}).
 */
public abstract class AbstractNetworkDeviceBlockEntity extends BlockEntity implements MenuProvider {

    protected IPAddress ipAddress;
    protected IPAddress gateway;
    protected int cidrPrefix = 24;
    protected DyeColor networkColor = DyeColor.WHITE;
    protected boolean connected;
    /** Vom Spieler gewähltes Netz (Farbe). null = automatisch (erstes erreichbares). */
    protected DyeColor preferredColor;

    protected AbstractNetworkDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // === Verbinden ===

    protected record RouterMatch(RouterBlockEntity router, DyeColor color) {}

    /**
     * Sucht über angrenzende Kabel (farb-bewusst) einen Router. Hat der Spieler ein
     * Netz gewählt ({@link #preferredColor}), wird NUR diese Farbe versucht, sonst die
     * erste erreichbare.
     */
    protected RouterMatch findRouter() {
        int maxHops = Config.CABLE_MAX_LENGTH.getAsInt();
        Iterable<DyeColor> colors = preferredColor != null ? List.of(preferredColor) : adjacentCableColors();
        for (DyeColor color : colors) {
            for (BlockPos devicePos : NetworkManager.discoverOnColor(level, worldPosition, color, maxHops)) {
                if (level.getBlockEntity(devicePos) instanceof RouterBlockEntity router && router.isPowered()) {
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

    protected boolean apply(RouterMatch match, IPAddress ip) {
        this.ipAddress = ip;
        this.gateway = match.router().getIpAddress();
        this.cidrPrefix = match.router().getCidrPrefix();
        this.networkColor = match.color();
        this.connected = true;
        NetworkManager.registerDevice(worldPosition, ip);
        syncToClient();
        return true;
    }

    protected boolean fail() {
        this.connected = false;
        syncToClient();
        return false;
    }

    /**
     * Trennt das Gerät hart vom Netz (gibt die IP frei) — z.B. wenn der Router
     * stehen bleibt (keine Rotation mehr). Die {@link #preferredColor} bleibt
     * erhalten, damit beim Wieder-Anlaufen automatisch reconnectet werden kann.
     */
    public void disconnect() {
        if (level == null || level.isClientSide) return;
        NetworkManager.unregisterDevice(worldPosition);
        fail();
    }

    /** Bitmaske der Farben aller angrenzenden Kabel (für die GUI-Auswahl). */
    public int availableColorMask() {
        int mask = 0;
        if (level != null) {
            for (DyeColor c : adjacentCableColors()) mask |= (1 << c.getId());
        }
        return mask;
    }

    /** Farben aller direkt angrenzenden Kabel. */
    protected Set<DyeColor> adjacentCableColors() {
        EnumSet<DyeColor> colors = EnumSet.noneOf(DyeColor.class);
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(dir)) instanceof NetworkCableBlockEntity cable) {
                colors.addAll(cable.getColors());
            }
        }
        return colors;
    }

    protected void syncToClient() {
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

    // === Getter (für die Menus) ===

    public boolean isConnected() { return connected; }
    public IPAddress getIpAddress() { return ipAddress; }
    public IPAddress getGateway() { return gateway; }
    public int getCidrPrefix() { return cidrPrefix; }
    public DyeColor getNetworkColor() { return networkColor; }
    public boolean hasIPAddress() { return ipAddress != null; }

    /**
     * Schreibt den Netz-Status (12 VarInts) in den Menu-Öffnungs-Buffer.
     * Reihenfolge muss exakt mit dem Client-Konstruktor des jeweiligen Menus
     * übereinstimmen. Unterklassen können danach weitere Werte anhängen.
     */
    public void writeNetworkToBuffer(FriendlyByteBuf buf) {
        buf.writeVarInt(connected ? 1 : 0);
        for (int i = 0; i < 4; i++) buf.writeVarInt(ipAddress == null ? 0 : ipAddress.getOctet(i));
        for (int i = 0; i < 4; i++) buf.writeVarInt(gateway == null ? 0 : gateway.getOctet(i));
        buf.writeVarInt(cidrPrefix);
        buf.writeVarInt(networkColor.getId());
        buf.writeVarInt(availableColorMask());
    }

    // === Persistenz (von Unterklassen aus save-/loadAdditional aufrufen) ===

    protected void saveNetwork(CompoundTag tag) {
        tag.putBoolean("connected", connected);
        tag.putInt("cidr", cidrPrefix);
        tag.putString("color", networkColor.getSerializedName());
        if (preferredColor != null) tag.putString("preferred", preferredColor.getSerializedName());
        if (ipAddress != null) tag.putString("ip", ipAddress.toString());
        if (gateway != null) tag.putString("gateway", gateway.toString());
    }

    protected void loadNetwork(CompoundTag tag) {
        this.connected = tag.getBoolean("connected");
        if (tag.contains("cidr")) this.cidrPrefix = tag.getInt("cidr");
        if (tag.contains("color")) this.networkColor = DyeColor.byName(tag.getString("color"), DyeColor.WHITE);
        this.preferredColor = tag.contains("preferred") ? DyeColor.byName(tag.getString("preferred"), null) : null;
        this.ipAddress = tag.contains("ip") ? IPAddress.parse(tag.getString("ip")) : null;
        this.gateway = tag.contains("gateway") ? IPAddress.parse(tag.getString("gateway")) : null;
    }

    // === Client-Sync (für Schraubenbrille / client-seitige Status-Anzeige) ===
    // Schickt den Netz-Status an den Client, sobald syncToClient() ein Block-Update auslöst.

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveNetwork(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadNetwork(tag);
    }

    // Standard-NBT: Netz-Status sichern/laden. Unterklassen mit eigenen Daten
    // überschreiben diese und rufen super.saveAdditional/loadAdditional auf.
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveNetwork(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadNetwork(tag);
    }
}
