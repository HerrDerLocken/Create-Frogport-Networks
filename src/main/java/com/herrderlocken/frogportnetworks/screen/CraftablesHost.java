package com.herrderlocken.frogportnetworks.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Vom Terminal-Screen implementiert, damit eingehende {@code CraftablesPacket} den
 * Craftbar-Index der richtigen offenen GUI aktualisieren.
 */
public interface CraftablesHost {
    BlockPos craftablesPos();
    void onCraftables(List<ItemStack> items);
}
