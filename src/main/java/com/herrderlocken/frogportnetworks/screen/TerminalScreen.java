package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.menu.TerminalMenu;
import com.herrderlocken.frogportnetworks.net.RequestDhcpPacket;
import com.herrderlocken.frogportnetworks.net.SelectNetworkPacket;
import com.herrderlocken.frogportnetworks.net.SetStaticIpPacket;
import com.herrderlocken.frogportnetworks.network.SubnetMask;
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
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * TerminalScreen — Create-natives Status-Panel + manuelle IP-Eingabe.
 *
 * Oben: Live-Status (verbunden, IP, Gateway, Subnetz, Netz-Farbe) aus der ContainerData.
 * Unten: 4 ScrollInputs für eine statische IP + Häkchen-Button (sendet {@link SetStaticIpPacket}).
 * Rechts oben: Refresh-Button für DHCP ({@link RequestDhcpPacket}).
 */
public class TerminalScreen extends AbstractSimiContainerScreen<TerminalMenu> {

    private static final int WINDOW_W = 210;
    private static final int WINDOW_H = 162;

    private static final int COLOR_TITLE   = 0xFFFFD79A;
    private static final int COLOR_LABEL   = 0xFFB8B6C8;
    private static final int COLOR_VALUE   = 0xFFEFEFF5;
    private static final int COLOR_DIM     = 0xFF8C8AA0;
    private static final int COLOR_OK      = 0xFF73D17A;
    private static final int COLOR_FAIL    = 0xFFE06A6A;
    private static final int COLOR_DIVIDER = 0x40FFFFFF;
    private static final int COLOR_SEP     = 0xFFD8D6E4;
    private static final int SLOT_FILL     = 0xFF373245;
    private static final int SLOT_BORDER   = 0xFF8A86A0;

    private static final int INPUT_H = 16;
    private static final int IP_W = 24;
    private static final int IP_GAP = 28;

    private final ScrollInput[] octetInputs = new ScrollInput[4];
    private int colInput, rowStatic, rowNet;

    private SelectionScrollInput netSelector;
    private List<DyeColor> availColors = new ArrayList<>();

    public TerminalScreen(TerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, WINDOW_H);
        super.init();

        int x = leftPos;
        int y = topPos;
        colInput = x + 60;
        rowNet = y + 90;
        rowStatic = y + WINDOW_H - 46;

        // DHCP (Refresh) oben rechts
        IconButton dhcp = new IconButton(x + WINDOW_W - 30, y + 8, AllIcons.I_REFRESH);
        dhcp.withCallback(() -> PacketDistributor.sendToServer(new RequestDhcpPacket(menu.getBlockPos())));
        dhcp.setToolTip(Component.literal("DHCP: automatisch verbinden / erneuern"));
        addRenderableWidget(dhcp);

        // Netz-Auswahl: nur wenn mehrere Kabelfarben anliegen
        availColors = menu.getAvailableColors();
        if (availColors.size() >= 2) {
            List<Component> names = new ArrayList<>();
            for (DyeColor c : availColors) names.add(Component.literal(capitalize(c.getName())));

            Label netLabel = new Label(colInput + 24, rowNet + 4, Component.empty()).withShadow().colored(COLOR_VALUE);
            netSelector = new SelectionScrollInput(colInput + 18, rowNet, 90, INPUT_H);
            netSelector.forOptions(names);
            netSelector.titled(Component.literal("Network"));
            netSelector.writingTo(netLabel);
            int initial = Math.max(0, availColors.indexOf(menu.getNetworkColor()));
            netSelector.setState(initial);
            netSelector.onChanged();
            // calling erst NACH dem initialen onChanged setzen → kein Paket beim Öffnen
            netSelector.calling(state -> PacketDistributor.sendToServer(
                    new SelectNetworkPacket(menu.getBlockPos(), availColors.get(state).getId())));
            addRenderableWidget(netSelector);
            addRenderableWidget(netLabel);
        }

        // Statische IP: 4 Oktett-Felder, vorbelegt mit der aktuellen IP
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

        // Statische IP übernehmen
        IconButton apply = new IconButton(colInput + 4 * IP_GAP + 2, rowStatic, AllIcons.I_CONFIRM);
        apply.withCallback(this::applyStatic);
        apply.setToolTip(Component.literal("Statische IP übernehmen"));
        addRenderableWidget(apply);
    }

    private void applyStatic() {
        PacketDistributor.sendToServer(new SetStaticIpPacket(
                menu.getBlockPos(),
                octetInputs[0].getState(), octetInputs[1].getState(),
                octetInputs[2].getState(), octetInputs[3].getState()));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        new BoxElement().at(x + 4, y + 4).withBounds(WINDOW_W - 8, WINDOW_H - 8).render(g);

        g.drawString(font, "Network Terminal", x + 12, y + 10, COLOR_TITLE, false);
        g.fill(x + 10, y + 22, x + WINDOW_W - 10, y + 23, COLOR_DIVIDER);

        boolean connected = menu.isConnected();
        g.drawString(font, "Status", x + 12, y + 30, COLOR_LABEL, false);
        g.drawString(font, connected ? "Connected" : "Not connected", x + 70, y + 30,
                connected ? COLOR_OK : COLOR_FAIL, false);

        if (connected) {
            g.drawString(font, "IP", x + 12, y + 44, COLOR_LABEL, false);
            g.drawString(font, menu.getIpString(), x + 70, y + 44, COLOR_VALUE, false);

            g.drawString(font, "Gateway", x + 12, y + 58, COLOR_LABEL, false);
            g.drawString(font, menu.getGatewayString(), x + 70, y + 58, COLOR_VALUE, false);

            int cidr = menu.getCidrPrefix();
            String mask = (cidr >= 0 && cidr <= 32) ? SubnetMask.fromCIDR(cidr).toString() : "?";
            g.drawString(font, "Subnet", x + 12, y + 72, COLOR_LABEL, false);
            g.drawString(font, "/" + cidr + "  " + mask, x + 70, y + 72, COLOR_DIM, false);

            DyeColor color = menu.getNetworkColor();
            int swatch = 0xFF000000 | (color.getTextureDiffuseColor() & 0xFFFFFF);
            g.fill(x + 150, y + 30, x + 159, y + 39, swatch);
            g.renderOutline(x + 150, y + 30, 9, 9, 0xFF000000);
        } else {
            g.drawString(font, "DHCP (button) or enter a static IP below.", x + 12, y + 44, COLOR_DIM, false);
        }

        // Netz-Auswahl (nur bei mehreren Kabelfarben)
        if (netSelector != null) {
            g.drawString(font, "Net", x + 12, rowNet + 4, COLOR_LABEL, false);
            DyeColor sel = availColors.get(netSelector.getState());
            int sw = 0xFF000000 | (sel.getTextureDiffuseColor() & 0xFFFFFF);
            g.fill(x + 40, rowNet + 3, x + 49, rowNet + 12, sw);
            g.renderOutline(x + 40, rowNet + 3, 9, 9, 0xFF000000);
            slot(g, colInput + 18, rowNet, 90);
        }

        // Divider + statische IP-Zeile
        g.fill(x + 10, rowStatic - 8, x + WINDOW_W - 10, rowStatic - 7, COLOR_DIVIDER);
        g.drawString(font, "Static IP", x + 12, rowStatic + 4, COLOR_LABEL, false);
        for (int i = 0; i < 4; i++) {
            slot(g, colInput + i * IP_GAP, rowStatic, IP_W);
            if (i < 3) g.drawString(font, ".", colInput + i * IP_GAP + IP_W + 1, rowStatic + 4, COLOR_SEP, false);
        }
    }

    private void slot(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + INPUT_H, SLOT_FILL);
        g.renderOutline(x, y, w, INPUT_H, SLOT_BORDER);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }

    @Override
    protected void renderForeground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderForeground(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
