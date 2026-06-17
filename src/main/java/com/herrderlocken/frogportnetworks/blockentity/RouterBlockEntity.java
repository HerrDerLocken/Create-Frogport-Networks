package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.Config;
import com.herrderlocken.frogportnetworks.menu.RouterMenu;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.network.RoutingTable;
import com.herrderlocken.frogportnetworks.network.SubnetMask;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RouterBlockEntity extends BlockEntity implements MenuProvider {

    private IPAddress ipAddress;
    private SubnetMask subnetMask;
    private int cidrPrefix = 24;
    private final RoutingTable routingTable = new RoutingTable();

    private int dhcpPoolStart;
    private int dhcpPoolEnd;
    private int dhcpNextOffer;
    private boolean dhcpEnabled = true;

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTER.get(), pos, state);
        this.ipAddress = new IPAddress(10, 0, 0, 1);
        this.subnetMask = SubnetMask.fromCIDR(cidrPrefix);
        this.dhcpPoolStart = Config.DHCP_POOL_START.getAsInt();
        this.dhcpPoolEnd = Config.DHCP_POOL_END.getAsInt();
        this.dhcpNextOffer = dhcpPoolStart;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.frogportnetworks.router");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new RouterMenu(containerId, playerInv, this);
    }

    public IPAddress assignDHCP() {
        if (!dhcpEnabled || dhcpNextOffer > dhcpPoolEnd) return null;
        IPAddress newIp = new IPAddress(
                ipAddress.getOctet(0), ipAddress.getOctet(1),
                ipAddress.getOctet(2), dhcpNextOffer);
        dhcpNextOffer++;
        setChanged();
        return newIp;
    }

    public IPAddress getIpAddress() { return ipAddress; }
    public void setIpAddress(IPAddress ip) { this.ipAddress = ip; setChanged(); }
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("ip", ipAddress.toString());
        tag.putInt("cidr", cidrPrefix);
        tag.putInt("dhcpStart", dhcpPoolStart);
        tag.putInt("dhcpEnd", dhcpPoolEnd);
        tag.putInt("dhcpNext", dhcpNextOffer);
        tag.putBoolean("dhcpEnabled", dhcpEnabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ip")) this.ipAddress = IPAddress.parse(tag.getString("ip"));
        if (tag.contains("cidr")) {
            this.cidrPrefix = tag.getInt("cidr");
            this.subnetMask = SubnetMask.fromCIDR(cidrPrefix);
        }
        if (tag.contains("dhcpStart")) this.dhcpPoolStart = tag.getInt("dhcpStart");
        if (tag.contains("dhcpEnd")) this.dhcpPoolEnd = tag.getInt("dhcpEnd");
        if (tag.contains("dhcpNext")) this.dhcpNextOffer = tag.getInt("dhcpNext");
        if (tag.contains("dhcpEnabled")) this.dhcpEnabled = tag.getBoolean("dhcpEnabled");
    }
}
