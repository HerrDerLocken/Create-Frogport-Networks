package com.herrderlocken.frogportnetworks.net;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.client.ClientStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * CraftPlanPacket — Server → Client: Aufschlüsselung eines Crafts ({@code proto}): ob herstellbar,
 * welche Rohstoffe pro Craft entnommen ({@code consumed}) und welche Vorstufen ({@code crafted})
 * hergestellt werden, was fehlt ({@code missing}) und wie oft der Vorrat reicht ({@code maxCrafts}).
 * Manueller StreamCodec, da 7 Felder die {@code composite}-Maxzahl übersteigen.
 */
public record CraftPlanPacket(BlockPos pos, ItemStack proto, boolean ok,
                              List<ItemStack> consumed, List<ItemStack> crafted,
                              List<ItemStack> missing, int maxCrafts) implements CustomPacketPayload {

    public static final Type<CraftPlanPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "craft_plan"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftPlanPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                BlockPos.STREAM_CODEC.encode(buf, p.pos());
                ItemStack.STREAM_CODEC.encode(buf, p.proto());
                buf.writeBoolean(p.ok());
                writeList(buf, p.consumed());
                writeList(buf, p.crafted());
                writeList(buf, p.missing());
                buf.writeVarInt(p.maxCrafts());
            },
            buf -> new CraftPlanPacket(
                    BlockPos.STREAM_CODEC.decode(buf),
                    ItemStack.STREAM_CODEC.decode(buf),
                    buf.readBoolean(),
                    readList(buf), readList(buf), readList(buf),
                    buf.readVarInt()));

    private static void writeList(RegistryFriendlyByteBuf buf, List<ItemStack> list) {
        buf.writeVarInt(list.size());
        for (ItemStack s : list) ItemStack.STREAM_CODEC.encode(buf, s);
    }

    private static List<ItemStack> readList(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ItemStack> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(ItemStack.STREAM_CODEC.decode(buf));
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CraftPlanPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStorage.applyCraftPlan(packet.pos(), packet.proto(), packet.ok(),
                packet.consumed(), packet.crafted(), packet.missing(), packet.maxCrafts()));
    }
}
