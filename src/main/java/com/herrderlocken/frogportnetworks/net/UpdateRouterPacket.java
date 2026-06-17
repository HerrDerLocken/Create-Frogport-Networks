package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.RouterBlockEntity;
import com.herrderlocken.frogportnetworks.network.IPAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * UpdateRouterPacket — wird vom Client zum Server geschickt wenn der Spieler "Apply" klickt.
 *
 * In NeoForge 1.21.x funktioniert Networking über CustomPacketPayload:
 * - record = die Daten die übertragen werden
 * - STREAM_CODEC = wie die Daten serialisiert/deserialisiert werden
 * - TYPE = eindeutige ID für das Paket
 * - handle() = was auf dem Server passieren soll wenn das Paket ankommt
 *
 * Records sind perfekt dafür weil sie automatisch immutable sind
 * und alle Felder im Konstruktor definiert werden.
 */
public record UpdateRouterPacket(
        BlockPos pos,
        int ip0, int ip1, int ip2, int ip3,
        int cidrPrefix,
        boolean dhcpEnabled,
        int dhcpPoolStart,
        int dhcpPoolEnd
) implements CustomPacketPayload {

    // Eindeutige ID für dieses Paket
    public static final Type<UpdateRouterPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "update_router"));

    /**
     * StreamCodec definiert die Serialisierung.
     * Liest/Schreibt die Felder in der gleichen Reihenfolge aus dem Netzwerk-Buffer.
     * composite() kombiniert mehrere Codecs zu einem — wie ein Builder.
     */
    public static final StreamCodec<FriendlyByteBuf, UpdateRouterPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.pos());
                        buf.writeVarInt(packet.ip0());
                        buf.writeVarInt(packet.ip1());
                        buf.writeVarInt(packet.ip2());
                        buf.writeVarInt(packet.ip3());
                        buf.writeVarInt(packet.cidrPrefix());
                        buf.writeBoolean(packet.dhcpEnabled());
                        buf.writeVarInt(packet.dhcpPoolStart());
                        buf.writeVarInt(packet.dhcpPoolEnd());
                    },
                    buf -> new UpdateRouterPacket(
                            buf.readBlockPos(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readVarInt(), buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /**
     * Server-seitiger Handler — wird aufgerufen wenn das Paket ankommt.
     *
     * Sicherheitschecks sind wichtig! Ein manipulierter Client könnte
     * falsche BlockPos schicken um fremde Router zu ändern.
     * Deshalb prüfen wir:
     * 1. Ist der Spieler nah genug am Block?
     * 2. Ist an der Position wirklich ein Router?
     * 3. Sind die Werte gültig?
     */
    public static void handle(UpdateRouterPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            // Sicherheitscheck: Spieler muss in Reichweite sein
            BlockPos pos = packet.pos();
            if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                CreateFrogportNetworks.LOGGER.warn("Player {} too far from router at {}", serverPlayer.getName().getString(), pos);
                return;
            }

            // Block Entity holen und validieren
            BlockEntity be = serverPlayer.level().getBlockEntity(pos);
            if (!(be instanceof RouterBlockEntity router)) {
                CreateFrogportNetworks.LOGGER.warn("No router at {}", pos);
                return;
            }

            // Werte validieren
            if (!isValidOctet(packet.ip0()) || !isValidOctet(packet.ip1())
                    || !isValidOctet(packet.ip2()) || !isValidOctet(packet.ip3())) {
                CreateFrogportNetworks.LOGGER.warn("Invalid IP octets");
                return;
            }
            if (packet.cidrPrefix() < 0 || packet.cidrPrefix() > 32) return;
            if (packet.dhcpPoolStart() < 2 || packet.dhcpPoolEnd() > 254) return;
            if (packet.dhcpPoolStart() > packet.dhcpPoolEnd()) return;

            // Alles okay → Werte setzen
            router.setIpAddress(new IPAddress(packet.ip0(), packet.ip1(), packet.ip2(), packet.ip3()));
            router.setCidrPrefix(packet.cidrPrefix());
            router.setDhcpEnabled(packet.dhcpEnabled());
            router.setDhcpPoolStart(packet.dhcpPoolStart());
            router.setDhcpPoolEnd(packet.dhcpPoolEnd());

            CreateFrogportNetworks.LOGGER.info("Router at {} updated: IP={}, CIDR=/{}, DHCP={}",
                    pos, router.getIpAddress(), packet.cidrPrefix(), packet.dhcpEnabled());
        });
    }

    private static boolean isValidOctet(int value) {
        return value >= 0 && value <= 255;
    }
}
