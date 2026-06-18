package com.herrderlocken.frogportnetworks.network;

import com.herrderlocken.frogportnetworks.Config;
import com.herrderlocken.frogportnetworks.block.NetworkGatewayBlock;
import com.herrderlocken.frogportnetworks.blockentity.NetworkCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RoutingManager — physisches Multi-Hop-Routing über Kabel.
 *
 * Ein einzelnes (Farb-)Netz ist über {@link NetworkManager#discoverOnColor} erreichbar.
 * Ein {@link NetworkGatewayBlock} koppelt die Netze ALLER an ihm anliegenden Kabelfarben:
 * Die Suche darf nur an einem Gateway die Farbe wechseln. So bleiben Subnetze getrennt,
 * außer sie sind physisch über ein Gateway verbunden — dann reicht die Erreichbarkeit
 * über mehrere Hops (Gateway → Gateway → …).
 */
public final class RoutingManager {

    private RoutingManager() {}

    /** Farben aller direkt an {@code pos} anliegenden Kabel. */
    public static Set<DyeColor> adjacentCableColors(Level level, BlockPos pos) {
        EnumSet<DyeColor> colors = EnumSet.noneOf(DyeColor.class);
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(pos.relative(d)) instanceof NetworkCableBlockEntity be) {
                colors.addAll(be.getColors());
            }
        }
        return colors;
    }

    /**
     * Alle von {@code start} aus erreichbaren Geräte (ohne Gateways selbst), wobei die Suche
     * auf {@code startColors} beginnt und nur an Gateways auf deren weitere Farben überspringt.
     */
    public static Set<BlockPos> reachableStorages(Level level, BlockPos start, Iterable<DyeColor> startColors) {
        int maxHops = Config.CABLE_MAX_LENGTH.getAsInt();
        Set<BlockPos> result = new HashSet<>();
        Set<BlockPos> visitedGateways = new HashSet<>();
        visitedGateways.add(start);

        Deque<Frontier> queue = new ArrayDeque<>();
        queue.add(new Frontier(start, startColors));

        while (!queue.isEmpty()) {
            Frontier f = queue.poll();
            for (DyeColor color : f.colors()) {
                for (BlockPos dev : NetworkManager.discoverOnColor(level, f.pos(), color, maxHops)) {
                    if (level.getBlockState(dev).getBlock() instanceof NetworkGatewayBlock) {
                        if (visitedGateways.add(dev)) {
                            queue.add(new Frontier(dev, adjacentCableColors(level, dev)));
                        }
                    } else {
                        result.add(dev);
                    }
                }
            }
        }
        result.remove(start);
        return result;
    }

    /** Bequeme Variante mit einer Startfarbe. */
    public static Set<BlockPos> reachableStorages(Level level, BlockPos start, DyeColor startColor) {
        return reachableStorages(level, start, List.of(startColor));
    }

    private record Frontier(BlockPos pos, Iterable<DyeColor> colors) {}
}
