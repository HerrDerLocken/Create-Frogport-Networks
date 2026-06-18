package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.menu.TerminalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * SelectScopePacket — Client → Server: teilt dem offenen Terminal-Menü mit, welcher Tab
 * (Scope) gewählt ist, damit Shift-Klick-Einlagern gezielt in DAS NAS/Subnetz geht
 * (statt immer ins erste der Aggregat-Liste). {@code scope} = Terminal-Pos für "All".
 */
public record SelectScopePacket(BlockPos scope) implements CustomPacketPayload {

    public static final Type<SelectScopePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "select_scope"));

    public static final StreamCodec<FriendlyByteBuf, SelectScopePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBlockPos(p.scope()),
                    buf -> new SelectScopePacket(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SelectScopePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof TerminalMenu tm) {
                tm.setTargetScope(packet.scope());
            }
        });
    }
}
