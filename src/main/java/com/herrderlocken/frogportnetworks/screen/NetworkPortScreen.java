package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.blockentity.NetworkPortBlockEntity;
import com.herrderlocken.frogportnetworks.menu.NetworkPortMenu;
import com.herrderlocken.frogportnetworks.net.RequestDhcpPacket;
import com.herrderlocken.frogportnetworks.net.SelectNetworkPacket;
import com.herrderlocken.frogportnetworks.net.SetPortFilterPacket;
import com.herrderlocken.frogportnetworks.net.SetPortModePacket;
import com.herrderlocken.frogportnetworks.net.SetStaticIpPacket;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.createmod.catnip.gui.element.BoxElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * NetworkPortScreen — Konfigurations-GUI: Netz-Status (DHCP/statisch/Netzwahl),
 * I/O-Modus (rein/raus/beides) und Item-Filter (Ghost-Slots). Linksklick auf einen
 * Filter-Slot mit Item am Cursor setzt den Filter, Rechtsklick/leerer Cursor leert ihn.
 */
public class NetworkPortScreen extends AbstractSimiContainerScreen<NetworkPortMenu> {

    private static final int COLOR_TITLE = 0xFFFFD79A;
    private static final int COLOR_LABEL = 0xFFB8B6C8;
    private static final int COLOR_VALUE = 0xFFEFEFF5;
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
    private int colInput, rowStatic, rowNet, rowMode;
    private int windowH;

    private SelectionScrollInput netSelector;
    private List<DyeColor> availColors = new ArrayList<>();

    public NetworkPortScreen(NetworkPortMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        int invY = menu.getInvY();
        int hotbarY = invY + 3 * NetworkPortMenu.SLOT + 4;
        windowH = hotbarY + NetworkPortMenu.SLOT + 8;
        setWindowSize(NetworkPortMenu.WINDOW_W, windowH);
        super.init();

        int x = leftPos;
        int y = topPos;
        colInput = x + 50;
        rowStatic = y + 46;
        rowNet = y + 66;
        rowMode = y + 86;

        IconButton dhcp = new IconButton(x + NetworkPortMenu.WINDOW_W - 30, y + 6, AllIcons.I_REFRESH);
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

        // I/O-Modus
        List<Component> modeNames = List.of(
                Component.translatable("gui.frogportnetworks.mode.both"),
                Component.translatable("gui.frogportnetworks.mode.insert"),
                Component.translatable("gui.frogportnetworks.mode.extract"));
        Label modeLabel = new Label(x + 64, rowMode + 4, Component.empty()).withShadow().colored(COLOR_VALUE);
        SelectionScrollInput modeSelector = new SelectionScrollInput(x + 58, rowMode, 90, INPUT_H);
        modeSelector.forOptions(modeNames);
        modeSelector.titled(Component.literal("I/O Mode"));
        modeSelector.writingTo(modeLabel);
        modeSelector.setState(menu.getMode().ordinal());
        modeSelector.onChanged();
        modeSelector.calling(state -> PacketDistributor.sendToServer(new SetPortModePacket(menu.getBlockPos(), state)));
        addRenderableWidget(modeSelector);
        addRenderableWidget(modeLabel);
    }

    private void applyStatic() {
        PacketDistributor.sendToServer(new SetStaticIpPacket(
                menu.getBlockPos(),
                octetInputs[0].getState(), octetInputs[1].getState(),
                octetInputs[2].getState(), octetInputs[3].getState()));
    }

    private int filterX(int i) { return leftPos + NetworkPortMenu.FILTER_X + i * NetworkPortMenu.SLOT; }
    private int filterY() { return topPos + NetworkPortMenu.FILTER_Y; }

    private int filterCellAt(double mx, double my) {
        int fy = filterY();
        if (my < fy || my >= fy + NetworkPortMenu.SLOT) return -1;
        for (int i = 0; i < NetworkPortMenu.FILTER_COLS; i++) {
            int fx = filterX(i);
            if (mx >= fx && mx < fx + NetworkPortMenu.SLOT) return i;
        }
        return -1;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        new BoxElement().at(x + 4, y + 4).withBounds(NetworkPortMenu.WINDOW_W - 8, windowH - 8).render(g);

        g.drawString(font, "Network Port", x + 12, y + 10, COLOR_TITLE, false);
        g.fill(x + 10, y + 22, x + NetworkPortMenu.WINDOW_W - 10, y + 23, COLOR_DIVIDER);

        boolean connected = menu.isConnected();
        g.drawString(font, connected ? "Connected" : "Not connected",
                x + 12, y + 28, connected ? COLOR_OK : COLOR_FAIL, false);
        if (connected) {
            g.drawString(font, menu.getIpString(), x + 80, y + 28, COLOR_VALUE, false);
            DyeColor color = menu.getNetworkColor();
            int sw = 0xFF000000 | (color.getTextureDiffuseColor() & 0xFFFFFF);
            g.fill(x + NetworkPortMenu.WINDOW_W - 24, y + 27, x + NetworkPortMenu.WINDOW_W - 15, y + 36, sw);
            g.renderOutline(x + NetworkPortMenu.WINDOW_W - 24, y + 27, 9, 9, 0xFF000000);
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

        g.drawString(font, "Mode", x + 12, rowMode + 4, COLOR_LABEL, false);
        slot(g, x + 58, rowMode, 90, INPUT_H);

        // Filter (Whitelist; leer = alles)
        g.drawString(font, "Filter", x + 12, y + NetworkPortMenu.FILTER_Y - 10, COLOR_LABEL, false);
        for (int i = 0; i < NetworkPortMenu.FILTER_COLS; i++) {
            int fx = filterX(i);
            int fy = filterY();
            slot(g, fx, fy, NetworkPortMenu.SLOT, NetworkPortMenu.SLOT);
            ItemStack f = menu.getFilter(i);
            if (!f.isEmpty()) g.renderItem(f, fx + 1, fy + 1);
        }

        // Spieler-Inventar-Rahmen
        for (Slot s : menu.slots) {
            slot(g, x + s.x - 1, y + s.y - 1, NetworkPortMenu.SLOT, NetworkPortMenu.SLOT);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cell = filterCellAt(mouseX, mouseY);
        if (cell >= 0) {
            ItemStack f = menu.getFilter(cell);
            if (!f.isEmpty()) g.renderTooltip(font, f, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cell = filterCellAt(mx, my);
        if (cell >= 0) {
            ItemStack carried = menu.getCarried();
            ItemStack set = (button == 1 || carried.isEmpty()) ? ItemStack.EMPTY : carried.copyWithCount(1);
            menu.setFilterClient(cell, set);
            PacketDistributor.sendToServer(new SetPortFilterPacket(menu.getBlockPos(), cell, set));
            return true;
        }
        return super.mouseClicked(mx, my, button);
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
