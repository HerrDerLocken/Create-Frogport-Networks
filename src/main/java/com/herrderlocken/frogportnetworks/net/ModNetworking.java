package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.storage.DeviceSnapshot;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import net.minecraft.core.BlockPos;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * ModNetworking — registriert alle Custom-Pakete.
 *
 * NeoForge nutzt ein Event-basiertes System für Netzwerk-Registrierung.
 * RegisterPayloadHandlersEvent wird beim Mod-Laden gefeuert und dort
 * melden wir alle Pakettypen an.
 *
 * playToServer = Client → Server (z.B. "Spieler hat IP geändert")
 * playToClient = Server → Client (z.B. "Netzwerk-Status Update")
 */
@EventBusSubscriber(modid = CreateFrogportNetworks.MODID)
public class ModNetworking {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CreateFrogportNetworks.MODID)
                .versioned("1.0");

        // Client → Server: Router-Einstellungen ändern
        registrar.playToServer(
                UpdateRouterPacket.TYPE,
                UpdateRouterPacket.STREAM_CODEC,
                UpdateRouterPacket::handle
        );

        // Client → Server: Terminal per DHCP (neu) verbinden
        registrar.playToServer(
                RequestDhcpPacket.TYPE,
                RequestDhcpPacket.STREAM_CODEC,
                RequestDhcpPacket::handle
        );

        // Client → Server: Terminal mit manueller (statischer) IP verbinden
        registrar.playToServer(
                SetStaticIpPacket.TYPE,
                SetStaticIpPacket.STREAM_CODEC,
                SetStaticIpPacket::handle
        );

        // Client → Server: Terminal-Netz (Kabelfarbe) wählen
        registrar.playToServer(
                SelectNetworkPacket.TYPE,
                SelectNetworkPacket.STREAM_CODEC,
                SelectNetworkPacket::handle
        );

        // Client → Server: aktuellen Speicher-Inhalt anfordern
        registrar.playToServer(
                RequestStorageSnapshotPacket.TYPE,
                RequestStorageSnapshotPacket.STREAM_CODEC,
                RequestStorageSnapshotPacket::handle
        );

        // Client → Server: Item aus dem Speicher entnehmen
        registrar.playToServer(
                WithdrawItemPacket.TYPE,
                WithdrawItemPacket.STREAM_CODEC,
                WithdrawItemPacket::handle
        );

        // Server → Client: Speicher-Inhalt für die offene GUI
        registrar.playToClient(
                StorageSnapshotPacket.TYPE,
                StorageSnapshotPacket.STREAM_CODEC,
                StorageSnapshotPacket::handle
        );

        // Client → Server: netzweite Geräteliste anfordern (Terminal)
        registrar.playToServer(
                RequestNetworkSnapshotPacket.TYPE,
                RequestNetworkSnapshotPacket.STREAM_CODEC,
                RequestNetworkSnapshotPacket::handle
        );

        // Server → Client: netzweite Geräteliste fürs Terminal
        registrar.playToClient(
                NetworkSnapshotPacket.TYPE,
                NetworkSnapshotPacket.STREAM_CODEC,
                NetworkSnapshotPacket::handle
        );

        // Client → Server: Network-Port I/O-Modus setzen
        registrar.playToServer(
                SetPortModePacket.TYPE,
                SetPortModePacket.STREAM_CODEC,
                SetPortModePacket::handle
        );

        // Client → Server: Network-Port Filter-Slot setzen
        registrar.playToServer(
                SetPortFilterPacket.TYPE,
                SetPortFilterPacket.STREAM_CODEC,
                SetPortFilterPacket::handle
        );

        // Client → Server: Network-Bridge konfigurieren (Cross-Network-Routing)
        registrar.playToServer(
                UpdateBridgePacket.TYPE,
                UpdateBridgePacket.STREAM_CODEC,
                UpdateBridgePacket::handle
        );

        // Client → Server: gewählten Terminal-Tab (Scope) fürs Einlagern setzen
        registrar.playToServer(
                SelectScopePacket.TYPE,
                SelectScopePacket.STREAM_CODEC,
                SelectScopePacket::handle
        );

        // Client → Server: Crafting anfordern (Terminal → Computer)
        registrar.playToServer(
                CraftRequestPacket.TYPE,
                CraftRequestPacket.STREAM_CODEC,
                CraftRequestPacket::handle
        );

        // Client → Server: craftbare Items anfordern (Terminal-Index)
        registrar.playToServer(
                RequestCraftablesPacket.TYPE,
                RequestCraftablesPacket.STREAM_CODEC,
                RequestCraftablesPacket::handle
        );

        // Server → Client: craftbare Items fürs Terminal
        registrar.playToClient(
                CraftablesPacket.TYPE,
                CraftablesPacket.STREAM_CODEC,
                CraftablesPacket::handle
        );

        // Client → Server: Craft-Aufschlüsselung anfordern (Hover-Tooltip)
        registrar.playToServer(
                RequestCraftPlanPacket.TYPE,
                RequestCraftPlanPacket.STREAM_CODEC,
                RequestCraftPlanPacket::handle
        );

        // Server → Client: Craft-Aufschlüsselung fürs Terminal
        registrar.playToClient(
                CraftPlanPacket.TYPE,
                CraftPlanPacket.STREAM_CODEC,
                CraftPlanPacket::handle
        );
    }

    /** Schickt einem Spieler den aktuellen Speicher-Inhalt für die offene GUI. */
    public static void sendSnapshot(ServerPlayer player, BlockPos pos, StorageSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, new StorageSnapshotPacket(pos, snapshot));
    }

    /** Schickt einem Spieler die netzweite Geräteliste fürs Terminal. */
    public static void sendNetworkSnapshot(ServerPlayer player, BlockPos pos, List<DeviceSnapshot> devices) {
        PacketDistributor.sendToPlayer(player, new NetworkSnapshotPacket(pos, devices));
    }

    /** Schickt einem Spieler die craftbaren Items fürs Terminal. */
    public static void sendCraftables(ServerPlayer player, BlockPos pos, List<net.minecraft.world.item.ItemStack> items) {
        PacketDistributor.sendToPlayer(player, new CraftablesPacket(pos, items));
    }

    /** Schickt einem Spieler die Craft-Aufschlüsselung eines Items. */
    public static void sendCraftPlan(ServerPlayer player, BlockPos pos, net.minecraft.world.item.ItemStack proto,
                                     com.herrderlocken.frogportnetworks.craft.CraftEngine.Plan plan) {
        PacketDistributor.sendToPlayer(player, new CraftPlanPacket(pos, proto, plan.ok(),
                plan.consumed(), plan.crafted(), plan.missing(), plan.maxCrafts()));
    }
}
