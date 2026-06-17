package com.herrderlocken.frogportnetworks.registry;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.RouterBlockEntity;
import com.herrderlocken.frogportnetworks.blockentity.NASBlockEntity;
import com.herrderlocken.frogportnetworks.blockentity.NetworkCableBlockEntity;
import com.herrderlocken.frogportnetworks.blockentity.TerminalBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ModBlockEntities — registriert alle BlockEntity-Typen.
 *
 * BlockEntityType ist eine "Factory": sie sagt Minecraft welche
 * BlockEntity-Klasse zu welchem Block gehört und wie man sie erstellt.
 *
 * BlockEntityType.Builder.of(Konstruktor, Block):
 *   - Konstruktor = Method Reference zum new-Aufruf (z.B. RouterBlockEntity::new)
 *   - Block = welche Blöcke diese BlockEntity haben dürfen
 *
 * .build(null) — der Parameter wäre ein DataFixer für alte Welt-Upgrades.
 *   Mods übergeben hier null, das ist nur für Vanilla-Minecraft relevant.
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, CreateFrogportNetworks.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RouterBlockEntity>> ROUTER =
            BLOCK_ENTITIES.register("router",
                    () -> BlockEntityType.Builder.of(RouterBlockEntity::new,
                            ModBlocks.ROUTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NASBlockEntity>> NAS =
            BLOCK_ENTITIES.register("nas",
                    () -> BlockEntityType.Builder.of(NASBlockEntity::new,
                            ModBlocks.NAS.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkCableBlockEntity>> CABLE =
            BLOCK_ENTITIES.register("network_cable",
                    () -> BlockEntityType.Builder.of(NetworkCableBlockEntity::new,
                            ModBlocks.NETWORK_CABLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> TERMINAL =
            BLOCK_ENTITIES.register("terminal",
                    () -> BlockEntityType.Builder.of(TerminalBlockEntity::new,
                            ModBlocks.TERMINAL.get()).build(null));

    public static void register() {}
}
