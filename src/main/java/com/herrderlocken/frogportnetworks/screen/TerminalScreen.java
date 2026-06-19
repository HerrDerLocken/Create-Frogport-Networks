package com.herrderlocken.frogportnetworks.screen;

import com.herrderlocken.frogportnetworks.client.ItemSorting;
import com.herrderlocken.frogportnetworks.client.StorageBrowser;
import com.herrderlocken.frogportnetworks.menu.TerminalMenu;
import com.herrderlocken.frogportnetworks.net.CraftRequestPacket;
import com.herrderlocken.frogportnetworks.net.RequestCraftablesPacket;
import com.herrderlocken.frogportnetworks.net.RequestDhcpPacket;
import com.herrderlocken.frogportnetworks.net.RequestNetworkSnapshotPacket;
import com.herrderlocken.frogportnetworks.net.SelectNetworkPacket;
import com.herrderlocken.frogportnetworks.net.SelectScopePacket;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * TerminalScreen — netzweite Storage-GUI mit Tabs: "All" (aggregiert über alle NAS)
 * plus ein Tab pro NAS-IP. Oben Netz-Status (DHCP/statisch/Netz-Auswahl), darunter
 * die Tab-Leiste, das scrollbare Inhalts-Raster ({@link StorageBrowser}) und das
 * Spieler-Inventar. Entnehmen aus dem gewählten Scope, Einlagern (Shift-Klick) ins Netz.
 */
public class TerminalScreen extends AbstractSimiContainerScreen<TerminalMenu> implements NetworkStorageHost, CraftablesHost {

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

    // Suche / Sortierung / Gruppierung
    private EditBox search;
    private SelectionScrollInput sortInput;
    private SelectionScrollInput groupInput;
    private ItemSorting.Sort sortMode = ItemSorting.Sort.COUNT_DESC;
    private ItemSorting.GroupBy groupMode = ItemSorting.GroupBy.NONE;

    private final StorageBrowser browser;
    private List<DeviceSnapshot> devices = new ArrayList<>();
    private int selectedTab; // 0 = All, sonst Gerät devices[tab-1]
    private int tabOffset;
    private long capUsed, capMax;
    private int reqTimer;

    // Craftbar-Index
    private List<ItemStack> craftables = new ArrayList<>();
    /** Item → Ausbeute pro Craft (z.B. Sticks = 4), aus dem Craftbar-Paket. */
    private final java.util.Map<net.minecraft.world.item.Item, Integer> craftYield = new java.util.HashMap<>();

    // Craft-Aufschlüsselung (Hover-Tooltip): pro Item angefragt + gecacht
    private record PlanData(boolean ok, List<ItemStack> consumed, List<ItemStack> crafted,
                            List<ItemStack> missing, int maxCrafts) {}
    private final java.util.Map<net.minecraft.world.item.Item, PlanData> planCache = new java.util.HashMap<>();
    private final java.util.Set<net.minecraft.world.item.Item> planRequested = new java.util.HashSet<>();

    // Craft-Warteschlange (mehrere Rezepte werden gesammelt und gemeinsam losgeschickt)
    private static final int QUEUE_MAX = 6;
    private static final int Q_ICON = 16;
    private final List<ItemStack> queueItems = new ArrayList<>();
    private final List<Integer> queueAmounts = new ArrayList<>();
    private int queueSel = -1; // welcher Eintrag gerade per Scroll editiert wird
    private ScrollInput craftAmount;
    private Label craftAmountLabel;
    private IconButton craftSend;
    private IconButton craftClear;

    // pro Frame berechnete Tab-Trefferflächen (für Klicks)
    private final List<int[]> tabRects = new ArrayList<>(); // {x, w, tabIndex}
    private int[] arrowLeft, arrowRight;

    public TerminalScreen(TerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.browser = new StorageBrowser(TerminalMenu.BROWSER_COLS, menu.getBrowserRows());
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

        // Suche + Sortierung + Gruppierung (Controls-Zeile)
        int ctrlY = y + TerminalMenu.CONTROLS_Y;
        search = new EditBox(font, x + TerminalMenu.GRID_X + 1, ctrlY, 80, 12, Component.literal("Search"));
        search.setBordered(true);
        search.setHint(Component.literal("Search…"));
        search.setResponder(s -> refreshBrowser());
        addRenderableWidget(search);

        Label sortLabel = new Label(x + 108, ctrlY + 2, Component.empty()).withShadow().colored(COLOR_VALUE);
        sortInput = new SelectionScrollInput(x + 104, ctrlY - 1, 42, INPUT_H - 2);
        sortInput.forOptions(optionLabels(ItemSorting.Sort.values(), v -> v.label));
        sortInput.titled(Component.literal("Sort"));
        sortInput.writingTo(sortLabel);
        sortInput.setState(sortMode.ordinal());
        sortInput.onChanged();
        sortInput.calling(s -> { sortMode = ItemSorting.Sort.values()[s]; refreshBrowser(); });
        addRenderableWidget(sortInput);
        addRenderableWidget(sortLabel);

        Label groupLabel = new Label(x + 152, ctrlY + 2, Component.empty()).withShadow().colored(COLOR_VALUE);
        groupInput = new SelectionScrollInput(x + 148, ctrlY - 1, 42, INPUT_H - 2);
        groupInput.forOptions(optionLabels(ItemSorting.GroupBy.values(), v -> v.label));
        groupInput.titled(Component.literal("Group"));
        groupInput.writingTo(groupLabel);
        groupInput.setState(groupMode.ordinal());
        groupInput.onChanged();
        groupInput.calling(s -> { groupMode = ItemSorting.GroupBy.values()[s]; refreshBrowser(); });
        addRenderableWidget(groupInput);
        addRenderableWidget(groupLabel);

        // Craft-Warteschlange-Widgets (versteckt, bis ein Item per Rechtsklick angereiht wird)
        int stripTop = stripTop();
        craftAmountLabel = new Label(x + 114, stripTop + 4, Component.empty()).withShadow().colored(COLOR_VALUE);
        craftAmount = new ScrollInput(x + 108, stripTop + 2, 40, 12)
                .withRange(1, 1025)
                .format(v -> Component.literal("x" + v))
                .titled(Component.literal("Amount"))
                .writingTo(craftAmountLabel)
                .calling(v -> {
                    if (queueSel >= 0 && queueSel < queueAmounts.size()) queueAmounts.set(queueSel, v);
                });
        craftAmount.setState(1);
        craftAmount.onChanged();
        craftSend = new IconButton(x + 152, stripTop, AllIcons.I_CONFIRM);
        craftSend.withCallback(this::sendQueue);
        craftSend.setToolTip(Component.literal("Send all craft jobs to a Computer"));
        craftClear = new IconButton(x + 172, stripTop, AllIcons.I_TRASH);
        craftClear.withCallback(this::clearQueue);
        craftClear.setToolTip(Component.literal("Clear craft queue"));
        addRenderableWidget(craftAmount);
        addRenderableWidget(craftAmountLabel);
        addRenderableWidget(craftSend);
        addRenderableWidget(craftClear);
        updateCraftWidgets();

        requestSnapshot();
        requestCraftables();
    }

    private int stripTop() { return topPos + TerminalMenu.BROWSER_Y - 16; }

    /** Reiht ein Item in die Craft-Queue ein (oder wählt es zum Editieren, falls schon enthalten). */
    private void enqueueCraft(ItemStack item) {
        for (int i = 0; i < queueItems.size(); i++) {
            if (ItemStack.isSameItemSameComponents(queueItems.get(i), item)) {
                selectQueueEntry(i);
                return;
            }
        }
        if (queueItems.size() >= QUEUE_MAX) return;
        queueItems.add(item.copyWithCount(1));
        queueAmounts.add(1);
        requestPlan(item); // für Mengen-Limit + Bedarfsanzeige
        selectQueueEntry(queueItems.size() - 1);
    }

    private void selectQueueEntry(int index) {
        queueSel = index;
        if (index >= 0 && index < queueAmounts.size()) {
            craftAmount.setState(queueAmounts.get(index));
            craftAmount.onChanged();
            applyCraftAmountLimit();
        }
        updateCraftWidgets();
    }

    private void removeQueueEntry(int index) {
        if (index < 0 || index >= queueItems.size()) return;
        queueItems.remove(index);
        queueAmounts.remove(index);
        if (queueItems.isEmpty()) queueSel = -1;
        else selectQueueEntry(Math.min(queueSel, queueItems.size() - 1));
        updateCraftWidgets();
    }

    private void clearQueue() {
        queueItems.clear();
        queueAmounts.clear();
        queueSel = -1;
        updateCraftWidgets();
    }

    /** Zeigt/versteckt die Queue-Widgets passend zum Füllstand. */
    private void updateCraftWidgets() {
        boolean v = !queueItems.isEmpty();
        if (craftAmount != null) { craftAmount.visible = v; craftAmount.active = v; }
        if (craftAmountLabel != null) craftAmountLabel.visible = v;
        if (craftSend != null) { craftSend.visible = v; craftSend.active = v; }
        if (craftClear != null) { craftClear.visible = v; craftClear.active = v; }
    }

    private void sendQueue() {
        if (queueItems.isEmpty()) return;
        List<ItemStack> protos = new ArrayList<>();
        for (ItemStack it : queueItems) protos.add(it.copyWithCount(1));
        PacketDistributor.sendToServer(
                new CraftRequestPacket(menu.getBlockPos(), protos, new ArrayList<>(queueAmounts)));
        clearQueue();
    }

    private void requestCraftables() {
        PacketDistributor.sendToServer(new RequestCraftablesPacket(menu.getBlockPos()));
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

    /** Teilt dem Server den gewählten Tab mit (Einlagern geht dann gezielt dorthin). */
    private void sendScope() {
        PacketDistributor.sendToServer(new SelectScopePacket(scopePos()));
    }

    @Override
    public BlockPos networkPos() { return menu.getBlockPos(); }

    @Override
    public void onNetworkSnapshot(List<DeviceSnapshot> devices) {
        this.devices = devices;
        if (selectedTab > devices.size()) selectedTab = 0;
        refreshBrowser();
        sendScope();
    }

    @Override
    public BlockPos craftablesPos() { return menu.getBlockPos(); }

    @Override
    public void onCraftables(List<ItemStack> items) {
        this.craftables = items;
        craftYield.clear();
        for (ItemStack it : items) {
            if (!it.isEmpty()) craftYield.put(it.getItem(), Math.max(1, it.getCount()));
        }
        // Aufschlüsselungen verfallen lassen (Bestand kann sich geändert haben).
        planCache.clear();
        planRequested.clear();
        refreshBrowser();
    }

    @Override
    public void onCraftPlan(ItemStack proto, boolean ok, List<ItemStack> consumed, List<ItemStack> crafted,
                            List<ItemStack> missing, int maxCrafts) {
        planCache.put(proto.getItem(), new PlanData(ok, consumed, crafted, missing, maxCrafts));
        // Wenn der gerade gewählte Queue-Eintrag betroffen ist: Mengen-Limit anwenden.
        if (queueSel >= 0 && queueSel < queueItems.size()
                && queueItems.get(queueSel).getItem() == proto.getItem()) {
            applyCraftAmountLimit();
        }
    }

    /** Begrenzt den Mengen-Regler des gewählten Eintrags auf das craftbare Maximum (laut Plan). */
    private void applyCraftAmountLimit() {
        if (craftAmount == null || queueSel < 0 || queueSel >= queueItems.size()) return;
        PlanData pd = planCache.get(queueItems.get(queueSel).getItem());
        int max = (pd != null && pd.ok()) ? Math.max(1, pd.maxCrafts()) : 1024;
        craftAmount.withRange(1, max + 1); // max ist exklusiv
        int clamped = Math.min(craftAmount.getState(), max);
        craftAmount.setState(clamped);
        craftAmount.onChanged();
        if (queueSel < queueAmounts.size()) queueAmounts.set(queueSel, clamped);
    }

    /** Fordert die Craft-Aufschlüsselung für ein Item an (einmal pro Cache-Zyklus). */
    private void requestPlan(ItemStack item) {
        if (planRequested.add(item.getItem())) {
            PacketDistributor.sendToServer(new com.herrderlocken.frogportnetworks.net.RequestCraftPlanPacket(
                    menu.getBlockPos(), item.copyWithCount(1)));
        }
    }

    /** Ausbeute pro Craft für ein Item (Standard 1). */
    private int yieldOf(ItemStack stack) {
        return craftYield.getOrDefault(stack.getItem(), 1);
    }

    /** Hängt die Craft-Aufschlüsselung (Verbrauch/Vorstufen bzw. Bedarf+Fehlendes) an einen Tooltip an. */
    private void appendPlanLines(List<Component> lines, ItemStack item, boolean flagged) {
        PlanData pd = planCache.get(item.getItem());
        if (pd == null) {
            if (flagged) lines.add(Component.literal("Calculating…").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            return;
        }
        if (pd.ok()) {
            if (!pd.consumed().isEmpty()) {
                lines.add(Component.literal("Uses from network:").withStyle(net.minecraft.ChatFormatting.GOLD));
                for (ItemStack c : pd.consumed()) lines.add(planLine(c, net.minecraft.ChatFormatting.GRAY, ""));
            }
            if (!pd.crafted().isEmpty()) {
                lines.add(Component.literal("Crafts on the way:").withStyle(net.minecraft.ChatFormatting.GOLD));
                for (ItemStack c : pd.crafted()) lines.add(planLine(c, net.minecraft.ChatFormatting.DARK_GRAY, ""));
            }
            return;
        }
        // Nicht machbar: nächstbestes Rezept + fehlende Zutaten zeigen.
        if (pd.consumed().isEmpty()) return; // gar kein Rezept → nichts anzeigen
        java.util.Set<net.minecraft.world.item.Item> miss = new java.util.HashSet<>();
        for (ItemStack m : pd.missing()) miss.add(m.getItem());
        lines.add(Component.literal("Recipe needs:").withStyle(net.minecraft.ChatFormatting.GOLD));
        for (ItemStack c : pd.consumed()) {
            boolean m = miss.contains(c.getItem());
            lines.add(planLine(c, m ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GRAY,
                    m ? " (missing)" : ""));
        }
    }

    /** Zeigt den GESAMT-Rohstoffbedarf eines Queue-Eintrags (Bedarf pro Craft × Anzahl Crafts). */
    private void appendTotalNeeds(List<Component> lines, ItemStack item, int crafts) {
        PlanData pd = planCache.get(item.getItem());
        if (pd == null || !pd.ok() || pd.consumed().isEmpty()) return;
        lines.add(Component.literal("Total needs:").withStyle(net.minecraft.ChatFormatting.GOLD));
        for (ItemStack c : pd.consumed()) {
            lines.add(Component.literal(" " + ((long) c.getCount() * crafts) + "x ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(c.getHoverName().copy().withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    private Component planLine(ItemStack c, net.minecraft.ChatFormatting color, String suffix) {
        return Component.literal(" " + c.getCount() + "x ").withStyle(color)
                .append(c.getHoverName().copy().withStyle(color))
                .append(Component.literal(suffix).withStyle(color));
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
        // Craftbar-Index (nur im "All"-Tab): craftbare, aber nicht vorrätige Items mit count 0 anhängen.
        if (selectedTab == 0) {
            for (ItemStack c : craftables) {
                boolean present = false;
                for (DiskEntry e : entries) {
                    if (net.minecraft.world.item.ItemStack.isSameItem(e.item(), c)) { present = true; break; }
                }
                if (!present) entries.add(new DiskEntry(c.copyWithCount(1), 0));
            }
        }

        // Suchfilter (Name oder Item-ID enthält den Text)
        String q = search == null ? "" : search.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) {
            entries.removeIf(e -> {
                String name = e.item().getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(e.item().getItem()).toString().toLowerCase(java.util.Locale.ROOT);
                return !name.contains(q) && !id.contains(q);
            });
        }

        // Sortierung + Gruppierung
        if (groupMode == ItemSorting.GroupBy.NONE) {
            ItemSorting.sort(entries, sortMode);
            browser.setEntries(entries);
        } else {
            browser.setGroups(ItemSorting.group(entries, groupMode, sortMode));
        }
    }

    private static <T> List<Component> optionLabels(T[] values, java.util.function.Function<T, String> fn) {
        List<Component> out = new ArrayList<>();
        for (T v : values) out.add(Component.literal(fn.apply(v)));
        return out;
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
        if (reqTimer % 40 == 0) requestCraftables();
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

        // Controls-Zeile: Sort/Group-Slots (Suche zeichnet ihren eigenen Rahmen)
        slot(g, x + 104, y + TerminalMenu.CONTROLS_Y - 1, 42, INPUT_H - 2);
        slot(g, x + 148, y + TerminalMenu.CONTROLS_Y - 1, 42, INPUT_H - 2);

        renderTabs(g);

        if (queueItems.isEmpty()) {
            // Kapazität rechts über dem Browser
            String cap = StorageBrowser.fmt(capUsed) + " / " + StorageBrowser.fmt(capMax) + " items";
            g.drawString(font, cap, x + TerminalMenu.WINDOW_W - 12 - font.width(cap),
                    y + TerminalMenu.BROWSER_Y - 10, COLOR_DIM, false);
        } else {
            renderCraftQueue(g);
        }

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

    /** Zeichnet die Craft-Queue-Leiste (Item-Icons + Mengen) über dem Browser. */
    private void renderCraftQueue(GuiGraphics g) {
        int top = stripTop();
        for (int i = 0; i < queueItems.size() && i < QUEUE_MAX; i++) {
            int cx = leftPos + 10 + i * Q_ICON;
            g.fill(cx, top, cx + Q_ICON, top + Q_ICON, SLOT_FILL);
            g.renderOutline(cx, top, Q_ICON, Q_ICON, i == queueSel ? COLOR_OK : SLOT_BORDER);
            g.renderItem(queueItems.get(i), cx + 0, top + 0);
            // Anzeige = Gesamt-Output (Anzahl Crafts × Ausbeute) – „wie viele man am Ende erhält".
            long total = (long) queueAmounts.get(i) * yieldOf(queueItems.get(i));
            String amt = StorageBrowser.fmt(total);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            float scale = 0.7f;
            g.pose().scale(scale, scale, 1);
            int tx = (int) ((cx + Q_ICON - 1 - font.width(amt) * scale) / scale);
            int ty = (int) ((top + Q_ICON - 1 - 6 * scale) / scale);
            g.drawString(font, amt, tx, ty, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
    }

    /** Index des Queue-Icons unter dem Cursor, oder -1. */
    private int queueIconAt(double mx, double my) {
        int top = stripTop();
        if (my < top || my >= top + Q_ICON) return -1;
        for (int i = 0; i < queueItems.size() && i < QUEUE_MAX; i++) {
            int cx = leftPos + 10 + i * Q_ICON;
            if (mx >= cx && mx < cx + Q_ICON) return i;
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // Tooltip für Queue-Icons
        int qi = queueIconAt(mouseX, mouseY);
        if (qi >= 0) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, queueItems.get(qi)));
            int crafts = queueAmounts.get(qi);
            int yield = yieldOf(queueItems.get(qi));
            lines.add(Component.literal(yield > 1
                    ? "x" + crafts + " crafts → " + ((long) crafts * yield) + " items"
                    : "Queued: x" + crafts)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            appendTotalNeeds(lines, queueItems.get(qi), crafts);
            lines.add(Component.literal("Left: select · Right: remove")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }

        DiskEntry hovered = browser.entryAt(browserX(), browserY(), mouseX, mouseY);
        if (hovered != null) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, hovered.item()));
            if (hovered.count() > 0) {
                lines.add(Component.literal(StorageBrowser.fmt(hovered.count()) + " stored")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            lines.add(Component.literal(hovered.count() > 0
                    ? "Left: take · Right: add to craft queue"
                    : "Craftable — right-click to queue")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            int yield = yieldOf(hovered.item());
            if (yield > 1) {
                lines.add(Component.literal("Yields " + yield + " per craft")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            // Craft-Aufschlüsselung: was wird verbraucht / mitgecraftet bzw. was fehlt
            requestPlan(hovered.item());
            boolean flagged = hovered.count() == 0 || craftYield.containsKey(hovered.item().getItem());
            appendPlanLines(lines, hovered.item(), flagged);
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
                sendScope();
                return true;
            }
        }

        // Craft-Queue-Leiste: Links = Eintrag wählen (Menge editieren), Rechts = entfernen.
        int qi = queueIconAt(mx, my);
        if (qi >= 0) {
            if (button == 1) removeQueueEntry(qi);
            else selectQueueEntry(qi);
            return true;
        }

        // Browser: Rechtsklick = in Craft-Queue einreihen; Links = 1, Shift+Links = Stack.
        DiskEntry e = browser.entryAt(browserX(), browserY(), mx, my);
        if (e != null) {
            if (button == 1) {
                enqueueCraft(e.item());
                return true;
            }
            if (e.count() > 0) { // vorrätig → entnehmen
                int amount = hasShiftDown() ? e.item().getMaxStackSize() : 1;
                PacketDistributor.sendToServer(new WithdrawItemPacket(scopePos(), e.item().copyWithCount(1), amount));
                requestSnapshot();
            }
            return true;
        }
        if (browser.inside(browserX(), browserY(), mx, my)) return true;

        // Create-Widgets (Octets, DHCP, Net-Selektor, Craft-Queue) zuletzt.
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

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
