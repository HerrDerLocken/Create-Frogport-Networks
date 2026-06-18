package com.herrderlocken.frogportnetworks.blockentity;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Eigener Unpacking-Handler für den {@link NetworkPortBlockEntity}. Create's
 * {@code DefaultUnpackingHandler} setzt normale Slot-Semantik voraus (jeder Slot
 * ≤ getSlotLimit, genug freie Slots) und scheitert daher an unserem virtuellen,
 * netzweiten IItemHandler (ein freier Slot, riesige Mengen pro Slot) sobald ein
 * Paket mehr als einen Stack/Typ enthält. Dieser Handler schiebt den Paketinhalt
 * stattdessen direkt in den Netz-Speicher.
 *
 * Vertrag (aus Create dekompiliert): {@code simulate == true} → nur prüfen, ob alles
 * passt (nichts verändern); {@code simulate == false} → wirklich einlagern und die
 * Rest-Mengen in die übergebene Liste zurückschreiben.
 */
public class NetworkPortUnpackingHandler implements UnpackingHandler {

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side,
                          List<ItemStack> stacks, PackageOrderWithCrafts order, boolean simulate) {
        if (!(level.getBlockEntity(pos) instanceof NetworkPortBlockEntity port)) return false;
        if (!port.getMode().allowsInsert()) return false;

        boolean allInserted = true;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            if (s.isEmpty()) continue;
            if (!port.filterAllows(s)) { allInserted = false; continue; }

            long inserted = port.insert(s, s.getCount(), simulate);
            if (inserted < s.getCount()) allInserted = false;
            if (!simulate) {
                ItemStack remainder = s.copy();
                remainder.shrink((int) inserted);
                stacks.set(i, remainder);
            }
        }
        return allInserted;
    }
}
