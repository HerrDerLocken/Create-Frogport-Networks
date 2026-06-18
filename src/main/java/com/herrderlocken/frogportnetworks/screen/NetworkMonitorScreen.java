package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.client.StorageBrowser;
import com.herrderlocken.frogportnetworks.menu.NetworkMonitorMenu;
import com.herrderlocken.frogportnetworks.net.RequestNetworkSnapshotPacket;
import com.herrderlocken.frogportnetworks.storage.DeviceSnapshot;
import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NetworkMonitorScreen — globale Item-Übersicht über ALLE Subnetze. Oben ein Suchfeld,
 * darunter eine Tab-Leiste ("All" + ein Tab pro Subnetz) und das scrollbare Raster
 * ({@link StorageBrowser}). Reine Anzeige — kein Entnehmen (das käme mit Cross-Network-Routing).
 */
public class NetworkMonitorScreen extends AbstractSimiContainerScreen<NetworkMonitorMenu> implements NetworkStorageHost {

    private static final int COLOR_TITLE = 0xFFFFD79A;
    private static final int COLOR_LABEL = 0xFFB8B6C8;
    private static final int COLOR_VALUE = 0xFFEFEFF5;
    private static final int COLOR_DIM   = 0xFF8C8AA0;
    private static final int COLOR_DIVIDER = 0x40FFFFFF;
    private static final int SLOT_FILL   = 0xFF373245;
    private static final int SLOT_BORDER = 0xFF8A86A0;
    private static final int TAB_BG      = 0xFF2A2733;
    private static final int TAB_BG_SEL  = 0xFF4A4757;
    private static final int TAB_BORDER  = 0xFF8A86A0;
    private static final int TAB_H = 14;
    private static final int ARROW_W = 10;

    private int windowH;
    private EditBox search;

    private final StorageBrowser browser = new StorageBrowser(NetworkMonitorMenu.BROWSER_COLS, NetworkMonitorMenu.BROWSER_ROWS);
    private List<DeviceSnapshot> devices = new ArrayList<>();
    private int selectedTab; // 0 = All, sonst Subnetz devices[tab-1]
    private int tabOffset;
    private long capUsed, capMax;
    private int reqTimer;

    private final List<int[]> tabRects = new ArrayList<>();
    private int[] arrowLeft, arrowRight;

    public NetworkMonitorScreen(NetworkMonitorMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        int invY = menu.getInvY();
        int hotbarY = invY + 3 * NetworkMonitorMenu.SLOT + 4;
        windowH = hotbarY + NetworkMonitorMenu.SLOT + 8;
        setWindowSize(NetworkMonitorMenu.WINDOW_W, windowH);
        super.init();

        int x = leftPos;
        int y = topPos;
        search = new EditBox(font, x + NetworkMonitorMenu.GRID_X + 1, y + NetworkMonitorMenu.SEARCH_Y,
                NetworkMonitorMenu.WINDOW_W - 2 * NetworkMonitorMenu.GRID_X, 12, Component.literal("Search"));
        search.setBordered(true);
        search.setHint(Component.literal("Search items…"));
        search.setResponder(s -> refreshBrowser());
        addRenderableWidget(search);

        requestSnapshot();
    }

    private void requestSnapshot() {
        PacketDistributor.sendToServer(new RequestNetworkSnapshotPacket(menu.getBlockPos()));
    }

    @Override
    public BlockPos networkPos() { return menu.getBlockPos(); }

    @Override
    public void onNetworkSnapshot(List<DeviceSnapshot> devices) {
        this.devices = devices;
        if (selectedTab > devices.size()) selectedTab = 0;
        refreshBrowser();
    }

    /** Befüllt den Browser je nach Tab (All = über alle Subnetze) + Suchfilter. */
    private void refreshBrowser() {
        List<DiskEntry> entries = new ArrayList<>();
        capUsed = 0;
        capMax = 0;
        if (selectedTab == 0) {
            for (DeviceSnapshot d : devices) {
                capUsed += d.snapshot().usedItems();
                capMax += d.snapshot().maxItems();
                for (DiskEntry e : d.snapshot().entries()) merge(entries, e);
            }
        } else if (selectedTab - 1 < devices.size()) {
            DeviceSnapshot d = devices.get(selectedTab - 1);
            capUsed = d.snapshot().usedItems();
            capMax = d.snapshot().maxItems();
            entries.addAll(d.snapshot().entries());
        }
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            entries.removeIf(e -> !e.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q));
        }
        browser.setEntries(entries);
    }

    private static void merge(List<DiskEntry> list, DiskEntry e) {
        for (int i = 0; i < list.size(); i++) {
            if (ItemStack.isSameItemSameComponents(list.get(i).item(), e.item())) {
                list.set(i, new DiskEntry(list.get(i).item(), list.get(i).count() + e.count()));
                return;
            }
        }
        list.add(e);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++reqTimer % 10 == 0) requestSnapshot();
    }

    private int browserX() { return leftPos + NetworkMonitorMenu.BROWSER_X; }
    private int browserY() { return topPos + NetworkMonitorMenu.BROWSER_Y; }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        new BoxElement().at(x + 4, y + 4).withBounds(NetworkMonitorMenu.WINDOW_W - 8, windowH - 8).render(g);

        g.drawString(font, "Network Monitor", x + 12, y + 10, COLOR_TITLE, false);
        g.fill(x + 10, y + 22, x + NetworkMonitorMenu.WINDOW_W - 10, y + 23, COLOR_DIVIDER);

        renderTabs(g);

        String cap = StorageBrowser.fmt(capUsed) + " / " + StorageBrowser.fmt(capMax) + " items";
        g.drawString(font, cap, x + NetworkMonitorMenu.WINDOW_W - 12 - font.width(cap),
                y + NetworkMonitorMenu.BROWSER_Y - 10, COLOR_DIM, false);

        for (Slot s : menu.slots) {
            slot(g, x + s.x - 1, y + s.y - 1, NetworkMonitorMenu.SLOT, NetworkMonitorMenu.SLOT);
        }

        browser.render(g, font, browserX(), browserY());
    }

    private void renderTabs(GuiGraphics g) {
        tabRects.clear();
        arrowLeft = null;
        arrowRight = null;

        int y = topPos + NetworkMonitorMenu.TABS_Y;
        int areaX = leftPos + 10;
        int areaR = leftPos + NetworkMonitorMenu.WINDOW_W - 10;

        List<String> labels = new ArrayList<>();
        labels.add("All");
        for (DeviceSnapshot d : devices) labels.add(d.ip());

        if (tabOffset > Math.max(0, labels.size() - 1)) tabOffset = 0;

        int[] widths = new int[labels.size()];
        int total = 0;
        for (int i = 0; i < labels.size(); i++) {
            widths[i] = Math.max(16, font.width(labels.get(i)) + 8);
            total += widths[i] + 2;
        }

        boolean pager = total > (areaR - areaX);
        int cursor = areaX;
        int limitR = areaR;
        if (pager) {
            arrowLeft = new int[]{areaX, y, ARROW_W, TAB_H};
            drawArrow(g, areaX, y, "<", tabOffset > 0);
            cursor = areaX + ARROW_W + 2;
            limitR = areaR - ARROW_W - 2;
            arrowRight = new int[]{areaR - ARROW_W, y, ARROW_W, TAB_H};
        } else {
            tabOffset = 0;
        }

        int lastRendered = -1;
        for (int i = tabOffset; i < labels.size(); i++) {
            int w = widths[i];
            if (cursor + w > limitR) break;
            drawChip(g, cursor, y, w, labels.get(i), i == selectedTab);
            tabRects.add(new int[]{cursor, w, i});
            cursor += w + 2;
            lastRendered = i;
        }
        if (pager) {
            drawArrow(g, areaR - ARROW_W, y, ">", lastRendered < labels.size() - 1);
        }
    }

    private void drawChip(GuiGraphics g, int x, int y, int w, String label, boolean selected) {
        g.fill(x, y, x + w, y + TAB_H, selected ? TAB_BG_SEL : TAB_BG);
        g.renderOutline(x, y, w, TAB_H, TAB_BORDER);
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + 3, selected ? COLOR_VALUE : COLOR_LABEL, false);
    }

    private void drawArrow(GuiGraphics g, int x, int y, String s, boolean enabled) {
        g.fill(x, y, x + ARROW_W, y + TAB_H, TAB_BG);
        g.renderOutline(x, y, ARROW_W, TAB_H, TAB_BORDER);
        g.drawString(font, s, x + 2, y + 3, enabled ? COLOR_VALUE : COLOR_DIM, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        DiskEntry hovered = browser.entryAt(browserX(), browserY(), mouseX, mouseY);
        if (hovered != null) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, hovered.item()));
            lines.add(Component.literal(StorageBrowser.fmt(hovered.count()) + " stored")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (arrowLeft != null && hit(arrowLeft, mx, my)) {
            if (tabOffset > 0) tabOffset--;
            return true;
        }
        if (arrowRight != null && hit(arrowRight, mx, my)) {
            tabOffset++;
            return true;
        }
        for (int[] r : tabRects) {
            if (mx >= r[0] && mx < r[0] + r[1] && my >= topPos + NetworkMonitorMenu.TABS_Y
                    && my < topPos + NetworkMonitorMenu.TABS_Y + TAB_H) {
                selectedTab = r[2];
                refreshBrowser();
                return true;
            }
        }
        if (browser.inside(browserX(), browserY(), mx, my)) return true; // read-only
        return super.mouseClicked(mx, my, button);
    }

    private static boolean hit(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tippen ins Suchfeld darf die GUI nicht schließen (z.B. 'e').
        if (search != null && search.isFocused() && keyCode != 256) {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (browser.mouseScrolled(browserX(), browserY(), mx, my, dy)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private void slot(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_FILL);
        g.renderOutline(x, y, w, h, SLOT_BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // eigener Look — keine Vanilla-Labels
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
