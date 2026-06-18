package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.client.ClientStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * CraftablesPacket — Server → Client: die gerade craftbaren Item-Typen fürs Terminal
 * (Craftbar-Index, je 1 Stück als Prototyp).
 */
public record CraftablesPacket(BlockPos pos, List<ItemStack> items) implements CustomPacketPayload {

    public static final Type<CraftablesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "craftables"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftablesPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CraftablesPacket::pos,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), CraftablesPacket::items,
            CraftablesPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CraftablesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStorage.applyCraftables(packet.pos(), packet.items()));
    }
}
