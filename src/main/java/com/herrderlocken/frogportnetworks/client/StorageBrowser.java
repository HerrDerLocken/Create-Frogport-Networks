package com.herrderlocken.frogportnetworks.client;

import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * StorageBrowser — wiederverwendbares, scrollbares Item-Raster für die Storage-GUIs
 * (NAS und Terminal). Rendert Item-Icons + Mengen aus einer Eintragsliste und
 * liefert Treffer-/Scroll-Logik. Die Slots sind virtuell (keine Vanilla-Slots).
 */
public class StorageBrowser {

    public static final int SLOT = 18;

    private static final int SLOT_FILL   = 0xFF373245;
    private static final int SLOT_BORDER = 0xFF8A86A0;
    private static final int TRACK       = 0x40FFFFFF;
    private static final int KNOB        = 0xFFD8D6E4;
    private static final int COUNT_COLOR = 0xFFFFFFFF;

    public final int cols;
    public final int rows;
    private List<DiskEntry> entries = List.of();
    private int scroll;

    public StorageBrowser(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
    }

    public void setEntries(List<DiskEntry> entries) {
        this.entries = entries;
        clampScroll();
    }

    public int maxScroll() {
        int r = (entries.size() + cols - 1) / cols;
        return Math.max(0, r - rows);
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
        int index = (scroll + row) * cols + col;
        return index < entries.size() ? entries.get(index) : null;
    }

    public boolean mouseScrolled(int ox, int oy, double mx, double my, double dy) {
        if (!inside(ox, oy, mx, my)) return false;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(dy), maxScroll()));
        return true;
    }

    public void render(GuiGraphics g, Font font, int ox, int oy) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int cx = ox + col * SLOT;
                int cy = oy + row * SLOT;
                g.fill(cx, cy, cx + SLOT, cy + SLOT, SLOT_FILL);
                g.renderOutline(cx, cy, SLOT, SLOT, SLOT_BORDER);
                int index = (scroll + row) * cols + col;
                if (index < entries.size()) {
                    DiskEntry e = entries.get(index);
                    g.renderItem(e.item(), cx + 1, cy + 1);
                    drawCount(g, font, e.count(), cx + 1, cy + 1);
                }
            }
        }
        if (maxScroll() > 0) {
            int trackX = ox + cols * SLOT + 1;
            int trackH = rows * SLOT;
            g.fill(trackX, oy, trackX + 2, oy + trackH, TRACK);
            int knobH = Math.max(6, trackH * rows / (maxScroll() + rows));
            int knobY = oy + (trackH - knobH) * scroll / maxScroll();
            g.fill(trackX, knobY, trackX + 2, knobY + knobH, KNOB);
        }
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
