package com.herrderlocken.frogportnetworks;

import com.herrderlocken.frogportnetworks.client.CableRenderer;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.herrderlocken.frogportnetworks.item.CableBlockItem;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.herrderlocken.frogportnetworks.registry.ModItems;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import com.herrderlocken.frogportnetworks.screen.NASScreen;
import com.herrderlocken.frogportnetworks.screen.NetworkPortScreen;
import com.herrderlocken.frogportnetworks.screen.NetworkMonitorScreen;
import com.herrderlocken.frogportnetworks.screen.NetworkBridgeScreen;
import com.herrderlocken.frogportnetworks.screen.ComputerScreen;
import com.herrderlocken.frogportnetworks.screen.RouterScreen;
import com.herrderlocken.frogportnetworks.screen.TerminalScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only Mod-Klasse.
 *
 * @EventBusSubscriber lauscht auf Events am Mod-Event-Bus.
 * RegisterMenuScreensEvent verbindet MenuType → Screen-Klasse:
 * "Wenn der Server ein RouterMenu öffnet, zeige dem Client einen RouterScreen."
 */
@Mod(value = CreateFrogportNetworks.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(
        modid = CreateFrogportNetworks.MODID,
        value = Dist.CLIENT
)
public class CreateFrogportNetworksClient {

    public CreateFrogportNetworksClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * Registriert welcher Screen zu welchem MenuType gehört.
     * Ohne diese Registrierung würde der Client nicht wissen
     * welche GUI er anzeigen soll wenn der Server ein Menu öffnet.
     */
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ROUTER_MENU.get(), RouterScreen::new);
        event.register(ModMenuTypes.TERMINAL_MENU.get(), TerminalScreen::new);
        event.register(ModMenuTypes.NAS_MENU.get(), NASScreen::new);
        event.register(ModMenuTypes.NETWORK_PORT_MENU.get(), NetworkPortScreen::new);
        event.register(ModMenuTypes.NETWORK_MONITOR_MENU.get(), NetworkMonitorScreen::new);
        event.register(ModMenuTypes.NETWORK_BRIDGE_MENU.get(), NetworkBridgeScreen::new);
        event.register(ModMenuTypes.COMPUTER_MENU.get(), ComputerScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CreateFrogportNetworks.LOGGER.info("Frogport Networks: Client setup complete.");
    }

    /** Verbindet den BlockEntityRenderer mit dem Kabel-BlockEntity-Typ. */
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CABLE.get(), CableRenderer::create);
        // Router: rotierende Welle entlang seiner Achse (Create-Kinetik-Optik).
        event.registerBlockEntityRenderer(ModBlockEntities.ROUTER.get(), ShaftRenderer::new);
    }

    /** Färbt das Kabel-Icon nach Kabel-TYP (Kupfer/Gold/Glasfaser), damit man den Typ im Inventar sieht. */
    @SubscribeEvent
    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> tintIndex == 0 && stack.getItem() instanceof CableBlockItem cable
                        ? 0xFF000000 | cable.getCableType().getBaseColor()
                        : 0xFFFFFFFF,
                ModItems.COPPER_CABLE.get(), ModItems.GOLD_CABLE.get(), ModItems.FIBER_CABLE.get());
    }
}
