package com.herrderlocken.frogportnetworks.network;

import com.herrderlocken.frogportnetworks.block.ComputerBlock;
import com.herrderlocken.frogportnetworks.block.NASBlock;
import com.herrderlocken.frogportnetworks.block.NetworkCableBlock;
import com.herrderlocken.frogportnetworks.block.NetworkGatewayBlock;
import com.herrderlocken.frogportnetworks.block.NetworkPortBlock;
import com.herrderlocken.frogportnetworks.block.RouterBlock;
import com.herrderlocken.frogportnetworks.block.TerminalBlock;
import com.herrderlocken.frogportnetworks.blockentity.NetworkCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.*;

/**
 * NetworkManager — globale Verwaltung aller Netzwerkgeräte.
 *
 * Aktuell ein einfacher statischer Manager. Funktioniert solange der Server läuft.
 *
 * TODO (Phase 2): Auf SavedData umstellen, damit das Netzwerk über Server-Neustarts
 * hinweg persistiert wird. SavedData ist Minecraft's Weg um custom Daten
 * pro World zu speichern (wie ein globaler NBT-Speicher).
 *
 * Kernkonzepte:
 * - deviceRegistry: Welche IP hat welcher Block? (BlockPos → IPAddress)
 * - discoverNetwork(): BFS-Suche über Kabel um alle verbundenen Geräte zu finden
 */
public class NetworkManager {

    // Bidirektionale Zuordnung: Position ↔ IP
    private static final Map<BlockPos, IPAddress> posToIp = new HashMap<>();
    private static final Map<IPAddress, BlockPos> ipToPos = new HashMap<>();

    // === Geräte registrieren / entfernen ===

    public static void registerDevice(BlockPos pos, IPAddress ip) {
        // Alte Zuordnung aufräumen falls der Block vorher eine andere IP hatte
        IPAddress oldIp = posToIp.get(pos);
        if (oldIp != null) ipToPos.remove(oldIp);

        posToIp.put(pos, ip);
        ipToPos.put(ip, pos);
    }

    public static void unregisterDevice(BlockPos pos) {
        IPAddress ip = posToIp.remove(pos);
        if (ip != null) ipToPos.remove(ip);
    }

    public static IPAddress getIP(BlockPos pos) {
        return posToIp.get(pos);
    }

    public static BlockPos getPosition(IPAddress ip) {
        return ipToPos.get(ip);
    }

    public static boolean isIPTaken(IPAddress ip) {
        return ipToPos.containsKey(ip);
    }

    /** Alle aktuell registrierten Geräte-Positionen (für die globale Übersicht). */
    public static Set<BlockPos> allDevicePositions() {
        return new HashSet<>(posToIp.keySet());
    }

    /**
     * Entdeckt alle Netzwerkgeräte die von einer Position aus über Kabel erreichbar sind.
     *
     * Verwendet BFS (Breadth-First Search) — der Algorithmus den du aus der Vorlesung kennst!
     * Startet an 'startPos' und folgt Kabelblöcken in alle 6 Richtungen.
     * Sammelt dabei alle Nicht-Kabel Netzwerkgeräte (Router, NAS, Terminal).
     *
     * maxDepth begrenzt die Suchtiefe → entspricht der maximalen Kabellänge.
     *
     * @param level     Die Minecraft-Welt
     * @param startPos  Startposition der Suche
     * @param maxDepth  Maximale Kabel-Hops
     * @return Set aller erreichbaren Geräte-Positionen (ohne Kabel selbst)
     */
    public static Set<BlockPos> discoverNetwork(Level level, BlockPos startPos, int maxDepth) {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> devices = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        // Depth-Tracking: speichert die aktuelle Tiefe pro Position
        Map<BlockPos, Integer> depth = new HashMap<>();

        queue.add(startPos);
        visited.add(startPos);
        depth.put(startPos, 0);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int currentDepth = depth.get(current);

            if (currentDepth > maxDepth) continue;

            // Ist der aktuelle Block ein Netzwerkgerät (kein Kabel)?
            if (NetworkCableBlock.isNetworkDevice(level, current)
                    && !(level.getBlockState(current).getBlock() instanceof NetworkCableBlock)) {
                devices.add(current);
            }

            // Alle 6 Richtungen prüfen (UP, DOWN, NORTH, SOUTH, EAST, WEST)
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                if (!visited.contains(neighbor) && NetworkCableBlock.isNetworkDevice(level, neighbor)) {
                    visited.add(neighbor);
                    depth.put(neighbor, currentDepth + 1);
                    queue.add(neighbor);
                }
            }
        }

        return devices;
    }

    /**
     * Farb-bewusste Discovery: findet alle Netzgeräte, die von 'start' aus über
     * Kabel EINER bestimmten Farbe erreichbar sind.
     *
     * Anders als {@link #discoverNetwork} folgt diese Suche nur Kabel-Segmenten der
     * gegebenen Farbe — so sind unterschiedlich gefärbte Netze logisch getrennt.
     * Startpunkt ist ein Gerät (z.B. Terminal); die Suche beginnt an dessen
     * angrenzenden farbgleichen Kabeln.
     *
     * @return Positionen aller erreichbaren Geräte (Router/NAS/Terminal), ohne 'start'.
     */
    public static Set<BlockPos> discoverOnColor(Level level, BlockPos start, DyeColor color, int maxDepth) {
        Set<BlockPos> devices = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Map<BlockPos, Integer> depth = new HashMap<>();

        // Start: angrenzende Kabel der passenden Farbe
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = start.relative(dir);
            if (isColorCable(level, neighbor, color)) {
                visited.add(neighbor);
                depth.put(neighbor, 1);
                queue.add(neighbor);
            }
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (depth.get(current) > maxDepth) continue;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (isColorCable(level, neighbor, color)) {
                    if (visited.add(neighbor)) {
                        depth.put(neighbor, depth.get(current) + 1);
                        queue.add(neighbor);
                    }
                } else if (!neighbor.equals(start) && isDevice(level, neighbor)) {
                    devices.add(neighbor);
                }
            }
        }
        return devices;
    }

    private static boolean isColorCable(Level level, BlockPos pos, DyeColor color) {
        return level.getBlockState(pos).getBlock() instanceof NetworkCableBlock
                && level.getBlockEntity(pos) instanceof NetworkCableBlockEntity be
                && be.hasSegment(color);
    }

    private static boolean isDevice(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof RouterBlock || block instanceof NASBlock || block instanceof TerminalBlock
                || block instanceof NetworkPortBlock || block instanceof NetworkGatewayBlock
                || block instanceof ComputerBlock;
    }

    /** Räumt alle Registrierungen auf (z.B. beim Server-Stop). */
    public static void clear() {
        posToIp.clear();
        ipToPos.clear();
    }
}
