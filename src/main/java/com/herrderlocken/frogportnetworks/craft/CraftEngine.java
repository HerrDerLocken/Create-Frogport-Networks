package com.herrderlocken.frogportnetworks.craft;

import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * CraftEngine — einfaches direktes Auto-Crafting (Vanilla-Crafting-Rezepte). Findet ein
 * Rezept für das Zielitem, prüft ob alle Zutaten im erreichbaren Netz-Speicher liegen, zieht
 * sie ab und legt das Ergebnis zurück ins Netz. Rekursion (Vorstufen) folgt mit dem KI-Chip.
 */
public final class CraftEngine {

    private CraftEngine() {}

    /** Versucht GENAU EIN Crafting des Zielitems aus {@code storage}. true = erfolgreich gecraftet. */
    public static boolean craftOnce(Level level, NetworkStorage storage, ItemStack target) {
        if (target.isEmpty()) return false;
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (result.isEmpty() || !ItemStack.isSameItem(result, target)) continue;
            if (tryCraft(level, storage, holder)) return true;
        }
        return false;
    }

    /** Maximale Anzahl craftbarer Items, die der Index liefert (Paketgröße begrenzen). */
    public static final int MAX_CRAFTABLES = 256;

    /**
     * Alle Item-Typen, für die im erreichbaren Netz GENAU JETZT mindestens ein Rezept
     * direkt craftbar ist (alle Zutaten vorhanden). Für den Terminal-Craftbar-Index.
     */
    public static List<ItemStack> craftableResults(Level level, NetworkStorage storage) {
        StorageSnapshot snap = storage.snapshot();
        List<ItemStack> out = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (result.isEmpty() || seen.contains(result.getItem())) continue;
            if (planIngredients(holder, snap) != null) {
                out.add(result.copyWithCount(1));
                seen.add(result.getItem());
                if (out.size() >= MAX_CRAFTABLES) break;
            }
        }
        return out;
    }

    /** Hat das erreichbare Netz alle Zutaten für (irgend)ein Rezept des Zielitems? */
    public static boolean canCraft(Level level, NetworkStorage storage, ItemStack target) {
        if (target.isEmpty()) return false;
        StorageSnapshot snap = storage.snapshot();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (result.isEmpty() || !ItemStack.isSameItem(result, target)) continue;
            if (planIngredients(holder, snap) != null) return true;
        }
        return false;
    }

    private static boolean tryCraft(Level level, NetworkStorage storage, RecipeHolder<CraftingRecipe> holder) {
        StorageSnapshot snap = storage.snapshot();
        List<ItemStack> plan = planIngredients(holder, snap);
        if (plan == null) return false;

        // Commit: Zutaten abziehen, Ergebnis einlagern
        for (ItemStack proto : plan) {
            if (storage.extract(proto, 1, false) < 1) {
                return false; // sollte nach Plan nicht passieren (race) → abbrechen
            }
        }
        ItemStack result = holder.value().getResultItem(level.registryAccess()).copy();
        storage.insert(result, result.getCount(), false);
        return true;
    }

    /**
     * Plant pro Rezept-Zutat genau ein passendes Item aus dem Snapshot (ohne Mehrfachvergabe).
     * @return Liste der zu entnehmenden Prototypen (je 1 Stück) oder null, wenn etwas fehlt.
     */
    private static List<ItemStack> planIngredients(RecipeHolder<CraftingRecipe> holder, StorageSnapshot snap) {
        List<DiskEntry> entries = snap.entries();
        long[] remaining = new long[entries.size()];
        for (int i = 0; i < entries.size(); i++) remaining[i] = entries.get(i).count();

        List<ItemStack> plan = new ArrayList<>();
        for (Ingredient ing : holder.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            boolean found = false;
            for (int i = 0; i < entries.size(); i++) {
                if (remaining[i] >= 1 && ing.test(entries.get(i).item())) {
                    remaining[i]--;
                    plan.add(entries.get(i).item().copyWithCount(1));
                    found = true;
                    break;
                }
            }
            if (!found) return null;
        }
        return plan;
    }
}
