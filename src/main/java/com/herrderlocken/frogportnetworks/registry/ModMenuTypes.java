package com.herrderlocken.frogportnetworks.registry;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.menu.RouterMenu;
import com.herrderlocken.frogportnetworks.menu.TerminalMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ModMenuTypes — registriert alle MenuTypes (GUI-Typen).
 *
 * Jede GUI braucht einen MenuType der NeoForge sagt:
 * "Wenn der Server diesen Menu-Typ öffnen will, erstelle auf dem Client
 *  eine Instanz mit diesen Parametern."
 *
 * IMenuTypeExtension.create() erstellt einen MenuType der zusätzlich
 * einen FriendlyByteBuf akzeptiert — damit können wir die BlockPos
 * vom Server zum Client schicken.
 */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, CreateFrogportNetworks.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RouterMenu>> ROUTER_MENU =
            MENUS.register("router_menu",
                    () -> IMenuTypeExtension.create(RouterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL_MENU =
            MENUS.register("terminal_menu",
                    () -> IMenuTypeExtension.create(TerminalMenu::new));

    public static void register() {}
}
