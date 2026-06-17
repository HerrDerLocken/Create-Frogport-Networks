package com.herrderlocken.frogportnetworks.registry;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.item.CableBlockItem;
import com.herrderlocken.frogportnetworks.network.CableType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * ModItems — registriert alle Items.
 *
 * Jeder Block braucht ein zugehöriges BlockItem damit er im Inventar
 * existieren kann. registerSimpleBlockItem() erstellt automatisch
 * ein BlockItem das den Block platziert wenn man Rechtsklick macht.
 *
 * Die Kabel gibt es als drei Typen (Kupfer/Gold/Glasfaser); platziert werden
 * "Standard"-Kabel, gefärbt wird in der Welt mit Farbstoff.
 */
public class ModItems {

    public static final DeferredItem<BlockItem> ROUTER_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("router", ModBlocks.ROUTER);

    public static final DeferredItem<CableBlockItem> COPPER_CABLE =
            CreateFrogportNetworks.ITEMS.registerItem("copper_cable",
                    props -> new CableBlockItem(ModBlocks.NETWORK_CABLE.get(), props, CableType.COPPER));

    public static final DeferredItem<CableBlockItem> GOLD_CABLE =
            CreateFrogportNetworks.ITEMS.registerItem("gold_cable",
                    props -> new CableBlockItem(ModBlocks.NETWORK_CABLE.get(), props, CableType.GOLD));

    public static final DeferredItem<CableBlockItem> FIBER_CABLE =
            CreateFrogportNetworks.ITEMS.registerItem("fiber_cable",
                    props -> new CableBlockItem(ModBlocks.NETWORK_CABLE.get(), props, CableType.FIBER));

    public static final DeferredItem<BlockItem> NAS_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("nas", ModBlocks.NAS);

    public static final DeferredItem<BlockItem> TERMINAL_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("terminal", ModBlocks.TERMINAL);

    /** Baut einen Standard-Kabel-ItemStack des passenden Typs. */
    public static ItemStack cableStack(CableType type, int count) {
        DeferredItem<CableBlockItem> item = switch (type) {
            case COPPER -> COPPER_CABLE;
            case GOLD -> GOLD_CABLE;
            case FIBER -> FIBER_CABLE;
        };
        return new ItemStack(item.get(), count);
    }

    public static void register() {}
}
