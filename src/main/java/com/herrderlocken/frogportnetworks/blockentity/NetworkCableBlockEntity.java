package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.block.NASBlock;
import com.herrderlocken.frogportnetworks.block.NetworkCableBlock;
import com.herrderlocken.frogportnetworks.block.RouterBlock;
import com.herrderlocken.frogportnetworks.block.TerminalBlock;
import com.herrderlocken.frogportnetworks.network.CableType;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NetworkCableBlockEntity — speichert die Kabel-Segmente eines Blocks.
 *
 * Jedes Segment (je {@link DyeColor} genau eines, max. 4) liegt flach auf einer
 * Befestigungsfläche und hat einen Typ. Standard-Kabel bekommen beim Platzieren
 * automatisch die nächste freie Farbe; gefärbt wird später in der Welt. Verbunden
 * wird tangential entlang der Fläche zu gleichfarbigen Nachbarn (+ um Ecken) und
 * zu Netzgeräten.
 */
public class NetworkCableBlockEntity extends BlockEntity {

    public static final int MAX_SEGMENTS = 4;

    /** Ein Segment: Kabeltyp + Fläche, auf der es klebt. (Die Bahn ergibt sich aus der Farbe.) */
    public record Segment(CableType type, Direction attachFace) {}

    // --- Geometrie-Konstanten (Pixel 0..16) ---
    public static final float THICK = 2.0f;
    public static final float HALF = 1.0f;
    public static final float BASE_GAP = 0.2f;

    /**
     * Feste (u,v)-Position je Farb-Bahn (color.id % 4). Weiß (Standard) liegt mittig;
     * weitere Farben verteilen sich zu den Seiten. Da die Bahn nur von der Farbe abhängt,
     * liegt eine Farbe in JEDEM Block gleich → durchgängige Stränge ohne Versatz.
     */
    public static final float[] LANE_POS = {0.0f, -2.5f, 2.5f, -5.0f};

    private static final VoxelShape FALLBACK = Block.box(6.5, 0, 6.5, 9.5, 2.5, 9.5);

    private final EnumMap<DyeColor, Segment> segments = new EnumMap<>(DyeColor.class);

    public NetworkCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state);
    }

    // === Segment-Verwaltung ===

    public boolean hasSegment(DyeColor color) {
        return segments.containsKey(color);
    }

    public CableType getType(DyeColor color) {
        Segment s = segments.get(color);
        return s == null ? null : s.type();
    }

    public Direction getAttachFace(DyeColor color) {
        Segment s = segments.get(color);
        return s == null ? Direction.DOWN : s.attachFace();
    }

    public Set<DyeColor> getColors() {
        return segments.keySet();
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public Direction getCommonAttachFace() {
        for (Segment s : segments.values()) return s.attachFace();
        return Direction.DOWN;
    }

    /** Nächste freie Farbe (Reihenfolge der 16 Dyes), oder null wenn der Block voll ist. */
    public DyeColor nextFreeColor() {
        if (segments.size() >= MAX_SEGMENTS) return null;
        for (DyeColor c : DyeColor.values()) {
            if (!segments.containsKey(c)) return c;
        }
        return null;
    }

    /**
     * Bahn-Index (0..3) einer Farbe IN DIESEM BLOCK.
     *
     * Bevorzugt wird die feste Bahn {@code id % 4} (damit eine Farbe in jedem Block
     * auf derselben Bahn liegt → durchgängige Stränge). Würden zwei Stränge dieselbe
     * Bahn belegen, weicht der mit der höheren Id deterministisch auf die nächste freie
     * Bahn aus. So überlagern sich nie zwei Stränge (kein "verschwundener" Strang), und
     * die niedrigste Id (z.B. Weiß) behält immer ihre Wunschbahn.
     */
    public int getLane(DyeColor color) {
        int n = LANE_POS.length;
        boolean[] used = new boolean[n];
        for (DyeColor c : segments.keySet()) { // EnumMap iteriert in Id-Reihenfolge
            int lane = c.getId() % n;
            if (used[lane]) {
                for (int i = 0; i < n; i++) {
                    if (!used[i]) { lane = i; break; }
                }
            }
            used[lane] = true;
            if (c == color) return lane;
        }
        return color.getId() % n; // Farbe nicht vorhanden → Wunschbahn
    }

    /**
     * Versatz (u,v) eines Strangs — fest pro Farbe. Dadurch liegt eine Farbe in JEDEM
     * Block auf derselben Bahn → durchgängige Stränge ohne Versatz an Knicken/Wänden,
     * unabhängig davon, wie viele andere Stränge im Block sind.
     */
    public float[] laneOffset(DyeColor color) {
        float off = LANE_POS[getLane(color)];
        return new float[]{off, off};
    }

    public boolean addSegment(DyeColor color, CableType type, Direction attachFace) {
        if (segments.containsKey(color) || segments.size() >= MAX_SEGMENTS) return false;
        segments.put(color, new Segment(type, attachFace));
        onSegmentsChanged();
        return true;
    }

    public CableType removeSegment(DyeColor color) {
        Segment removed = segments.remove(color);
        if (removed != null) onSegmentsChanged();
        return removed == null ? null : removed.type();
    }

    /** Färbt ein Segment um (für das Färben ganzer Strecken). */
    public boolean recolor(DyeColor from, DyeColor to) {
        if (from == to) return false;
        Segment s = segments.get(from);
        if (s == null || segments.containsKey(to)) return false; // Kollision vermeiden
        segments.remove(from);
        segments.put(to, s);
        onSegmentsChanged();
        return true;
    }

    private void onSegmentsChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // === Verbindungen ===

    public EnumSet<Direction> getConnections(DyeColor color) {
        EnumSet<Direction> result = EnumSet.noneOf(Direction.class);
        Segment seg = segments.get(color);
        if (seg == null || level == null) return result;

        Direction attach = seg.attachFace();
        for (Direction dir : Direction.values()) {
            if (dir.getAxis() == attach.getAxis()) continue; // nur tangential

            BlockPos neighborPos = worldPosition.relative(dir);
            BlockState neighbor = level.getBlockState(neighborPos);

            if (neighbor.getBlock() instanceof NetworkCableBlock) {
                if (level.getBlockEntity(neighborPos) instanceof NetworkCableBlockEntity nbe
                        && nbe.segments.containsKey(color)) {
                    result.add(dir);
                }
            } else if (isDevice(neighbor)) {
                result.add(dir);
            }
        }

        // Eck-Verbindung (z.B. Boden → Wand)
        BlockPos openPos = worldPosition.relative(attach.getOpposite());
        if (level.getBlockEntity(openPos) instanceof NetworkCableBlockEntity obe) {
            Segment os = obe.segments.get(color);
            if (os != null && os.attachFace().getAxis() != attach.getAxis()) {
                result.add(os.attachFace());
            }
        }
        return result;
    }

    private static boolean isDevice(BlockState state) {
        Block block = state.getBlock();
        return block instanceof RouterBlock
                || block instanceof NASBlock
                || block instanceof TerminalBlock;
    }

    // === Geometrie (geteilt von Shape & Renderer) ===

    public static float[] normalRange(Direction attachFace, float gap, float thick) {
        if (attachFace.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            return new float[]{gap, gap + thick};
        }
        return new float[]{16 - gap - thick, 16 - gap};
    }

    public static List<float[]> collectBoxes(Direction attachFace, EnumSet<Direction> connections,
                                             float offU, float offV, float half, float n0, float n1) {
        List<float[]> boxes = new ArrayList<>();
        Direction.Axis nAxis = attachFace.getAxis();

        Direction.Axis uAxis = null, vAxis = null;
        for (Direction.Axis a : Direction.Axis.values()) {
            if (a == nAxis) continue;
            if (uAxis == null) uAxis = a; else vAxis = a;
        }

        float cu = 8 + offU;
        float cv = 8 + offV;

        boxes.add(makeBox(nAxis, uAxis, vAxis, n0, n1, cu - half, cu + half, cv - half, cv + half));

        for (Direction d : connections) {
            boolean positive = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            if (d.getAxis() == uAxis) {
                boxes.add(makeBox(nAxis, uAxis, vAxis, n0, n1, positive ? cu : 0, positive ? 16 : cu, cv - half, cv + half));
            } else if (d.getAxis() == vAxis) {
                boxes.add(makeBox(nAxis, uAxis, vAxis, n0, n1, cu - half, cu + half, positive ? cv : 0, positive ? 16 : cv));
            }
        }
        return boxes;
    }

    private static float[] makeBox(Direction.Axis nAxis, Direction.Axis uAxis, Direction.Axis vAxis,
                                   float n0, float n1, float u0, float u1, float v0, float v1) {
        float[] lo = new float[3];
        float[] hi = new float[3];
        lo[nAxis.ordinal()] = n0; hi[nAxis.ordinal()] = n1;
        lo[uAxis.ordinal()] = u0; hi[uAxis.ordinal()] = u1;
        lo[vAxis.ordinal()] = v0; hi[vAxis.ordinal()] = v1;
        return new float[]{lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]};
    }

    // === Box-Aufbau (Kern + Arme + Eck-Riser), geteilt von Shape & Renderer ===

    /**
     * Alle Boxen eines Strangs in Pixel-Koordinaten. {@code nExpand} weitet die Dicke
     * leicht (für die Farb-Linie, damit sie obenauf liegt).
     */
    public List<float[]> buildSegmentBoxes(DyeColor color, float half, float nExpand) {
        Segment seg = segments.get(color);
        if (seg == null) return new ArrayList<>();
        Direction attach = seg.attachFace();
        float[] off = laneOffset(color);
        float[] nr = normalRange(attach, BASE_GAP, THICK);
        float n0 = nr[0] - nExpand, n1 = nr[1] + nExpand;

        List<float[]> boxes = collectBoxes(attach, getConnections(color), off[0], off[1], half, n0, n1);

        // Eck-Riser: geht ein gleichfarbiges Kabel auf der "offenen" Seite quer weg
        // (Boden → Wand), füllen wir das Steigstück im Anschlussblock, damit kein
        // sichtbarer Sprung entsteht.
        if (level != null) {
            BlockPos openPos = worldPosition.relative(attach.getOpposite());
            if (level.getBlockEntity(openPos) instanceof NetworkCableBlockEntity obe) {
                Segment os = obe.segments.get(color);
                if (os != null && os.attachFace().getAxis() != attach.getAxis()) {
                    boxes.add(riserBox(attach, os.attachFace(), off[0], half, n0, n1));
                }
            }
        }
        return boxes;
    }

    /** Vertikales Steigstück zur Ecke hin (entlang der Normalachse, weg von der Fläche). */
    private static float[] riserBox(Direction attach, Direction cornerFace, float off, float half, float n0, float n1) {
        Direction.Axis nAxis = attach.getAxis();
        Direction.Axis cAxis = cornerFace.getAxis();
        Direction.Axis rAxis = Direction.Axis.X;
        for (Direction.Axis a : Direction.Axis.values()) {
            if (a != nAxis && a != cAxis) rAxis = a;
        }
        float[] lo = new float[3];
        float[] hi = new float[3];
        if (attach.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            lo[nAxis.ordinal()] = n1; hi[nAxis.ordinal()] = 16;
        } else {
            lo[nAxis.ordinal()] = 0; hi[nAxis.ordinal()] = n0;
        }
        // Bündig an die Eck-Fläche (gleiche Schicht wie das anschließende Wandkabel).
        float[] cr = normalRange(cornerFace, BASE_GAP, THICK);
        lo[cAxis.ordinal()] = cr[0]; hi[cAxis.ordinal()] = cr[1];
        float center = 8 + off;
        lo[rAxis.ordinal()] = center - half; hi[rAxis.ordinal()] = center + half;
        return new float[]{lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]};
    }

    // === Shape ===

    /** Form eines einzelnen Strangs (für Sub-Hit-Erkennung beim Abbauen/Färben). */
    public VoxelShape getColorShape(DyeColor color) {
        if (!segments.containsKey(color)) return Shapes.empty();
        VoxelShape shape = Shapes.empty();
        for (float[] b : buildSegmentBoxes(color, HALF, 0f)) {
            shape = Shapes.or(shape, Shapes.box(
                    b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16));
        }
        return shape;
    }

    public VoxelShape getShape() {
        if (segments.isEmpty()) return FALLBACK;
        VoxelShape shape = Shapes.empty();
        for (DyeColor color : segments.keySet()) {
            shape = Shapes.or(shape, getColorShape(color));
        }
        return shape;
    }

    // === Persistenz ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag seg = new CompoundTag();
        for (Map.Entry<DyeColor, Segment> e : segments.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putString("type", e.getValue().type().getSerializedName());
            s.putString("face", e.getValue().attachFace().getName());
            seg.put(e.getKey().getSerializedName(), s);
        }
        tag.put("segments", seg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        segments.clear();
        CompoundTag seg = tag.getCompound("segments");
        for (String key : seg.getAllKeys()) {
            CompoundTag s = seg.getCompound(key);
            Direction face = Direction.byName(s.getString("face"));
            segments.put(DyeColor.byName(key, DyeColor.WHITE),
                    new Segment(CableType.byName(s.getString("type")),
                            face == null ? Direction.DOWN : face));
        }
    }

    // === Client-Sync (Rendering) ===

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) loadAdditional(tag, registries);
    }
}
