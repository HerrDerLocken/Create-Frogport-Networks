package com.herrderlocken.frogportnetworks.registry;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.block.RouterBlock;
import com.herrderlocken.frogportnetworks.block.NetworkCableBlock;
import com.herrderlocken.frogportnetworks.block.NASBlock;
import com.herrderlocken.frogportnetworks.block.NetworkPortBlock;
import com.herrderlocken.frogportnetworks.block.NetworkMonitorBlock;
import com.herrderlocken.frogportnetworks.block.NetworkBridgeBlock;
import com.herrderlocken.frogportnetworks.block.NetworkGatewayBlock;
import com.herrderlocken.frogportnetworks.block.ComputerBlock;
import com.herrderlocken.frogportnetworks.block.TerminalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * ModBlocks — registriert alle Blöcke beim Spiel.
 *
 * DeferredRegister ist NeoForge's Weg um Blöcke "lazy" zu registrieren:
 * die Objekte werden erst erstellt wenn Minecraft bereit ist, nicht beim Classloading.
 * Das verhindert Race Conditions beim Mod-Laden.
 *
 * Jeder Block bekommt:
 * - destroyTime: wie lange der Abbau dauert (Stein = 1.5, Obsidian = 50)
 * - requiresCorrectToolForDrops: nur mit richtigem Tool abbaubar
 * - sound: Geräusche beim Platzieren/Abbauen
 */
public class ModBlocks {

    public static final DeferredBlock<RouterBlock> ROUTER =
            CreateFrogportNetworks.BLOCKS.register("router",
                    () -> new RouterBlock(BlockBehaviour.Properties.of()
                            .destroyTime(2.0f)
                            .explosionResistance(6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<NetworkCableBlock> NETWORK_CABLE =
            CreateFrogportNetworks.BLOCKS.register("network_cable",
                    () -> new NetworkCableBlock(BlockBehaviour.Properties.of()
                            .destroyTime(0.5f)
                            .sound(SoundType.WOOL)    // Kabel klingen weich
                            .noOcclusion()));          // Nicht lichtblockierend

    public static final DeferredBlock<NASBlock> NAS =
            CreateFrogportNetworks.BLOCKS.register("nas",
                    () -> new NASBlock(BlockBehaviour.Properties.of()
                            .destroyTime(2.0f)
                            .explosionResistance(6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<NetworkPortBlock> NETWORK_PORT =
            CreateFrogportNetworks.BLOCKS.register("network_port",
                    () -> new NetworkPortBlock(BlockBehaviour.Properties.of()
                            .destroyTime(2.0f)
                            .explosionResistance(6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<NetworkMonitorBlock> NETWORK_MONITOR =
            CreateFrogportNetworks.BLOCKS.register("network_monitor",
                    () -> new NetworkMonitorBlock(BlockBehaviour.Properties.of()
                            .destroyTime(2.0f)
                            .explosionResistance(6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<NetworkBridgeBlock> NETWORK_BRIDGE =
            CreateFrogportNetworks.BLOCKS.register("network_bridge",
                    () -> new NetworkBridgeBlock(BlockBehaviour.Properties.of()
                            .destroyTime(2.0f)
                            .explosionResistance(6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<NetworkGatewayBlock> NETWORK_GATEWAY =
            CreateFrogportNetworks.BLOCKS.register("network_gateway",
                    () -> new NetworkGatewayBlock(BlockBehaviour.Properties.of()
                            .destroyTime(2.0f)
                            .explosionResistance(6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<ComputerBlock> COMPUTER =
            CreateFrogportNetworks.BLOCKS.register("computer",
                    () -> new ComputerBlock(BlockBehaviour.Properties.of()
                            .destroyTime(2.5f)
                            .explosionResistance(6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<TerminalBlock> TERMINAL =
            CreateFrogportNetworks.BLOCKS.register("terminal",
                    () -> new TerminalBlock(BlockBehaviour.Properties.of()
                            .destroyTime(1.5f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    public static void register() {}
}
