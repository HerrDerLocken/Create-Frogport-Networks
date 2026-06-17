package com.herrderlocken.frogportnetworks;

import com.mojang.logging.LogUtils;
import com.herrderlocken.frogportnetworks.block.NetworkCableBlock;
import com.herrderlocken.frogportnetworks.blockentity.NetworkCableBlockEntity;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.herrderlocken.frogportnetworks.registry.ModBlocks;
import com.herrderlocken.frogportnetworks.registry.ModDataComponents;
import com.herrderlocken.frogportnetworks.registry.ModItems;
import com.herrderlocken.frogportnetworks.registry.ModCreativeTabs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.herrderlocken.frogportnetworks.registry.ModMenuTypes;

@Mod(CreateFrogportNetworks.MODID)
public class CreateFrogportNetworks {

    public static final String MODID = "frogportnetworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Zentrale DeferredRegister — werden von den Registry-Klassen befüllt
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public CreateFrogportNetworks(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // Alle DeferredRegister am Mod-Event-Bus anmelden
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        ModMenuTypes.register();

        // Statische Initialisierung der Registry-Klassen auslösen
        // Ohne diese Aufrufe würden die DeferredBlock/DeferredItem-Felder
        // nie geladen und die Blöcke/Items nie registriert
        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModDataComponents.register();
        ModCreativeTabs.register();

        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Frogport Networks loaded.");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Frogport Networks: Server starting.");
    }

    /**
     * Modularer Abbau: Ein Linksklick auf einen Kabelblock entfernt sofort nur den
     * anvisierten Strang (und droppt ihn); der Block bleibt, solange weitere Stränge
     * vorhanden sind. So reagiert jeder Klick direkt, statt erst nach voller Abbauzeit.
     */
    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof NetworkCableBlockEntity be)) return;

        DyeColor target = NetworkCableBlock.pickStrandLookingAt(level, pos, event.getEntity());
        if (target == null) return;

        // Auf BEIDEN Seiten abbrechen → verhindert auch im Creative das sofortige
        // Wegbrechen des ganzen Blocks (Client-Vorhersage). Mutiert wird nur serverseitig.
        event.setCanceled(true);
        if (level.isClientSide) return;

        var type = be.removeSegment(target);
        if (type != null && !event.getEntity().getAbilities().instabuild) {
            Block.popResource(level, pos, ModItems.cableStack(type, 1));
        }
        if (be.isEmpty()) {
            level.removeBlock(pos, false);
        }
    }
}
