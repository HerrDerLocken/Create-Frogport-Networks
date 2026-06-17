package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.network.IPAddress;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * TerminalBlockEntity — reine Netzwerk-Schnittstelle.
 *
 * Anders als der NAS hat das Terminal KEIN eigenes Inventar.
 * Es ist nur ein "Fenster" ins Netzwerk — es aggregiert die Inventare
 * aller erreichbaren NAS und zeigt sie dem Spieler an.
 *
 * Speichert nur die eigene IP-Adresse.
 */
public class TerminalBlockEntity extends BlockEntity {

    private IPAddress ipAddress;

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL.get(), pos, state);
    }

    public IPAddress getIpAddress() { return ipAddress; }

    public void setIpAddress(IPAddress ip) {
        this.ipAddress = ip;
        setChanged();
    }

    public boolean hasIPAddress() { return ipAddress != null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ipAddress != null) tag.putString("ip", ipAddress.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ip")) this.ipAddress = IPAddress.parse(tag.getString("ip"));
    }
}
