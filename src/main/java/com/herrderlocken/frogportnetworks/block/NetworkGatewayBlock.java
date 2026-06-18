package com.herrderlocken.frogportnetworks.block;

import com.herrderlocken.frogportnetworks.network.RoutingManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * NetworkGatewayBlock — koppelt die (Farb-)Netze aller an ihm anliegenden Kabel.
 * Passiver Relay ohne BlockEntity: das {@link RoutingManager}-Multi-Hop-Routing darf
 * nur an einem Gateway die Kabelfarbe wechseln. Rechtsklick zeigt die gekoppelten Farben.
 */
public class NetworkGatewayBlock extends Block {

    public static final MapCodec<NetworkGatewayBlock> CODEC = simpleCodec(NetworkGatewayBlock::new);

    public NetworkGatewayBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state, Level level,
                                               BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            Set<DyeColor> colors = RoutingManager.adjacentCableColors(level, pos);
            String list = colors.isEmpty() ? "—"
                    : colors.stream().map(DyeColor::getName).collect(Collectors.joining(", "));
            player.displayClientMessage(
                    Component.translatable("message.frogportnetworks.gateway_links", list), true);
        }
        return InteractionResult.SUCCESS;
    }
}
