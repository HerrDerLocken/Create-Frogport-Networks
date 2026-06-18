package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * WithdrawItemPacket — Client → Server: entnimm bis zu {@code amount} des Prototyps
 * aus dem Speicher und gib sie dem Spieler (Inventar oder gedroppt).
 */
public record WithdrawItemPacket(BlockPos pos, ItemStack proto, int amount) implements CustomPacketPayload {

    public static final Type<WithdrawItemPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "withdraw_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawItemPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WithdrawItemPacket::pos,
            ItemStack.STREAM_CODEC, WithdrawItemPacket::proto,
            ByteBufCodecs.VAR_INT, WithdrawItemPacket::amount,
            WithdrawItemPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WithdrawItemPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = packet.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            if (packet.proto().isEmpty() || packet.amount() <= 0) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof NetworkStorage storage)) return;

            ItemStack proto = packet.proto();
            int want = Math.min(packet.amount(), proto.getMaxStackSize() * 64); // Sicherheitsdeckel
            long extracted = storage.extract(proto, want, false);
            if (extracted <= 0) return;

            int max = proto.getMaxStackSize();
            long remaining = extracted;
            while (remaining > 0) {
                int n = (int) Math.min(remaining, max);
                ItemStack give = proto.copyWithCount(n);
                if (!player.getInventory().add(give)) {
                    player.drop(give, false);
                }
                remaining -= n;
            }
            ModNetworking.sendSnapshot(player, pos, storage.snapshot());
        });
    }
}
