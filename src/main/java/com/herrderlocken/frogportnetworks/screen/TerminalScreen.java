package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.client.StorageBrowser;
import com.herrderlocken.frogportnetworks.menu.TerminalMenu;
import com.herrderlocken.frogportnetworks.net.RequestDhcpPacket;
import com.herrderlocken.frogportnetworks.net.RequestNetworkSnapshotPacket;
import com.herrderlocken.frogportnetworks.net.SelectNetworkPacket;
import com.herrderlocken.frogportnetworks.net.SetStaticIpPacket;
import com.herrderlocken.frogportnetworks.net.WithdrawItemPacket;
import com.herrderlocken.frogportnetworks.storage.DeviceSnapshot;
import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.createmod.catnip.gui.element.BoxElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * TerminalScreen — netzweite Storage-GUI mit Tabs: "All" (aggregiert über alle NAS)
 * plus ein Tab pro NAS-IP. Oben Netz-Status (DHCP/statisch/Netz-Auswahl), darunter
 * die Tab-Leiste, das scrollbare Inhalts-Raster ({@link StorageBrowser}) und das
 * Spieler-Inventar. Entnehmen aus dem gewählten Scope, Einlagern (Shift-Klick) ins Netz.
 */
public class TerminalScreen extends AbstractSimiContainerScreen<TerminalMenu> implements NetworkStorageHost {

    private static final int COLOR_TITLE = 0xFFFFD79A;
    private static final int COLOR_LABEL = 0xFFB8B6C8;
    private static final int COLOR_VALUE = 0xFFEFEFF5;
    private static final int COLOR_DIM   = 0xFF8C8AA0;
    private static final int COLOR_OK    = 0xFF73D17A;
    private static final int COLOR_FAIL  = 0xFFE06A6A;
    private static final int COLOR_DIVIDER = 0x40FFFFFF;
    private static final int COLOR_SEP   = 0xFFD8D6E4;
    private static final int SLOT_FILL   = 0xFF373245;
    private static final int SLOT_BORDER = 0xFF8A86A0;
    private static final int TAB_BG      = 0xFF2A2733;
    private static final int TAB_BG_SEL  = 0xFF4A4757;
    private static final int TAB_BORDER  = 0xFF8A86A0;

    private static final int INPUT_H = 16;
    private static final int IP_W = 24;
    private static final int IP_GAP = 28;
    private static final int TAB_H = 14;
    private static final int ARROW_W = 10;

    private final ScrollInput[] octetInputs = new ScrollInput[4];
    private int colInput, rowStatic, rowNet;
    private int windowH;

    private SelectionScrollInput netSelector;
    private List<DyeColor> availColors = new ArrayList<>();

    private final StorageBrowser browser = new StorageBrowser(TerminalMenu.BROWSER_COLS, TerminalMenu.BROWSER_ROWS);
    private List<DeviceSnapshot> devices = new ArrayList<>();
    private int selectedTab; // 0 = All, sonst Gerät devices[tab-1]
    private int tabOffset;
    private long capUsed, capMax;
    private int reqTimer;

    // pro Frame berechnete Tab-Trefferflächen (für Klicks)
    private final List<int[]> tabRects = new ArrayList<>(); // {x, w, tabIndex}
    private int[] arrowLeft, arrowRight;

    public TerminalScreen(TerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        int invY = menu.getInvY();
        int hotbarY = invY + 3 * TerminalMenu.SLOT + 4;
        windowH = hotbarY + TerminalMenu.SLOT + 8;
        setWindowSize(TerminalMenu.WINDOW_W, windowH);
        super.init();

        int x = leftPos;
        int y = topPos;
        colInput = x + 50;
        rowStatic = y + 46;
        rowNet = y + 66;

        IconButton dhcp = new IconButton(x + TerminalMenu.WINDOW_W - 30, y + 6, AllIcons.I_REFRESH);
        dhcp.withCallback(() -> PacketDistributor.sendToServer(new RequestDhcpPacket(menu.getBlockPos())));
        dhcp.setToolTip(Component.literal("DHCP: automatisch verbinden / erneuern"));
        addRenderableWidget(dhcp);

        for (int i = 0; i < 4; i++) {
            int ix = colInput + i * IP_GAP;
            Label value = new Label(ix + 6, rowStatic + 4, Component.empty()).withShadow().colored(COLOR_VALUE);
            ScrollInput input = new ScrollInput(ix, rowStatic, IP_W, INPUT_H)
                    .withRange(0, 256)
                    .format(v -> Component.literal(String.valueOf(v)))
                    .titled(Component.literal("Octet " + (i + 1)))
                    .writingTo(value);
            input.setState(menu.getIpOctet(i));
            input.onChanged();
            addRenderableWidget(input);
            addRenderableWidget(value);
            octetInputs[i] = input;
        }

        IconButton apply = new IconButton(colInput + 4 * IP_GAP + 2, rowStatic, AllIcons.I_CONFIRM);
        apply.withCallback(this::applyStatic);
        apply.setToolTip(Component.literal("Statische IP übernehmen"));
        addRenderableWidget(apply);

        availColors = menu.getAvailableColors();
        if (availColors.size() >= 2) {
            List<Component> names = new ArrayList<>();
            for (DyeColor c : availColors) names.add(Component.literal(capitalize(c.getName())));
            Label netLabel = new Label(x + 64, rowNet + 4, Component.empty()).withShadow().colored(COLOR_VALUE);
            netSelector = new SelectionScrollInput(x + 58, rowNet, 90, INPUT_H);
            netSelector.forOptions(names);
            netSelector.titled(Component.literal("Network"));
            netSelector.writingTo(netLabel);
            netSelector.setState(Math.max(0, availColors.indexOf(menu.getNetworkColor())));
            netSelector.onChanged();
            netSelector.calling(state -> PacketDistributor.sendToServer(
                    new SelectNetworkPacket(menu.getBlockPos(), availColors.get(state).getId())));
            addRenderableWidget(netSelector);
            addRenderableWidget(netLabel);
        }

        requestSnapshot();
    }

    private void applyStatic() {
        PacketDistributor.sendToServer(new SetStaticIpPacket(
                menu.getBlockPos(),
                octetInputs[0].getState(), octetInputs[1].getState(),
                octetInputs[2].getState(), octetInputs[3].getState()));
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

    /** Befüllt den Browser je nach gewähltem Tab (All = aggregiert). */
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
        browser.setEntries(entries);
    }

    private static void merge(List<DiskEntry> list, DiskEntry e) {
        for (int i = 0; i < list.size(); i++) {
            if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(list.get(i).item(), e.item())) {
                list.set(i, new DiskEntry(list.get(i).item(), list.get(i).count() + e.count()));
                return;
            }
        }
        list.add(e);
    }

    private BlockPos scopePos() {
        if (selectedTab == 0 || selectedTab - 1 >= devices.size()) return menu.getBlockPos();
        return devices.get(selectedTab - 1).pos();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++reqTimer % 10 == 0) requestSnapshot();
    }

    private int browserX() { return leftPos + TerminalMenu.BROWSER_X; }
    private int browserY() { return topPos + TerminalMenu.BROWSER_Y; }

    // === Rendering ===

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        new BoxElement().at(x + 4, y + 4).withBounds(TerminalMenu.WINDOW_W - 8, windowH - 8).render(g);

        g.drawString(font, "Network Terminal", x + 12, y + 10, COLOR_TITLE, false);
        g.fill(x + 10, y + 22, x + TerminalMenu.WINDOW_W - 10, y + 23, COLOR_DIVIDER);

        boolean connected = menu.isConnected();
        g.drawString(font, connected ? "Connected" : "Not connected",
                x + 12, y + 28, connected ? COLOR_OK : COLOR_FAIL, false);
        if (connected) {
            g.drawString(font, menu.getIpString(), x + 80, y + 28, COLOR_VALUE, false);
            DyeColor color = menu.getNetworkColor();
            int sw = 0xFF000000 | (color.getTextureDiffuseColor() & 0xFFFFFF);
            g.fill(x + TerminalMenu.WINDOW_W - 24, y + 27, x + TerminalMenu.WINDOW_W - 15, y + 36, sw);
            g.renderOutline(x + TerminalMenu.WINDOW_W - 24, y + 27, 9, 9, 0xFF000000);
        }

        g.drawString(font, "Static", x + 12, rowStatic + 4, COLOR_LABEL, false);
        for (int i = 0; i < 4; i++) {
            slot(g, colInput + i * IP_GAP, rowStatic, IP_W, INPUT_H);
            if (i < 3) g.drawString(font, ".", colInput + i * IP_GAP + IP_W + 1, rowStatic + 4, COLOR_SEP, false);
        }

        if (netSelector != null) {
            g.drawString(font, "Net", x + 12, rowNet + 4, COLOR_LABEL, false);
            DyeColor sel = availColors.get(netSelector.getState());
            int sw = 0xFF000000 | (sel.getTextureDiffuseColor() & 0xFFFFFF);
            g.fill(x + 40, rowNet + 3, x + 49, rowNet + 12, sw);
            g.renderOutline(x + 40, rowNet + 3, 9, 9, 0xFF000000);
            slot(g, x + 58, rowNet, 90, INPUT_H);
        }

        renderTabs(g);

        // Kapazität rechts über dem Browser
        String cap = StorageBrowser.fmt(capUsed) + " / " + StorageBrowser.fmt(capMax) + " items";
        g.drawString(font, cap, x + TerminalMenu.WINDOW_W - 12 - font.width(cap),
                y + TerminalMenu.BROWSER_Y - 10, COLOR_DIM, false);

        for (Slot s : menu.slots) {
            slot(g, x + s.x - 1, y + s.y - 1, TerminalMenu.SLOT, TerminalMenu.SLOT);
        }

        browser.render(g, font, browserX(), browserY());
    }

    /** Zeichnet die Tab-Leiste (All + pro IP) mit Pager-Pfeilen bei Überlauf. */
    private void renderTabs(GuiGraphics g) {
        tabRects.clear();
        arrowLeft = null;
        arrowRight = null;

        int y = topPos + TerminalMenu.TABS_Y;
        int areaX = leftPos + 10;
        int areaR = leftPos + TerminalMenu.WINDOW_W - 10;

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
            // linker Pfeil
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
        // Tabs
        if (arrowLeft != null && hit(arrowLeft, mx, my)) {
            if (tabOffset > 0) tabOffset--;
            return true;
        }
        if (arrowRight != null && hit(arrowRight, mx, my)) {
            tabOffset++;
            return true;
        }
        for (int[] r : tabRects) {
            if (mx >= r[0] && mx < r[0] + r[1] && my >= topPos + TerminalMenu.TABS_Y
                    && my < topPos + TerminalMenu.TABS_Y + TAB_H) {
                selectedTab = r[2];
                refreshBrowser();
                return true;
            }
        }

        // Browser-Entnahme
        DiskEntry e = browser.entryAt(browserX(), browserY(), mx, my);
        if (e != null) {
            int amount = button == 1 ? 1 : e.item().getMaxStackSize();
            PacketDistributor.sendToServer(new WithdrawItemPacket(scopePos(), e.item().copyWithCount(1), amount));
            requestSnapshot();
            return true;
        }
        if (browser.inside(browserX(), browserY(), mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    private static boolean hit(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
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

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
