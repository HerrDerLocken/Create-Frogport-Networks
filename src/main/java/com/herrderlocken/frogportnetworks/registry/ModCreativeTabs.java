package com.herrderlocken.frogportnetworks.registry;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * ModCreativeTabs — eigener Tab im Creative-Menü.
 *
 * Ohne eigenen Tab würden unsere Items nur über /give erreichbar sein.
 * displayItems() füllt den Tab mit allen registrierten Items.
 */
public class ModCreativeTabs {

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FROGPORT_NETWORKS_TAB =
            CreateFrogportNetworks.CREATIVE_MODE_TABS.register("main_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.frogportnetworks"))
                            .icon(() -> ModItems.ROUTER_ITEM.get().getDefaultInstance())
                            .displayItems((params, output) -> {
                                output.accept(ModItems.ROUTER_ITEM.get());
                                output.accept(ModItems.COPPER_CABLE.get());
                                output.accept(ModItems.GOLD_CABLE.get());
                                output.accept(ModItems.FIBER_CABLE.get());
                                output.accept(ModItems.NAS_ITEM.get());
                                output.accept(ModItems.TERMINAL_ITEM.get());
                                output.accept(ModItems.NETWORK_PORT_ITEM.get());
                                output.accept(ModItems.NETWORK_MONITOR_ITEM.get());
                                output.accept(ModItems.NETWORK_BRIDGE_ITEM.get());
                                output.accept(ModItems.NETWORK_GATEWAY_ITEM.get());
                                output.accept(ModItems.COMPUTER_ITEM.get());
                                output.accept(ModItems.AI_CHIP.get());
                                output.accept(ModItems.MIXING_UPGRADE.get());
                                output.accept(ModItems.PRESSING_UPGRADE.get());
                                output.accept(ModItems.DEPLOYING_UPGRADE.get());
                                output.accept(ModItems.MILLING_UPGRADE.get());
                                output.accept(ModItems.HAUNTING_UPGRADE.get());
                                output.accept(ModItems.SMELTING_UPGRADE.get());
                                output.accept(ModItems.SMOKING_UPGRADE.get());
                                output.accept(ModItems.WASHING_UPGRADE.get());
                                output.accept(ModItems.DISK_16K.get());
                                output.accept(ModItems.DISK_64K.get());
                                output.accept(ModItems.DISK_256K.get());
                                output.accept(ModItems.DISK_1M.get());
                            })
                            .build());

    public static void register() {}
}
