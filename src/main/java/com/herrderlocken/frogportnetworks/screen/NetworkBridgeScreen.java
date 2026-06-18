package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.menu.NetworkBridgeMenu;
import com.herrderlocken.frogportnetworks.net.UpdateBridgePacket;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import net.createmod.catnip.gui.element.BoxElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NetworkBridgeScreen — Regel-GUI: Quell-IP (From), Ziel-IP (To), Item-Filter (Ghost-Slot,
 * leer = beliebig), Menge pro Transfer, Schwellwert ("nur senden solange Ziel < N") und
 * An/Aus. IP/Menge/Schwelle werden mit "Apply" übernommen; Filter und An/Aus sofort.
 */
public class NetworkBridgeScreen extends AbstractSimiContainerScreen<NetworkBridgeMenu> {

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
    private static final int IP_W = 22;
    private static final int IP_GAP = 26;

    private final ScrollInput[] srcInputs = new ScrollInput[4];
    private final ScrollInput[] dstInputs = new ScrollInput[4];
    private ScrollInput amountInput, thresholdInput;
    private Button enableButton;

    private int colInput, rowFrom, rowTo, rowOpts;
    private int windowH;

    private ItemStack filter = ItemStack.EMPTY;
    private boolean enabled = true;

    public NetworkBridgeScreen(NetworkBridgeMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        int invY = menu.getInvY();
        int hotbarY = invY + 3 * NetworkBridgeMenu.SLOT + 4;
        windowH = hotbarY + NetworkBridgeMenu.SLOT + 8;
        setWindowSize(NetworkBridgeMenu.WINDOW_W, windowH);
        super.init();

        int x = leftPos;
        int y = topPos;
        colInput = x + 44;
        rowFrom = y + 28;
        rowTo = y + 48;
        rowOpts = y + 70;

        filter = menu.getFilter();
        enabled = menu.isEnabled();

        makeOctets(srcInputs, rowFrom, menu::getSrcOctet);
        makeOctets(dstInputs, rowTo, menu::getDstOctet);

        // Menge
        Label amtLabel = new Label(x + 70, rowOpts + 4, Component.empty()).withShadow().colored(COLOR_VALUE);
        amountInput = new ScrollInput(x + 64, rowOpts, 40, INPUT_H)
                .withRange(1, 65537)
                .format(v -> Component.literal(String.valueOf(v)))
                .titled(Component.literal("Amount per transfer"))
                .writingTo(amtLabel);
        amountInput.setState(Math.max(1, menu.getAmount()));
        amountInput.onChanged();
        addRenderableWidget(amountInput);
        addRenderableWidget(amtLabel);

        // Schwellwert ("keep dest < N"; 0 = ohne Limit)
        Label thrLabel = new Label(x + 124, rowOpts + 4, Component.empty()).withShadow().colored(COLOR_VALUE);
        thresholdInput = new ScrollInput(x + 118, rowOpts, 56, INPUT_H)
                .withRange(0, 1000001)
                .format(v -> Component.literal(v == 0 ? "∞" : String.valueOf(v)))
                .titled(Component.literal("Keep target below (0 = no limit)"))
                .writingTo(thrLabel);
        thresholdInput.setState(Math.max(0, menu.getThreshold()));
        thresholdInput.onChanged();
        addRenderableWidget(thresholdInput);
        addRenderableWidget(thrLabel);

        // Apply
        IconButton apply = new IconButton(x + NetworkBridgeMenu.WINDOW_W - 28, rowFrom, AllIcons.I_CONFIRM);
        apply.withCallback(this::sendConfig);
        apply.setToolTip(Component.literal("Apply IPs / amount / threshold"));
        addRenderableWidget(apply);

        // An/Aus
        enableButton = Button.builder(enabledLabel(), b -> {
            enabled = !enabled;
            b.setMessage(enabledLabel());
            sendConfig();
        }).bounds(x + NetworkBridgeMenu.WINDOW_W - 40, rowTo, 32, INPUT_H).build();
        addRenderableWidget(enableButton);
    }

    private interface OctetSource { int get(int index); }

    private void makeOctets(ScrollInput[] arr, int row, OctetSource initial) {
        for (int i = 0; i < 4; i++) {
            int ix = colInput + i * IP_GAP;
            Label value = new Label(ix + 5, row + 4, Component.empty()).withShadow().colored(COLOR_VALUE);
            ScrollInput input = new ScrollInput(ix, row, IP_W, INPUT_H)
                    .withRange(0, 256)
                    .format(v -> Component.literal(String.valueOf(v)))
                    .titled(Component.literal("Octet " + (i + 1)))
                    .writingTo(value);
            input.setState(initial.get(i));
            input.onChanged();
            addRenderableWidget(input);
            addRenderableWidget(value);
            arr[i] = input;
        }
    }

    private Component enabledLabel() {
        return Component.literal(enabled ? "On" : "Off");
    }

    private void sendConfig() {
        int[] src = {srcInputs[0].getState(), srcInputs[1].getState(), srcInputs[2].getState(), srcInputs[3].getState()};
        int[] dst = {dstInputs[0].getState(), dstInputs[1].getState(), dstInputs[2].getState(), dstInputs[3].getState()};
        boolean hasSrc = !(src[0] == 0 && src[1] == 0 && src[2] == 0 && src[3] == 0);
        boolean hasDst = !(dst[0] == 0 && dst[1] == 0 && dst[2] == 0 && dst[3] == 0);
        PacketDistributor.sendToServer(new UpdateBridgePacket(menu.getBlockPos(),
                hasSrc, src, hasDst, dst, amountInput.getState(), thresholdInput.getState(), enabled, filter));
    }

    private int filterX() { return leftPos + NetworkBridgeMenu.GRID_X; }
    private int filterY() { return rowOpts; }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        new BoxElement().at(x + 4, y + 4).withBounds(NetworkBridgeMenu.WINDOW_W - 8, windowH - 8).render(g);

        g.drawString(font, "Network Bridge", x + 12, y + 10, COLOR_TITLE, false);
        g.fill(x + 10, y + 22, x + NetworkBridgeMenu.WINDOW_W - 10, y + 23, COLOR_DIVIDER);

        drawIpRow(g, "From", rowFrom, srcInputs, menu.isSrcResolved());
        drawIpRow(g, "To", rowTo, dstInputs, menu.isDstResolved());

        // Filter-Ghost-Slot + Menge/Schwelle
        slot(g, filterX(), filterY(), NetworkBridgeMenu.SLOT, NetworkBridgeMenu.SLOT);
        if (!filter.isEmpty()) g.renderItem(filter, filterX() + 1, filterY() + 1);
        g.drawString(font, "x", x + 58, rowOpts + 4, COLOR_LABEL, false);
        slot(g, x + 64, rowOpts, 40, INPUT_H);
        g.drawString(font, "≤", x + 110, rowOpts + 4, COLOR_LABEL, false);
        slot(g, x + 118, rowOpts, 56, INPUT_H);

        // Status
        int sy = rowOpts + 22;
        boolean s = menu.isSrcResolved(), d = menu.isDstResolved();
        g.drawString(font, "src", x + 12, sy, COLOR_LABEL, false);
        g.drawString(font, s ? "ok" : "?", x + 32, sy, s ? COLOR_OK : COLOR_FAIL, false);
        g.drawString(font, "dst", x + 52, sy, COLOR_LABEL, false);
        g.drawString(font, d ? "ok" : "?", x + 72, sy, d ? COLOR_OK : COLOR_FAIL, false);
        String moved = "moved " + menu.getLastMoved();
        g.drawString(font, moved, x + NetworkBridgeMenu.WINDOW_W - 12 - font.width(moved), sy, COLOR_DIM, false);

        for (Slot slot : menu.slots) {
            slot(g, x + slot.x - 1, y + slot.y - 1, NetworkBridgeMenu.SLOT, NetworkBridgeMenu.SLOT);
        }
    }

    private void drawIpRow(GuiGraphics g, String label, int row, ScrollInput[] inputs, boolean resolved) {
        int x = leftPos;
        g.drawString(font, label, x + 12, row + 4, resolved ? COLOR_OK : COLOR_LABEL, false);
        for (int i = 0; i < 4; i++) {
            slot(g, colInput + i * IP_GAP, row, IP_W, INPUT_H);
            if (i < 3) g.drawString(font, ".", colInput + i * IP_GAP + IP_W, row + 4, COLOR_SEP, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!filter.isEmpty() && mouseX >= filterX() && mouseX < filterX() + NetworkBridgeMenu.SLOT
                && mouseY >= filterY() && mouseY < filterY() + NetworkBridgeMenu.SLOT) {
            g.renderTooltip(font, filter, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= filterX() && mx < filterX() + NetworkBridgeMenu.SLOT
                && my >= filterY() && my < filterY() + NetworkBridgeMenu.SLOT) {
            ItemStack carried = menu.getCarried();
            filter = (button == 1 || carried.isEmpty()) ? ItemStack.EMPTY : carried.copyWithCount(1);
            sendConfig();
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
