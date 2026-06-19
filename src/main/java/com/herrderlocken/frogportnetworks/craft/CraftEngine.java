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

    /** Maximale Rekursionstiefe / Schrittzahl beim rekursiven (KI-)Crafting. */
    public static final int MAX_DEPTH = 8;
    public static final int MAX_STEPS = 256;

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

    /**
     * Rekursives Crafting (KI-Chip): craftet bei Bedarf zuerst fehlende Vorstufen und dann das
     * Zielitem. Plant den kompletten Rezeptbaum auf einer Snapshot-Simulation (kein Rollback nötig)
     * und führt die Schritte danach der Reihe nach auf dem echten Speicher aus. true = 1× gecraftet.
     */
    public static boolean craftOnceRecursive(Level level, NetworkStorage storage, ItemStack target) {
        if (target.isEmpty()) return false;
        Sim sim = Sim.of(storage.snapshot());
        List<RecipeHolder<CraftingRecipe>> steps = new ArrayList<>();
        if (!planProduce(level, sim, target, 0, new java.util.HashSet<>(), steps)) return false;
        if (steps.isEmpty() || steps.size() > MAX_STEPS) return false;
        for (RecipeHolder<CraftingRecipe> holder : steps) {
            if (!tryCraft(level, storage, holder)) return false; // sollte nach Plan nicht passieren
        }
        return true;
    }

    /** Plant die Produktion EINES {@code target} in {@code sim}; hängt die nötigen Schritte an. */
    private static boolean planProduce(Level level, Sim sim, ItemStack target, int depth,
                                       java.util.Set<net.minecraft.world.item.Item> visiting,
                                       List<RecipeHolder<CraftingRecipe>> steps) {
        if (depth > MAX_DEPTH) return false;
        if (!visiting.add(target.getItem())) return false; // Zyklus (A braucht B braucht A)
        try {
            for (RecipeHolder<CraftingRecipe> holder : recipesFor(level, target)) {
                if (planRecipe(level, sim, holder, depth, visiting, steps)) return true;
            }
            return false;
        } finally {
            visiting.remove(target.getItem());
        }
    }

    /** Plant das Anwenden EINES Rezepts in {@code sim} (Vorstufen werden ggf. rekursiv gecraftet). */
    private static boolean planRecipe(Level level, Sim sim, RecipeHolder<CraftingRecipe> holder, int depth,
                                      java.util.Set<net.minecraft.world.item.Item> visiting,
                                      List<RecipeHolder<CraftingRecipe>> steps) {
        Sim work = sim.copy();
        List<RecipeHolder<CraftingRecipe>> local = new ArrayList<>();
        for (Ingredient ing : holder.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            if (work.consumeOne(ing)) continue;          // schon vorrätig (auch frisch gecraftet)
            // fehlt → eine passende, craftbare Vorstufe wählen und herstellen
            ItemStack candidate = craftableCandidate(level, ing, visiting);
            if (candidate == null) return false;
            if (!planProduce(level, work, candidate, depth + 1, visiting, local)) return false;
            if (!work.consumeOne(ing)) return false;     // nach Produktion entnehmen
        }
        work.add(holder.value().getResultItem(level.registryAccess()));
        sim.replaceWith(work);
        steps.addAll(local);
        steps.add(holder);
        return true;
    }

    /** Wählt für eine Zutat ein Item, das (nicht im Zyklus liegt und) selbst ein Rezept hat. */
    private static ItemStack craftableCandidate(Level level, Ingredient ing,
                                                java.util.Set<net.minecraft.world.item.Item> visiting) {
        for (ItemStack opt : ing.getItems()) {
            if (opt.isEmpty() || visiting.contains(opt.getItem())) continue;
            if (!recipesFor(level, opt).isEmpty()) return opt.copyWithCount(1);
        }
        return null;
    }

    /** Alle Crafting-Rezepte, deren Ergebnis {@code target} entspricht. */
    private static List<RecipeHolder<CraftingRecipe>> recipesFor(Level level, ItemStack target) {
        List<RecipeHolder<CraftingRecipe>> out = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack r = holder.value().getResultItem(level.registryAccess());
            if (!r.isEmpty() && ItemStack.isSameItem(r, target)) out.add(holder);
        }
        return out;
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
                out.add(result.copy()); // Anzahl = Rezept-Ausbeute (für die Terminal-Anzeige)
                seen.add(result.getItem());
                if (out.size() >= MAX_CRAFTABLES) break;
            }
        }
        return out;
    }

    /**
     * Wie {@link #craftableResults} aber REKURSIV (KI-Chip): liefert alle Item-Typen, die sich aus
     * dem aktuellen Netz herstellen lassen, wenn fehlende Vorstufen mitgecraftet werden. Präsenz-basiert
     * (ignoriert exakte Mengen) + memoisiert pro Scan, damit der Index bezahlbar bleibt; der eigentliche
     * Craft prüft die Mengen später genau.
     */
    public static List<ItemStack> craftableResultsRecursive(Level level, NetworkStorage storage) {
        StorageSnapshot snap = storage.snapshot();
        List<DiskEntry> stock = snap.entries();
        // Ergebnis-Item → seine Rezepte, einmal pro Scan aufgebaut (sonst O(Items × Rezepte)).
        java.util.Map<net.minecraft.world.item.Item, List<RecipeHolder<CraftingRecipe>>> byResult =
                new java.util.HashMap<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack r = holder.value().getResultItem(level.registryAccess());
            if (!r.isEmpty()) byResult.computeIfAbsent(r.getItem(), k -> new ArrayList<>()).add(holder);
        }
        java.util.Map<net.minecraft.world.item.Item, Byte> memo = new java.util.HashMap<>();
        // Item → tatsächlich gewähltes Rezept (für die korrekte Ausbeute-Anzeige, z.B. Sticks=4).
        java.util.Map<net.minecraft.world.item.Item, RecipeHolder<CraftingRecipe>> chosen = new java.util.HashMap<>();
        List<ItemStack> out = new ArrayList<>();
        for (var entry : byResult.entrySet()) {
            if (buildableItem(level, entry.getKey(), stock, byResult, memo, chosen, 0)) {
                RecipeHolder<CraftingRecipe> holder = chosen.getOrDefault(entry.getKey(), entry.getValue().get(0));
                out.add(holder.value().getResultItem(level.registryAccess()).copy());
                if (out.size() >= MAX_CRAFTABLES) break;
            }
        }
        return out;
    }

    // memo-Zustände: 1=in Arbeit (Zyklus), 2=ja, 3=nein
    private static boolean buildableItem(Level level, net.minecraft.world.item.Item item, List<DiskEntry> stock,
                                         java.util.Map<net.minecraft.world.item.Item, List<RecipeHolder<CraftingRecipe>>> byResult,
                                         java.util.Map<net.minecraft.world.item.Item, Byte> memo,
                                         java.util.Map<net.minecraft.world.item.Item, RecipeHolder<CraftingRecipe>> chosen,
                                         int depth) {
        Byte st = memo.get(item);
        if (st != null) return st == 2;       // in Arbeit (1) → als Zyklus = false
        if (depth > MAX_DEPTH) return false;
        memo.put(item, (byte) 1);
        boolean result = false;
        List<RecipeHolder<CraftingRecipe>> recipes = byResult.get(item);
        if (recipes != null) {
            outer:
            for (RecipeHolder<CraftingRecipe> holder : recipes) {
                for (Ingredient ing : holder.value().getIngredients()) {
                    if (ing.isEmpty()) continue;
                    if (!buildableIngredient(level, ing, stock, byResult, memo, chosen, depth)) continue outer;
                }
                result = true;
                chosen.put(item, holder); // dieses Rezept wird tatsächlich verwendet
                break;
            }
        }
        memo.put(item, (byte) (result ? 2 : 3));
        return result;
    }

    /** Eine Zutat gilt als beschaffbar, wenn sie vorrätig ist ODER selbst (rekursiv) craftbar. */
    private static boolean buildableIngredient(Level level, Ingredient ing, List<DiskEntry> stock,
                                               java.util.Map<net.minecraft.world.item.Item, List<RecipeHolder<CraftingRecipe>>> byResult,
                                               java.util.Map<net.minecraft.world.item.Item, Byte> memo,
                                               java.util.Map<net.minecraft.world.item.Item, RecipeHolder<CraftingRecipe>> chosen,
                                               int depth) {
        for (DiskEntry e : stock) {
            if (e.count() >= 1 && ing.test(e.item())) return true;
        }
        for (ItemStack opt : ing.getItems()) {
            if (opt.isEmpty()) continue;
            if (buildableItem(level, opt.getItem(), stock, byResult, memo, chosen, depth + 1)) return true;
        }
        return false;
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

    /**
     * Veränderbare Speicher-Simulation (Prototyp + Menge) für die Rekursionsplanung — so kann ein
     * fehlgeschlagener Rezeptzweig per {@link #copy()} verworfen werden, ohne echten Speicher zu ändern.
     */
    private static final class Sim {
        final List<ItemStack> protos = new ArrayList<>();
        final List<Long> counts = new ArrayList<>();

        static Sim of(StorageSnapshot snap) {
            Sim s = new Sim();
            for (DiskEntry e : snap.entries()) {
                s.protos.add(e.item().copyWithCount(1));
                s.counts.add(e.count());
            }
            return s;
        }

        Sim copy() {
            Sim s = new Sim();
            s.protos.addAll(protos);
            s.counts.addAll(counts);
            return s;
        }

        void replaceWith(Sim o) {
            protos.clear(); protos.addAll(o.protos);
            counts.clear(); counts.addAll(o.counts);
        }

        boolean consumeOne(Ingredient ing) {
            for (int i = 0; i < protos.size(); i++) {
                if (counts.get(i) >= 1 && ing.test(protos.get(i))) {
                    counts.set(i, counts.get(i) - 1);
                    return true;
                }
            }
            return false;
        }

        void add(ItemStack result) {
            for (int i = 0; i < protos.size(); i++) {
                if (ItemStack.isSameItemSameComponents(protos.get(i), result)) {
                    counts.set(i, counts.get(i) + result.getCount());
                    return;
                }
            }
            protos.add(result.copyWithCount(1));
            counts.add((long) result.getCount());
        }
    }
}
