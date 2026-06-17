package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
@EventBusSubscriber(modid = CreateFrogportNetworks.MODID, bus = EventBusSubscriber.Bus.MOD)
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
    }
}
