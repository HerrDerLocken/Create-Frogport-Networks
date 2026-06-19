package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.ComputerBlockEntity;
import com.herrderlocken.frogportnetworks.blockentity.TerminalBlockEntity;
import com.herrderlocken.frogportnetworks.craft.CraftEngine;
import com.herrderlocken.frogportnetworks.item.ComputerUpgrade;
import com.herrderlocken.frogportnetworks.network.RoutingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * RequestCraftPlanPacket — Client → Server: das Terminal will wissen, was das Craften von
 * {@code proto} verbrauchen/herstellen würde (für den Hover-Tooltip). Wird über den erreichbaren
 * Computer (dessen Upgrades) berechnet und als {@link CraftPlanPacket} zurückgeschickt.
 */
public record RequestCraftPlanPacket(BlockPos terminalPos, ItemStack proto) implements CustomPacketPayload {

    public static final Type<RequestCraftPlanPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "request_craft_plan"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCraftPlanPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestCraftPlanPacket::terminalPos,
            ItemStack.STREAM_CODEC, RequestCraftPlanPacket::proto,
            RequestCraftPlanPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestCraftPlanPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = packet.terminalPos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            if (packet.proto().isEmpty()) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof TerminalBlockEntity terminal)) return;

            ComputerBlockEntity computer = null;
            for (BlockPos p : RoutingManager.reachableStorages(player.level(), pos, terminal.getNetworkColor())) {
                if (player.level().getBlockEntity(p) instanceof ComputerBlockEntity c) { computer = c; break; }
            }
            CraftEngine.Plan plan;
            if (computer == null) {
                plan = new CraftEngine.Plan(false, java.util.List.of(), java.util.List.of(), java.util.List.of(), 0);
            } else {
                plan = CraftEngine.planConsumption(player.level(), terminal, packet.proto(),
                        computer.enabledProcesses(), computer.hasUpgrade(ComputerUpgrade.AI));
            }
            ModNetworking.sendCraftPlan(player, pos, packet.proto(), plan);
        });
    }
}
