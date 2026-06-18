package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.menu.ComputerMenu;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * ComputerScreen — Upgrade-Slots + Status (RPM, SU-Impact, Strom/Überlastung).
 * Crafting-Anzeige folgt, sobald die Craft-Engine steht.
 */
public class ComputerScreen extends AbstractSimiContainerScreen<ComputerMenu> {

    private static final int COLOR_TITLE = 0xFFFFD79A;
    private static final int COLOR_LABEL = 0xFFB8B6C8;
    private static final int COLOR_VALUE = 0xFFEFEFF5;
    private static final int COLOR_OK    = 0xFF73D17A;
    private static final int COLOR_FAIL  = 0xFFE06A6A;
    private static final int COLOR_DIVIDER = 0x40FFFFFF;
    private static final int SLOT_FILL   = 0xFF373245;
    private static final int SLOT_BORDER = 0xFF8A86A0;

    private int windowH;

    public ComputerScreen(ComputerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        int invY = menu.getInvY();
        int hotbarY = invY + 3 * ComputerMenu.SLOT + 4;
        windowH = hotbarY + ComputerMenu.SLOT + 8;
        setWindowSize(ComputerMenu.WINDOW_W, windowH);
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        new BoxElement().at(x + 4, y + 4).withBounds(ComputerMenu.WINDOW_W - 8, windowH - 8).render(g);

        g.drawString(font, "Network Computer", x + 12, y + 10, COLOR_TITLE, false);
        g.fill(x + 10, y + 22, x + ComputerMenu.WINDOW_W - 10, y + 23, COLOR_DIVIDER);

        // Status
        boolean powered = menu.isPowered();
        boolean over = menu.isOverStressed();
        String state = over ? "Overstressed" : (powered ? "Running" : "No rotation");
        g.drawString(font, state, x + 12, y + 28, over ? COLOR_FAIL : (powered ? COLOR_OK : COLOR_LABEL), false);
        String stats = menu.getRpm() + " RPM   " + menu.getStressImpact() + " SU/rpm";
        g.drawString(font, stats, x + ComputerMenu.WINDOW_W - 12 - font.width(stats), y + 28, COLOR_VALUE, false);

        g.drawString(font, "Upgrades", x + 12, y + ComputerMenu.UPGRADES_Y - 10, COLOR_LABEL, false);

        for (Slot s : menu.slots) {
            slot(g, x + s.x - 1, y + s.y - 1, ComputerMenu.SLOT, ComputerMenu.SLOT);
        }
    }

    private void slot(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_FILL);
        g.renderOutline(x, y, w, h, SLOT_BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // eigener Look
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
