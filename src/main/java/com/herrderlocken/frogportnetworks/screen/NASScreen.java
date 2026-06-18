package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.client.StorageBrowser;
import com.herrderlocken.frogportnetworks.menu.NASMenu;
import com.herrderlocken.frogportnetworks.net.RequestDhcpPacket;
import com.herrderlocken.frogportnetworks.net.RequestStorageSnapshotPacket;
import com.herrderlocken.frogportnetworks.net.SelectNetworkPacket;
import com.herrderlocken.frogportnetworks.net.SetStaticIpPacket;
import com.herrderlocken.frogportnetworks.net.WithdrawItemPacket;
import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
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
 * NASScreen — Disk-Laufwerk-GUI: Netz-Status oben, Disk-Bays, ein scrollbares
 * virtuelles Inhalts-Raster ({@link StorageBrowser}), darunter das Spieler-Inventar.
 */
public class NASScreen extends AbstractSimiContainerScreen<NASMenu> implements StorageHost {

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

    private static final int INPUT_H = 16;
    private static final int IP_W = 24;
    private static final int IP_GAP = 28;

    private final ScrollInput[] octetInputs = new ScrollInput[4];
    private int colInput, rowStatic, rowNet;
    private int windowH;

    private SelectionScrollInput netSelector;
    private List<DyeColor> availColors = new ArrayList<>();

    private final StorageBrowser browser = new StorageBrowser(NASMenu.BROWSER_COLS, NASMenu.BROWSER_ROWS);
    private StorageSnapshot snapshot = StorageSnapshot.EMPTY;
    private int reqTimer;

    public NASScreen(NASMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        int invY = menu.getInvY();
        int hotbarY = invY + 3 * NASMenu.SLOT + 4;
        windowH = hotbarY + NASMenu.SLOT + 8;
        setWindowSize(NASMenu.WINDOW_W, windowH);
        super.init();

        int x = leftPos;
        int y = topPos;
        colInput = x + 50;
        rowStatic = y + 46;
        rowNet = y + 66;

        IconButton dhcp = new IconButton(x + NASMenu.WINDOW_W - 30, y + 6, AllIcons.I_REFRESH);
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
        PacketDistributor.sendToServer(new RequestStorageSnapshotPacket(menu.getBlockPos()));
    }

    @Override
    public BlockPos storagePos() { return menu.getBlockPos(); }

    @Override
    public void onSnapshot(StorageSnapshot snapshot) {
        this.snapshot = snapshot;
        browser.setEntries(snapshot.entries());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++reqTimer % 10 == 0) requestSnapshot();
    }

    private int browserX() { return leftPos + NASMenu.BROWSER_X; }
    private int browserY() { return topPos + NASMenu.BROWSER_Y; }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        new BoxElement().at(x + 4, y + 4).withBounds(NASMenu.WINDOW_W - 8, windowH - 8).render(g);

        g.drawString(font, "Network Storage", x + 12, y + 10, COLOR_TITLE, false);
        g.fill(x + 10, y + 22, x + NASMenu.WINDOW_W - 10, y + 23, COLOR_DIVIDER);

        boolean connected = menu.isConnected();
        g.drawString(font, connected ? "Connected" : "Not connected",
                x + 12, y + 28, connected ? COLOR_OK : COLOR_FAIL, false);
        if (connected) {
            g.drawString(font, menu.getIpString(), x + 80, y + 28, COLOR_VALUE, false);
            DyeColor color = menu.getNetworkColor();
            int sw = 0xFF000000 | (color.getTextureDiffuseColor() & 0xFFFFFF);
            g.fill(x + NASMenu.WINDOW_W - 24, y + 27, x + NASMenu.WINDOW_W - 15, y + 36, sw);
            g.renderOutline(x + NASMenu.WINDOW_W - 24, y + 27, 9, 9, 0xFF000000);
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

        // Disk-Bays Beschriftung + Kapazität
        g.drawString(font, "Drives", x + 12, y + NASMenu.DRIVES_Y - 10, COLOR_LABEL, false);
        String cap = StorageBrowser.fmt(snapshot.usedItems()) + " / " + StorageBrowser.fmt(snapshot.maxItems()) + " items";
        g.drawString(font, cap, x + NASMenu.WINDOW_W - 12 - font.width(cap), y + NASMenu.DRIVES_Y - 10, COLOR_DIM, false);

        // Slot-Rahmen (Disk-Bays + Spieler-Inventar)
        for (Slot s : menu.slots) {
            slot(g, x + s.x - 1, y + s.y - 1, NASMenu.SLOT, NASMenu.SLOT);
        }

        browser.render(g, font, browserX(), browserY());
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
        DiskEntry e = browser.entryAt(browserX(), browserY(), mx, my);
        if (e != null) {
            int amount = button == 1 ? 1 : e.item().getMaxStackSize();
            PacketDistributor.sendToServer(new WithdrawItemPacket(menu.getBlockPos(), e.item().copyWithCount(1), amount));
            return true;
        }
        if (browser.inside(browserX(), browserY(), mx, my)) return true;
        return super.mouseClicked(mx, my, button);
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
