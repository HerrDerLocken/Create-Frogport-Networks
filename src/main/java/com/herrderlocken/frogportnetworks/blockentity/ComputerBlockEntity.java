package com.herrderlocken.frogportnetworks.blockentity;

import com.herrderlocken.frogportnetworks.craft.CraftEngine;
import com.herrderlocken.frogportnetworks.item.ComputerUpgrade;
import com.herrderlocken.frogportnetworks.item.ComputerUpgradeItem;
import com.herrderlocken.frogportnetworks.menu.ComputerMenu;
import com.herrderlocken.frogportnetworks.network.RoutingManager;
import com.herrderlocken.frogportnetworks.registry.ModBlockEntities;
import com.herrderlocken.frogportnetworks.storage.AggregateNetworkStorage;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ComputerBlockEntity — der Autocrafting-Rechner (Create-Stress-Verbraucher). Basis-Impact
 * {@link #BASE_STRESS} SU/RPM; jedes gesteckte Upgrade erhöht den Impact (KI-Chip +16, sonst +2),
 * siehe {@link #calculateStressApplied()}. Das Arbeitstempo skaliert mit der Drehzahl (getSpeed),
 * bei Überlastung steht er still. Crafting-Logik folgt in späteren Schritten.
 */
public class ComputerBlockEntity extends KineticBlockEntity implements MenuProvider {

    public static final int UPGRADE_SLOTS = 9;
    public static final float BASE_STRESS = 16f;
    /** Fortschritts-Schwelle pro Craft; pro Tick wird die RPM aufaddiert (256 RPM ⇒ 1 Craft/Tick). */
    public static final int CRAFT_THRESHOLD = 256;

    /** Maximale Anzahl wartender Aufträge in der Warteschlange. */
    public static final int MAX_QUEUE = 32;

    // Aktueller Crafting-Auftrag + Warteschlange (transient; nicht persistiert)
    private ItemStack craftTarget = ItemStack.EMPTY;
    private int craftRemaining = 0;
    private int progress = 0;
    private final java.util.ArrayDeque<Job> queue = new java.util.ArrayDeque<>();

    /** Ein wartender Crafting-Auftrag (Ziel-Item + Anzahl). */
    private record Job(ItemStack target, int amount) {}

    private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof ComputerUpgradeItem;
        }
    };

    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPUTER.get(), pos, state);
        upgrades.addListener(c -> {
            setChanged();
            // Stress im Create-Netz sofort neu setzen, sobald Upgrades sich ändern
            // (networkDirty allein übernimmt den geänderten Impact nicht zuverlässig).
            if (level != null && !level.isClientSide && hasNetwork()) {
                getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
            }
        });
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // keine Zusatz-Behaviours
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (craftTarget.isEmpty()) pullNext();          // nächsten Auftrag aus der Queue holen
        if (craftTarget.isEmpty() || !isPowered()) return;

        progress += getRpm();
        if (progress < CRAFT_THRESHOLD) return;
        progress -= CRAFT_THRESHOLD;

        NetworkStorage net = reachableStorage();
        boolean ok = hasUpgrade(ComputerUpgrade.AI)
                ? CraftEngine.craftOnceRecursive(level, net, craftTarget)  // KI-Chip: Vorstufen mitcraften
                : CraftEngine.craftOnce(level, net, craftTarget);
        if (ok) {
            craftRemaining--;
        } else {
            craftRemaining = 0; // Zutaten fehlen / kein Rezept → diesen Auftrag beenden
        }
        if (craftRemaining <= 0) finishCurrent();
        setChanged();
    }

    /** Beendet den aktuellen Auftrag und zieht ggf. den nächsten nach. */
    private void finishCurrent() {
        craftTarget = ItemStack.EMPTY;
        craftRemaining = 0;
        progress = 0;
        pullNext();
    }

    /** Holt den nächsten wartenden Auftrag (falls vorhanden) als aktiven Auftrag. */
    private void pullNext() {
        Job job = queue.poll();
        if (job != null) {
            craftTarget = job.target();
            craftRemaining = job.amount();
            progress = 0;
        }
    }

    /** Nimmt einen Crafting-Auftrag an (vom Terminal). Läuft bereits einer, wird angehängt. */
    public void requestCraft(ItemStack target, int amount) {
        if (target.isEmpty() || amount <= 0) return;
        if (craftTarget.isEmpty()) {
            this.craftTarget = target.copyWithCount(1);
            this.craftRemaining = amount;
            this.progress = 0;
        } else if (queue.size() < MAX_QUEUE) {
            queue.add(new Job(target.copyWithCount(1), amount));
        }
        setChanged();
    }

    public boolean isBusy() { return craftRemaining > 0 || !queue.isEmpty(); }

    /** Anzahl wartender (noch nicht aktiver) Aufträge. */
    public int getQueueLength() { return queue.size(); }

    /** Aggregierter Speicher über alle erreichbaren NAS (multi-hop über Gateways). */
    private NetworkStorage reachableStorage() {
        List<NetworkStorage> nas = new ArrayList<>();
        for (BlockPos p : RoutingManager.reachableStorages(level, worldPosition,
                RoutingManager.adjacentCableColors(level, worldPosition))) {
            if (level.getBlockEntity(p) instanceof NASBlockEntity n) nas.add(n);
        }
        return new AggregateNetworkStorage(nas);
    }

    @Override
    public float calculateStressApplied() {
        float impact = BASE_STRESS;
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (upgrades.getItem(i).getItem() instanceof ComputerUpgradeItem u) {
                impact += u.getUpgrade().stressCost();
            }
        }
        this.lastStressApplied = impact;
        return impact;
    }

    /** Läuft der Computer (dreht sich, nicht überlastet)? */
    public boolean isPowered() {
        return Math.abs(getSpeed()) > 0 && !isOverStressed();
    }

    /** Ist ein bestimmtes Upgrade gesteckt? */
    public boolean hasUpgrade(ComputerUpgrade type) {
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (upgrades.getItem(i).getItem() instanceof ComputerUpgradeItem u && u.getUpgrade() == type) return true;
        }
        return false;
    }

    public SimpleContainer getUpgrades() { return upgrades; }

    public int getStressImpact() { return (int) calculateStressApplied(); }
    public int getRpm() { return (int) Math.abs(getSpeed()); }

    public void dropUpgrades(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, upgrades);
    }

    // === Menu ===

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.frogportnetworks.computer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ComputerMenu(containerId, playerInv, this);
    }

    // === Persistenz (SmartBlockEntity write/read) ===

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("upgrades", upgrades.createTag(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("upgrades")) {
            upgrades.fromTag(tag.getList("upgrades", Tag.TAG_COMPOUND), registries);
        }
    }
}
