package com.herrderlocken.frogportnetworks;

import com.herrderlocken.frogportnetworks.client.CableRenderer;
import com.herrderlocken.frogportnetworks.item.CableBlockItem;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.herrderlocken.frogportnetworks.registry.ModItems;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;
import com.herrderlocken.frogportnetworks.screen.RouterScreen;
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
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CreateFrogportNetworks.LOGGER.info("Frogport Networks: Client setup complete.");
    }

    /** Verbindet den BlockEntityRenderer mit dem Kabel-BlockEntity-Typ. */
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CABLE.get(), CableRenderer::create);
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
