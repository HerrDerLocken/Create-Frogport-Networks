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

    // Aktueller Crafting-Auftrag (transient; nicht persistiert)
    private ItemStack craftTarget = ItemStack.EMPTY;
    private int craftRemaining = 0;
    private int progress = 0;

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
        if (craftRemaining <= 0 || craftTarget.isEmpty() || !isPowered()) return;

        progress += getRpm();
        if (progress < CRAFT_THRESHOLD) return;
        progress -= CRAFT_THRESHOLD;

        NetworkStorage net = reachableStorage();
        if (CraftEngine.craftOnce(level, net, craftTarget)) {
            craftRemaining--;
        } else {
            craftRemaining = 0; // Zutaten fehlen / kein Rezept → Auftrag beenden
        }
        setChanged();
    }

    /** Nimmt einen Crafting-Auftrag an (vom Terminal). */
    public void requestCraft(ItemStack target, int amount) {
        if (target.isEmpty() || amount <= 0) return;
        this.craftTarget = target.copyWithCount(1);
        this.craftRemaining = amount;
        this.progress = 0;
        setChanged();
    }

    public boolean isBusy() { return craftRemaining > 0; }

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
