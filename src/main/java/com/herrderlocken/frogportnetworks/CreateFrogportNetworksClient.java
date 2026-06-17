package com.herrderlocken.frogportnetworks;

import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import com.herrderlocken.frogportnetworks.screen.RouterScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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
@EventBusSubscriber(modid = CreateFrogportNetworks.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
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
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CreateFrogportNetworks.LOGGER.info("Frogport Networks: Client setup complete.");
    }
}
