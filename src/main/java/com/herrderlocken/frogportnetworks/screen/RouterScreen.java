package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.menu.RouterMenu;
import com.herrderlocken.frogportnetworks.net.UpdateRouterPacket;
import com.herrderlocken.frogportnetworks.network.SubnetMask;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * RouterScreen — vollständige Router-GUI mit:
 * - IP-Adresse (4 Oktett-Felder)
 * - Subnet-Maske als CIDR (Textfeld, z.B. "24")
 * - DHCP Toggle
 * - DHCP Pool Start/End (Textfelder)
 * - Apply-Button der alles an den Server schickt
 */
public class RouterScreen extends AbstractContainerScreen<RouterMenu> {

    private EditBox[] ipFields = new EditBox[4];
    private EditBox cidrField;
    private EditBox poolStartField;
    private EditBox poolEndField;
    private Button dhcpToggleButton;
    private Button applyButton;
    private boolean localDhcpEnabled;

    // Farben als Konstanten für Übersichtlichkeit
    private static final int COLOR_ACCENT = 0xFF00d4aa;
    private static final int COLOR_BG = 0xCC1a1a2e;
    private static final int COLOR_LABEL = 0xFFCCCCCC;
    private static final int COLOR_TITLE = 0xFF00d4aa;
    private static final int COLOR_SUCCESS = 0xFF00ff88;
    private static final int COLOR_DIM = 0xFF888888;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    public RouterScreen(RouterMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 240;
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = -999;
        this.titleLabelY = -999;

        localDhcpEnabled = menu.isDhcpEnabled();

        int baseX = leftPos + 15;
        int baseY = topPos + 30;

        // === IP-Adresse: 4 Felder ===
        for (int i = 0; i < 4; i++) {
            ipFields[i] = new EditBox(this.font, baseX + 75 + i * 40, baseY, 32, 14,
                    Component.literal(""));
            ipFields[i].setMaxLength(3);
            ipFields[i].setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
            ipFields[i].setValue(String.valueOf(menu.getIpOctet(i)));
            addRenderableWidget(ipFields[i]);
        }

        // === Subnet CIDR ===
        cidrField = new EditBox(this.font, baseX + 75, baseY + 24, 32, 14,
                Component.literal(""));
        cidrField.setMaxLength(2);
        cidrField.setFilter(s -> s.isEmpty() || s.matches("\\d{1,2}"));
        cidrField.setValue(String.valueOf(menu.getCidrPrefix()));
        addRenderableWidget(cidrField);

        // === DHCP Toggle ===
        dhcpToggleButton = Button.builder(
                        getDhcpButtonText(),
                        button -> {
                            localDhcpEnabled = !localDhcpEnabled;
                            button.setMessage(getDhcpButtonText());
                            // Pool-Felder aktivieren/deaktivieren
                            poolStartField.setEditable(localDhcpEnabled);
                            poolEndField.setEditable(localDhcpEnabled);
                        })
                .pos(baseX + 75, baseY + 58)
                .size(70, 18)
                .build();
        addRenderableWidget(dhcpToggleButton);

        // === DHCP Pool Range ===
        poolStartField = new EditBox(this.font, baseX + 75, baseY + 86, 32, 14,
                Component.literal(""));
        poolStartField.setMaxLength(3);
        poolStartField.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
        poolStartField.setValue(String.valueOf(menu.getDhcpPoolStart()));
        poolStartField.setEditable(localDhcpEnabled);
        addRenderableWidget(poolStartField);

        poolEndField = new EditBox(this.font, baseX + 130, baseY + 86, 32, 14,
                Component.literal(""));
        poolEndField.setMaxLength(3);
        poolEndField.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
        poolEndField.setValue(String.valueOf(menu.getDhcpPoolEnd()));
        poolEndField.setEditable(localDhcpEnabled);
        addRenderableWidget(poolEndField);

        // === Apply Button ===
        applyButton = Button.builder(
                        Component.literal("Apply"),
                        button -> applySettings())
                .pos(baseX + 75, baseY + 120)
                .size(90, 20)
                .build();
        addRenderableWidget(applyButton);
    }

    private Component getDhcpButtonText() {
        return Component.literal(localDhcpEnabled ? "DHCP: ON" : "DHCP: OFF");
    }

    /**
     * Liest alle Felder aus und schickt ein UpdateRouterPacket an den Server.
     * PacketDistributor.sendToServer() serialisiert das Paket automatisch
     * über den STREAM_CODEC und schickt es.
     */
    private void applySettings() {
        try {
            int[] octets = new int[4];
            for (int i = 0; i < 4; i++) {
                String val = ipFields[i].getValue().isEmpty() ? "0" : ipFields[i].getValue();
                octets[i] = Integer.parseInt(val);
                if (octets[i] < 0 || octets[i] > 255) return;
            }

            String cidrVal = cidrField.getValue().isEmpty() ? "24" : cidrField.getValue();
            int cidr = Integer.parseInt(cidrVal);
            if (cidr < 0 || cidr > 32) return;

            String startVal = poolStartField.getValue().isEmpty() ? "100" : poolStartField.getValue();
            String endVal = poolEndField.getValue().isEmpty() ? "200" : poolEndField.getValue();
            int poolStart = Integer.parseInt(startVal);
            int poolEnd = Integer.parseInt(endVal);

            // Paket an Server senden
            PacketDistributor.sendToServer(new UpdateRouterPacket(
                    menu.getBlockPos(),
                    octets[0], octets[1], octets[2], octets[3],
                    cidr, localDhcpEnabled, poolStart, poolEnd
            ));

            CreateFrogportNetworks.LOGGER.info("Settings sent to server");

        } catch (NumberFormatException e) {
            CreateFrogportNetworks.LOGGER.warn("Invalid input in router GUI");
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Hintergrund
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_BG);
        // Außenrand
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, COLOR_ACCENT);
        // Trennlinie unter Titel
        g.fill(leftPos + 10, topPos + 20, leftPos + imageWidth - 10, topPos + 21, COLOR_ACCENT);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int x = 15;

        // Titel
        g.drawString(font, "Network Router", x, 7, COLOR_TITLE, false);

        // Labels links
        int fieldY = 30;
        g.drawString(font, "IP Address", x, fieldY + 3, COLOR_LABEL, false);

        // Punkte zwischen Oktetten
        for (int i = 0; i < 3; i++) {
            g.drawString(font, ".", x + 107 + i * 40, fieldY + 3, COLOR_WHITE, false);
        }

        g.drawString(font, "Subnet", x, fieldY + 27, COLOR_LABEL, false);
        // Zeige berechnete Maske neben dem CIDR-Feld
        try {
            String cidrVal = cidrField != null && !cidrField.getValue().isEmpty() ? cidrField.getValue() : "24";
            int cidr = Integer.parseInt(cidrVal);
            if (cidr >= 0 && cidr <= 32) {
                SubnetMask mask = SubnetMask.fromCIDR(cidr);
                g.drawString(font, "= " + mask.toString(), x + 115, fieldY + 27, COLOR_DIM, false);
            }
        } catch (NumberFormatException ignored) {}

        // CIDR Prefix-Zeichen
        g.drawString(font, "/", x + 65, fieldY + 27, COLOR_WHITE, false);

        g.drawString(font, "DHCP", x, fieldY + 62, COLOR_LABEL, false);

        g.drawString(font, "Pool", x, fieldY + 90, COLOR_LABEL, false);
        g.drawString(font, "-", x + 116, fieldY + 90, COLOR_WHITE, false);

        // Status-Zeile
        g.drawString(font, "Status:", x, fieldY + 148, COLOR_LABEL, false);
        g.drawString(font, "Online", x + 50, fieldY + 148, COLOR_SUCCESS, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
