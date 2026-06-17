package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.menu.RouterMenu;
import com.herrderlocken.frogportnetworks.net.UpdateRouterPacket;
import com.herrderlocken.frogportnetworks.network.SubnetMask;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import net.createmod.catnip.gui.element.BoxElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * RouterScreen — im Create-Stil aufgebaut.
 *
 * Statt technischer Texteingabe (wie bei AE2 & Co.) werden alle Werte über
 * Creates {@link ScrollInput}-Widgets gesetzt: scrollen / klicken statt tippen,
 * genau wie bei Creates eigenen Konfig-Blöcken (Stockpile-/Threshold-Switch).
 * Der Hintergrund ist Creates Standard-Panel ({@link BoxElement}), Bestätigt
 * wird über einen {@link IconButton} mit Häkchen-Icon.
 */
public class RouterScreen extends AbstractSimiContainerScreen<RouterMenu> {

    private static final int WINDOW_W = 204;
    private static final int WINDOW_H = 156;

    // Create-typische, warme/zurückhaltende Textfarben auf dunklem Panel
    private static final int COLOR_TITLE   = 0xFFFFD79A; // sanftes Gold
    private static final int COLOR_LABEL   = 0xFFB8B6C8;
    private static final int COLOR_VALUE   = 0xFFEFEFF5;
    private static final int COLOR_MASK    = 0xFF8C8AA0;
    private static final int COLOR_STATUS  = 0xFF73D17A;
    private static final int COLOR_DIVIDER = 0x40FFFFFF;
    private static final int COLOR_SEP     = 0xFFD8D6E4;
    private static final int SLOT_FILL     = 0xFF373245;
    private static final int SLOT_BORDER   = 0xFF8A86A0;

    private final ScrollInput[] ipInputs = new ScrollInput[4];
    private ScrollInput cidrInput;
    private ScrollInput dhcpInput;
    private ScrollInput poolStartInput;
    private ScrollInput poolEndInput;
    private IconButton confirmButton;
    private Label maskLabel;

    private boolean dhcpEnabled;

    // Layout-Koordinaten (absolut, nach init() gesetzt)
    private int rowAddr, rowSubnet, rowDhcp, rowPool, colInput;

    private static final int INPUT_H = 16;
    private static final int IP_W = 24;
    private static final int IP_GAP = 28;

    public RouterScreen(RouterMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, WINDOW_H);
        super.init();

        int x = leftPos;
        int y = topPos;
        colInput = x + 70;
        rowAddr = y + 26;
        rowSubnet = y + 52;
        rowDhcp = y + 78;
        rowPool = y + 104;

        dhcpEnabled = menu.isDhcpEnabled();

        // === IP-Adresse: 4 Oktette ===
        for (int i = 0; i < 4; i++) {
            int ix = colInput + i * IP_GAP;
            ipInputs[i] = numberInput(ix, rowAddr, IP_W, 0, 256,
                    menu.getIpOctet(i), "Octet " + (i + 1));
        }

        // === Subnetz-Maske (CIDR) ===
        maskLabel = new Label(colInput + IP_W + 8, rowSubnet + 4, Component.empty())
                .colored(COLOR_MASK);
        addRenderableWidget(maskLabel);
        cidrInput = numberInput(colInput, rowSubnet, IP_W, 0, 33,
                menu.getCidrPrefix(), "Subnet Prefix (CIDR)");
        cidrInput.calling(this::updateMaskLabel);
        updateMaskLabel(menu.getCidrPrefix());

        // === DHCP an/aus ===
        Label dhcpValue = valueLabel(colInput + 6, rowDhcp + 4);
        dhcpInput = new ScrollInput(colInput, rowDhcp, 70, INPUT_H)
                .withRange(0, 2)
                .format(v -> Component.literal(v == 1 ? "Enabled" : "Disabled"))
                .titled(Component.literal("DHCP Server"))
                .addHint(Component.literal("Vergibt automatisch IPs an verbundene Geräte"))
                .calling(v -> {
                    dhcpEnabled = v == 1;
                    updatePoolActive();
                })
                .writingTo(dhcpValue);
        dhcpInput.setState(dhcpEnabled ? 1 : 0);
        dhcpInput.onChanged();
        addRenderableWidget(dhcpInput);
        addRenderableWidget(dhcpValue);

        // === DHCP-Pool Start/Ende ===
        poolStartInput = numberInput(colInput, rowPool, IP_W, 2, 255,
                menu.getDhcpPoolStart(), "DHCP Pool Start");
        poolEndInput = numberInput(colInput + IP_W + 10, rowPool, IP_W, 2, 255,
                menu.getDhcpPoolEnd(), "DHCP Pool End");
        updatePoolActive();

        // === Bestätigen ===
        confirmButton = new IconButton(x + WINDOW_W - 34, y + WINDOW_H - 30, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::applySettings);
        confirmButton.setToolTip(Component.literal("Übernehmen"));
        addRenderableWidget(confirmButton);
    }

    /** Erstellt ein Zahlen-ScrollInput mit zugehörigem Wert-Label und fügt beide hinzu. */
    private ScrollInput numberInput(int x, int y, int w, int min, int maxExclusive,
                                    int initial, String title) {
        Label value = valueLabel(x + 6, y + 4);
        ScrollInput input = new ScrollInput(x, y, w, INPUT_H)
                .withRange(min, maxExclusive)
                .format(v -> Component.literal(String.valueOf(v)))
                .titled(Component.literal(title))
                .writingTo(value);
        input.setState(initial);
        input.onChanged();
        addRenderableWidget(input);
        addRenderableWidget(value);
        return input;
    }

    private Label valueLabel(int x, int y) {
        return new Label(x, y, Component.empty()).withShadow().colored(COLOR_VALUE);
    }

    private void updateMaskLabel(int cidr) {
        if (maskLabel != null && cidr >= 0 && cidr <= 32) {
            maskLabel.text = Component.literal("= " + SubnetMask.fromCIDR(cidr));
        }
    }

    private void updatePoolActive() {
        // Kann während dhcpInput.onChanged() in init() feuern, bevor die Pool-Felder
        // existieren — der explizite Aufruf nach deren Erzeugung setzt den Zustand korrekt.
        if (poolStartInput == null || poolEndInput == null) return;
        poolStartInput.setActive(dhcpEnabled);
        poolEndInput.setActive(dhcpEnabled);
    }

    /** Liest alle Widget-Zustände aus und schickt sie an den Server. */
    private void applySettings() {
        PacketDistributor.sendToServer(new UpdateRouterPacket(
                menu.getBlockPos(),
                ipInputs[0].getState(), ipInputs[1].getState(),
                ipInputs[2].getState(), ipInputs[3].getState(),
                cidrInput.getState(),
                dhcpEnabled,
                poolStartInput.getState(), poolEndInput.getState()
        ));
        CreateFrogportNetworks.LOGGER.info("Router settings sent to server");
        onClose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Create-Panel (Vanilla-Tooltip-Stil: dunkler Grund + Gradient-Rahmen)
        new BoxElement()
                .at(x + 4, y + 4)
                .withBounds(WINDOW_W - 8, WINDOW_H - 8)
                .render(g);

        // Titel + Trennlinie
        g.drawString(font, "Network Router", x + 12, y + 10, COLOR_TITLE, false);
        g.fill(x + 10, y + 22, x + WINDOW_W - 10, y + 23, COLOR_DIVIDER);

        // Zeilen-Beschriftungen
        g.drawString(font, "Address", x + 12, rowAddr + 4, COLOR_LABEL, false);
        g.drawString(font, "Subnet",  x + 12, rowSubnet + 4, COLOR_LABEL, false);
        g.drawString(font, "DHCP",    x + 12, rowDhcp + 4, COLOR_LABEL, false);
        g.drawString(font, "Pool",    x + 12, rowPool + 4, COLOR_LABEL, false);

        // Vertiefte "Slots" hinter den Eingaben
        for (int i = 0; i < 4; i++) slot(g, colInput + i * IP_GAP, rowAddr, IP_W);
        slot(g, colInput, rowSubnet, IP_W);
        slot(g, colInput, rowDhcp, 70);
        slot(g, colInput, rowPool, IP_W);
        slot(g, colInput + IP_W + 10, rowPool, IP_W);

        // Trenner: Punkte zwischen den Oktetten
        for (int i = 0; i < 3; i++) {
            g.drawString(font, ".", colInput + i * IP_GAP + IP_W + 1, rowAddr + 4, COLOR_SEP, false);
        }
        // "/" vor dem CIDR-Feld und "-" zwischen den Pool-Feldern
        g.drawString(font, "/", colInput - 6, rowSubnet + 4, COLOR_SEP, false);
        g.drawString(font, "-", colInput + IP_W + 3, rowPool + 4, COLOR_SEP, false);

        // Status-Zeile
        g.drawString(font, "Status:", x + 12, y + WINDOW_H - 22, COLOR_LABEL, false);
        g.drawString(font, "Online", x + 50, y + WINDOW_H - 22, COLOR_STATUS, false);
    }

    /** Zeichnet einen kleinen vertieften Eingabe-Slot. */
    private void slot(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + INPUT_H, SLOT_FILL);
        g.renderOutline(x, y, w, INPUT_H, SLOT_BORDER);
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
