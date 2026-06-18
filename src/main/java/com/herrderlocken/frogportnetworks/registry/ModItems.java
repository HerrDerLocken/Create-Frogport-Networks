package com.herrderlocken.frogportnetworks.registry;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.item.CableBlockItem;
import com.herrderlocken.frogportnetworks.item.ComputerUpgrade;
import com.herrderlocken.frogportnetworks.item.ComputerUpgradeItem;
import com.herrderlocken.frogportnetworks.item.StorageDiskItem;
import com.herrderlocken.frogportnetworks.network.CableType;
import com.herrderlocken.frogportnetworks.storage.DiskTier;
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

    public static final DeferredItem<BlockItem> NETWORK_PORT_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("network_port", ModBlocks.NETWORK_PORT);

    public static final DeferredItem<BlockItem> NETWORK_MONITOR_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("network_monitor", ModBlocks.NETWORK_MONITOR);

    public static final DeferredItem<BlockItem> NETWORK_BRIDGE_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("network_bridge", ModBlocks.NETWORK_BRIDGE);

    public static final DeferredItem<BlockItem> NETWORK_GATEWAY_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("network_gateway", ModBlocks.NETWORK_GATEWAY);

    public static final DeferredItem<BlockItem> COMPUTER_ITEM =
            CreateFrogportNetworks.ITEMS.registerSimpleBlockItem("computer", ModBlocks.COMPUTER);

    /** Computer-Upgrades (in die Upgrade-Slots des Computers). */
    public static final DeferredItem<ComputerUpgradeItem> AI_CHIP =
            CreateFrogportNetworks.ITEMS.registerItem("ai_chip", p -> new ComputerUpgradeItem(p, ComputerUpgrade.AI));
    public static final DeferredItem<ComputerUpgradeItem> MIXING_UPGRADE =
            CreateFrogportNetworks.ITEMS.registerItem("mixing_upgrade", p -> new ComputerUpgradeItem(p, ComputerUpgrade.MIXING));
    public static final DeferredItem<ComputerUpgradeItem> PRESSING_UPGRADE =
            CreateFrogportNetworks.ITEMS.registerItem("pressing_upgrade", p -> new ComputerUpgradeItem(p, ComputerUpgrade.PRESSING));
    public static final DeferredItem<ComputerUpgradeItem> DEPLOYING_UPGRADE =
            CreateFrogportNetworks.ITEMS.registerItem("deploying_upgrade", p -> new ComputerUpgradeItem(p, ComputerUpgrade.DEPLOYING));
    public static final DeferredItem<ComputerUpgradeItem> MILLING_UPGRADE =
            CreateFrogportNetworks.ITEMS.registerItem("milling_upgrade", p -> new ComputerUpgradeItem(p, ComputerUpgrade.MILLING));
    public static final DeferredItem<ComputerUpgradeItem> HAUNTING_UPGRADE =
            CreateFrogportNetworks.ITEMS.registerItem("haunting_upgrade", p -> new ComputerUpgradeItem(p, ComputerUpgrade.HAUNTING));
    public static final DeferredItem<ComputerUpgradeItem> SMELTING_UPGRADE =
            CreateFrogportNetworks.ITEMS.registerItem("smelting_upgrade", p -> new ComputerUpgradeItem(p, ComputerUpgrade.SMELTING));
    public static final DeferredItem<ComputerUpgradeItem> SMOKING_UPGRADE =
            CreateFrogportNetworks.ITEMS.registerItem("smoking_upgrade", p -> new ComputerUpgradeItem(p, ComputerUpgrade.SMOKING));
    public static final DeferredItem<ComputerUpgradeItem> WASHING_UPGRADE =
            CreateFrogportNetworks.ITEMS.registerItem("washing_upgrade", p -> new ComputerUpgradeItem(p, ComputerUpgrade.WASHING));

    /** Speicher-Disks (AE2-artig): in ein NAS-Laufwerk gesteckt stellen sie Kapazität bereit. */
    public static final DeferredItem<StorageDiskItem> DISK_16K =
            CreateFrogportNetworks.ITEMS.registerItem("storage_disk_16k",
                    props -> new StorageDiskItem(props, DiskTier.K16));
    public static final DeferredItem<StorageDiskItem> DISK_64K =
            CreateFrogportNetworks.ITEMS.registerItem("storage_disk_64k",
                    props -> new StorageDiskItem(props, DiskTier.K64));
    public static final DeferredItem<StorageDiskItem> DISK_256K =
            CreateFrogportNetworks.ITEMS.registerItem("storage_disk_256k",
                    props -> new StorageDiskItem(props, DiskTier.K256));
    public static final DeferredItem<StorageDiskItem> DISK_1M =
            CreateFrogportNetworks.ITEMS.registerItem("storage_disk_1m",
                    props -> new StorageDiskItem(props, DiskTier.M1));

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
