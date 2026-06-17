package com.herrderlocken.frogportnetworks.registry;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * ModItems — registriert alle Items.
 *
 * Jeder Block braucht ein zugehöriges BlockItem damit er im Inventar
 * existieren kann. registerSimpleBlockItem() erstellt automatisch
 * ein BlockItem das den Block platziert wenn man Rechtsklick macht.
 *
 * Später kommen hier auch eigenständige Items hin:
 * - Netzwerk-Konfigurator (Phase 2)
 * - NIC (Network Interface Card) (Phase 3)
 */
public class ModItems {

    public static final DeferredItem<BlockItem> ROUTER_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("router", ModBlocks.ROUTER);

    public static final DeferredItem<BlockItem> NETWORK_CABLE_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("network_cable", ModBlocks.NETWORK_CABLE);

    public static final DeferredItem<BlockItem> NAS_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("nas", ModBlocks.NAS);

    public static final DeferredItem<BlockItem> TERMINAL_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("terminal", ModBlocks.TERMINAL);

    public static void register() {}
}
