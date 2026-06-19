package com.herrderlocken.frogportnetworks.client;

import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * StorageBrowser — wiederverwendbares, scrollbares Item-Raster für die Storage-GUIs
 * (NAS und Terminal). Rendert Item-Icons + Mengen aus einer Eintragsliste und
 * liefert Treffer-/Scroll-Logik. Die Slots sind virtuell (keine Vanilla-Slots).
 *
 * <p>Zwei Modi: flach ({@link #setEntries}) oder gruppiert mit Abschnitts-Überschriften
 * ({@link #setGroups}). Im gruppierten Modus belegt jede Überschrift eine eigene Zeile.
 */
public class StorageBrowser {

    public static final int SLOT = 18;

    private static final int SLOT_FILL   = 0xFF373245;
    private static final int SLOT_BORDER = 0xFF8A86A0;
    private static final int TRACK       = 0x40FFFFFF;
    private static final int KNOB        = 0xFFD8D6E4;
    private static final int COUNT_COLOR = 0xFFFFFFFF;
    private static final int HEADER_BG    = 0xFF2A2733;
    private static final int HEADER_TEXT  = 0xFFFFD79A;
    /** Hintergrund für craftbare (count==0) Einträge im Terminal-Index. */
    private static final int CRAFTABLE_BG = 0x553A6CC8;

    /** Eine Gruppe mit Überschrift und ihren Einträgen (für den gruppierten Modus). */
    public record Group(String label, List<DiskEntry> items) {}

    /** Eine sichtbare Zeile: entweder eine Überschrift (header != null) oder eine Item-Zeile. */
    private record Line(String header, List<DiskEntry> items) {}

    public final int cols;
    public final int rows;
    private boolean grouped;
    private List<DiskEntry> entries = List.of();
    private final List<Line> lines = new ArrayList<>();
    private int scroll;

    public StorageBrowser(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
    }

    public void setEntries(List<DiskEntry> entries) {
        this.grouped = false;
        this.entries = entries;
        clampScroll();
    }

    public void setGroups(List<Group> groups) {
        this.grouped = true;
        lines.clear();
        for (Group gr : groups) {
            if (gr.items().isEmpty()) continue;
            lines.add(new Line(gr.label(), null));
            for (int i = 0; i < gr.items().size(); i += cols) {
                lines.add(new Line(null, gr.items().subList(i, Math.min(i + cols, gr.items().size()))));
            }
        }
        clampScroll();
    }

    private int totalRows() {
        return grouped ? lines.size() : (entries.size() + cols - 1) / cols;
    }

    public int maxScroll() {
        return Math.max(0, totalRows() - rows);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    public boolean inside(int ox, int oy, double mx, double my) {
        return mx >= ox && mx < ox + cols * SLOT && my >= oy && my < oy + rows * SLOT;
    }

    public DiskEntry entryAt(int ox, int oy, double mx, double my) {
        if (!inside(ox, oy, mx, my)) return null;
        int col = (int) ((mx - ox) / SLOT);
        int row = (int) ((my - oy) / SLOT);
        if (grouped) {
            int idx = scroll + row;
            if (idx >= lines.size()) return null;
            Line line = lines.get(idx);
            if (line.header() != null || line.items() == null) return null;
            return col < line.items().size() ? line.items().get(col) : null;
        }
        int index = (scroll + row) * cols + col;
        return index < entries.size() ? entries.get(index) : null;
    }

    public boolean mouseScrolled(int ox, int oy, double mx, double my, double dy) {
        if (!inside(ox, oy, mx, my)) return false;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(dy), maxScroll()));
        return true;
    }

    public void render(GuiGraphics g, Font font, int ox, int oy) {
        if (grouped) renderGrouped(g, font, ox, oy);
        else renderFlat(g, font, ox, oy);
        renderScrollbar(g, ox, oy);
    }

    private void renderFlat(GuiGraphics g, Font font, int ox, int oy) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int cx = ox + col * SLOT;
                int cy = oy + row * SLOT;
                int index = (scroll + row) * cols + col;
                drawCell(g, font, cx, cy, index < entries.size() ? entries.get(index) : null);
            }
        }
    }

    private void renderGrouped(GuiGraphics g, Font font, int ox, int oy) {
        for (int row = 0; row < rows; row++) {
            int idx = scroll + row;
            int cy = oy + row * SLOT;
            if (idx >= lines.size()) {
                for (int col = 0; col < cols; col++) drawCell(g, font, ox + col * SLOT, cy, null);
                continue;
            }
            Line line = lines.get(idx);
            if (line.header() != null) {
                g.fill(ox, cy, ox + cols * SLOT, cy + SLOT, HEADER_BG);
                g.drawString(font, line.header(), ox + 3, cy + (SLOT - 8) / 2, HEADER_TEXT, false);
            } else {
                for (int col = 0; col < cols; col++) {
                    DiskEntry e = (line.items() != null && col < line.items().size()) ? line.items().get(col) : null;
                    drawCell(g, font, ox + col * SLOT, cy, e);
                }
            }
        }
    }

    private void drawCell(GuiGraphics g, Font font, int cx, int cy, DiskEntry e) {
        g.fill(cx, cy, cx + SLOT, cy + SLOT, SLOT_FILL);
        g.renderOutline(cx, cy, SLOT, SLOT, SLOT_BORDER);
        if (e == null) return;
        if (e.count() <= 0) {
            g.fill(cx + 1, cy + 1, cx + SLOT - 1, cy + SLOT - 1, CRAFTABLE_BG); // craftbar
        }
        g.renderItem(e.item(), cx + 1, cy + 1);
        if (e.count() > 0) drawCount(g, font, e.count(), cx + 1, cy + 1);
    }

    private void renderScrollbar(GuiGraphics g, int ox, int oy) {
        if (maxScroll() <= 0) return;
        int trackX = ox + cols * SLOT + 1;
        int trackH = rows * SLOT;
        g.fill(trackX, oy, trackX + 2, oy + trackH, TRACK);
        int knobH = Math.max(6, trackH * rows / (maxScroll() + rows));
        int knobY = oy + (trackH - knobH) * scroll / maxScroll();
        g.fill(trackX, knobY, trackX + 2, knobY + knobH, KNOB);
    }

    private void drawCount(GuiGraphics g, Font font, long count, int x, int y) {
        String s = fmt(count);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        float scale = 0.75f;
        g.pose().scale(scale, scale, 1);
        int tx = (int) ((x + 17 - font.width(s) * scale) / scale);
        int ty = (int) ((y + 17 - 6 * scale) / scale);
        g.drawString(font, s, tx, ty, COUNT_COLOR, true);
        g.pose().popPose();
    }

    public static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fk", n / 1_000.0);
        return Long.toString(n);
    }
}
