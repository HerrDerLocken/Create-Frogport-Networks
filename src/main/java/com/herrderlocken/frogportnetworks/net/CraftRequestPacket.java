package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.ComputerBlockEntity;
import com.herrderlocken.frogportnetworks.blockentity.TerminalBlockEntity;
import com.herrderlocken.frogportnetworks.network.RoutingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * CraftRequestPacket — Client → Server: das Terminal soll {@code amount}× {@code proto}
 * craften lassen. Der Server sucht einen über das Netz (multi-hop) erreichbaren, laufenden
 * {@link ComputerBlockEntity} und übergibt ihm den Auftrag.
 */
public record CraftRequestPacket(BlockPos terminalPos, ItemStack proto, int amount) implements CustomPacketPayload {

    public static final Type<CraftRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "craft_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftRequestPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CraftRequestPacket::terminalPos,
            ItemStack.STREAM_CODEC, CraftRequestPacket::proto,
            ByteBufCodecs.VAR_INT, CraftRequestPacket::amount,
            CraftRequestPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CraftRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = packet.terminalPos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            if (packet.proto().isEmpty() || packet.amount() <= 0) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof TerminalBlockEntity terminal)) return;

            ComputerBlockEntity computer = null;
            for (BlockPos p : RoutingManager.reachableStorages(player.level(), pos, terminal.getNetworkColor())) {
                if (player.level().getBlockEntity(p) instanceof ComputerBlockEntity c && c.isPowered() && !c.isBusy()) {
                    computer = c;
                    break;
                }
            }
            if (computer == null) {
                player.displayClientMessage(Component.translatable("message.frogportnetworks.no_computer"), true);
                return;
            }
            computer.requestCraft(packet.proto(), packet.amount());
            player.displayClientMessage(Component.translatable("message.frogportnetworks.craft_started",
                    packet.amount(), packet.proto().getHoverName()), true);
        });
    }
}
