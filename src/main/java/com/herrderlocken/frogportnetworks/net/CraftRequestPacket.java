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

import java.util.List;

/**
 * CraftRequestPacket — Client → Server: das Terminal schickt eine Liste von Crafting-Aufträgen
 * ({@code protos[i]} × {@code amounts[i]}) auf einmal los. Der Server sucht einen über das Netz
 * (multi-hop) erreichbaren, laufenden {@link ComputerBlockEntity} und hängt alle Aufträge an seine
 * Warteschlange an – sie werden dort nacheinander abgearbeitet.
 */
public record CraftRequestPacket(BlockPos terminalPos, List<ItemStack> protos, List<Integer> amounts)
        implements CustomPacketPayload {

    public static final Type<CraftRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "craft_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftRequestPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CraftRequestPacket::terminalPos,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), CraftRequestPacket::protos,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), CraftRequestPacket::amounts,
            CraftRequestPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CraftRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = packet.terminalPos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            int n = Math.min(packet.protos().size(), packet.amounts().size());
            if (n <= 0) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof TerminalBlockEntity terminal)) return;

            // Ersten erreichbaren, laufenden Computer wählen (alle Aufträge gehen an denselben,
            // damit die Reihenfolge erhalten bleibt).
            ComputerBlockEntity computer = null;
            for (BlockPos p : RoutingManager.reachableStorages(player.level(), pos, terminal.getNetworkColor())) {
                if (player.level().getBlockEntity(p) instanceof ComputerBlockEntity c && c.isPowered()) {
                    computer = c;
                    break;
                }
            }
            if (computer == null) {
                player.displayClientMessage(Component.translatable("message.frogportnetworks.no_computer"), true);
                return;
            }

            int queued = 0;
            for (int i = 0; i < n; i++) {
                ItemStack proto = packet.protos().get(i);
                int amount = packet.amounts().get(i);
                if (proto.isEmpty() || amount <= 0) continue;
                computer.requestCraft(proto, amount);
                queued++;
            }
            if (queued == 1) {
                player.displayClientMessage(Component.translatable("message.frogportnetworks.craft_started",
                        packet.amounts().get(0), packet.protos().get(0).getHoverName()), true);
            } else if (queued > 1) {
                player.displayClientMessage(Component.translatable("message.frogportnetworks.craft_queued", queued), true);
            }
        });
    }
}
