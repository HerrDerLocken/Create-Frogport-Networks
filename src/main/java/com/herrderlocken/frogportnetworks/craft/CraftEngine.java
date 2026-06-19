package com.herrderlocken.frogportnetworks.craft;

import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import com.herrderlocken.frogportnetworks.storage.NetworkStorage;
import com.herrderlocken.frogportnetworks.storage.StorageSnapshot;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CraftEngine — Auto-Crafting über das Netz. Vereint Vanilla-Crafting, Vanilla-Schmelzen/Räuchern
 * (Ventilator-Upgrades) und Create-Prozessrezepte (Mixer/Presse/Deployer/Mühle/Crushing/Haunting/
 * Splashing) in einer gemeinsamen {@link Production}-Abstraktion. Welche Create-Prozesse erlaubt sind,
 * gibt das {@code ComputerBlockEntity} über seine Upgrades als {@code Set<Process>} vor. Mit KI-Chip
 * werden fehlende Vorstufen rekursiv mitgecraftet.
 *
 * <p>Hinweise/Grenzen: Rezepte mit Flüssigkeits-Zutaten werden übersprungen (noch keine Fluids im Netz);
 * Hitze-Bedingungen (geheiztes Becken) werden ignoriert; Nebenprodukte (Chance &lt; 1) werden ausgewürfelt.
 */
public final class CraftEngine {

    private CraftEngine() {}

    /** Create-/Vanilla-Prozesse, die ein Upgrade freischalten kann. */
    public enum Process { MIXING, PRESSING, DEPLOYING, MILLING, HAUNTING, SPLASHING, SMELTING, SMOKING }

    public static final int MAX_DEPTH = 8;
    public static final int MAX_STEPS = 256;
    /** Maximale Anzahl craftbarer Items, die der Index liefert (Paketgröße begrenzen). */
    public static final int MAX_CRAFTABLES = 256;

    /**
     * Ein Herstellungsweg für ein Item: feste Zutaten (je 1 Stück), ein garantiertes Ergebnis
     * (Anzahl = Ausbeute) und optionale Nebenprodukte (werden beim Ausführen ausgewürfelt).
     */
    private record Production(List<Ingredient> inputs, ItemStack result, List<ProcessingOutput> extras) {}

    // === Öffentliche Einstiegspunkte ===

    /** Versucht GENAU EIN Crafting des Zielitems (direkt, ohne Vorstufen). */
    public static boolean craftOnce(Level level, NetworkStorage storage, ItemStack target, Set<Process> enabled) {
        if (target.isEmpty()) return false;
        for (Production prod : productionsFor(level, target.getItem(), enabled)) {
            if (executeProduction(level, storage, prod)) return true;
        }
        return false;
    }

    /** Rekursives Crafting (KI-Chip): craftet bei Bedarf erst die fehlenden Vorstufen. */
    public static boolean craftOnceRecursive(Level level, NetworkStorage storage, ItemStack target, Set<Process> enabled) {
        if (target.isEmpty()) return false;
        Sim sim = Sim.of(storage.snapshot());
        List<Production> steps = new ArrayList<>();
        if (!planProduce(level, sim, target.getItem(), 0, new HashSet<>(), steps, enabled)) return false;
        if (steps.isEmpty() || steps.size() > MAX_STEPS) return false;
        for (Production prod : steps) {
            if (!executeProduction(level, storage, prod)) return false; // sollte nach Plan nicht passieren
        }
        return true;
    }

    /** Item-Typen, die im erreichbaren Netz GERADE direkt herstellbar sind (alle Zutaten vorhanden). */
    public static List<ItemStack> craftableResults(Level level, NetworkStorage storage, Set<Process> enabled) {
        StorageSnapshot snap = storage.snapshot();
        List<ItemStack> out = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        for (Production prod : allProductions(level, enabled)) {
            Item item = prod.result().getItem();
            if (seen.contains(item)) continue;
            if (planInputs(prod.inputs(), snap) != null) {
                out.add(prod.result().copy());
                seen.add(item);
                if (out.size() >= MAX_CRAFTABLES) break;
            }
        }
        return out;
    }

    /** Wie {@link #craftableResults} aber REKURSIV (KI-Chip): inkl. herstellbarer Vorstufen. */
    public static List<ItemStack> craftableResultsRecursive(Level level, NetworkStorage storage, Set<Process> enabled) {
        StorageSnapshot snap = storage.snapshot();
        List<DiskEntry> stock = snap.entries();
        Map<Item, List<Production>> byResult = new HashMap<>();
        for (Production prod : allProductions(level, enabled)) {
            byResult.computeIfAbsent(prod.result().getItem(), k -> new ArrayList<>()).add(prod);
        }
        Map<Item, Byte> memo = new HashMap<>();
        Map<Item, Production> chosen = new HashMap<>();
        List<ItemStack> out = new ArrayList<>();
        for (Item item : byResult.keySet()) {
            if (buildableItem(item, stock, byResult, memo, chosen, 0)) {
                Production p = chosen.getOrDefault(item, byResult.get(item).get(0));
                out.add(p.result().copy());
                if (out.size() >= MAX_CRAFTABLES) break;
            }
        }
        return out;
    }

    /**
     * Aufschlüsselung eines Crafts: {@code ok} = herstellbar. Bei ok: {@code consumed} = netto aus dem
     * Netz entnommene Rohstoffe, {@code crafted} = unterwegs hergestellte Vorstufen. Bei NICHT machbar:
     * {@code consumed} = Zutaten des nächstbesten Rezepts, {@code missing} = davon nicht vorrätige.
     */
    public record Plan(boolean ok, List<ItemStack> consumed, List<ItemStack> crafted,
                       List<ItemStack> missing, int maxCrafts) {}

    /** Plant (ohne Ausführung) was das Craften EINES {@code target} verbrauchen/herstellen würde. */
    public static Plan planConsumption(Level level, NetworkStorage storage, ItemStack target,
                                       Set<Process> enabled, boolean recursive) {
        List<ItemStack> none = List.of();
        if (target.isEmpty()) return new Plan(false, none, none, none, 0);
        StorageSnapshot snap = storage.snapshot();
        Sim sim = Sim.of(snap);
        int origSize = sim.protos.size();
        long[] orig = new long[origSize];
        for (int i = 0; i < origSize; i++) orig[i] = sim.counts.get(i);

        List<Production> steps = new ArrayList<>();
        boolean ok = recursive
                ? planProduce(level, sim, target.getItem(), 0, new HashSet<>(), steps, enabled)
                : planSingle(level, sim, target.getItem(), enabled, steps);

        if (ok) {
            List<ItemStack> consumed = new ArrayList<>();
            long maxCrafts = Long.MAX_VALUE;
            for (int i = 0; i < origSize; i++) {
                long delta = orig[i] - sim.counts.get(i);
                if (delta > 0) {
                    consumed.add(sim.protos.get(i).copyWithCount((int) Math.min(delta, 99999)));
                    maxCrafts = Math.min(maxCrafts, orig[i] / delta); // wie oft reicht der Vorrat dafür
                }
            }
            int maxC = consumed.isEmpty() ? 9999 : (int) Math.min(maxCrafts, 9999);
            Map<Item, Integer> tally = new java.util.LinkedHashMap<>();
            for (Production p : steps) tally.merge(p.result().getItem(), p.result().getCount(), Integer::sum);
            tally.remove(target.getItem());
            List<ItemStack> crafted = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : tally.entrySet()) crafted.add(new ItemStack(e.getKey(), e.getValue()));
            return new Plan(true, consumed, crafted, none, Math.max(1, maxC));
        }

        // Nicht (komplett) machbar → nächstbestes Rezept anzeigen (wenigste fehlende Zutaten).
        List<Production> prods = productionsFor(level, target.getItem(), enabled);
        List<ItemStack> bestNeeded = none, bestMissing = none;
        int bestMiss = Integer.MAX_VALUE;
        for (Production prod : prods) {
            List<ItemStack> needed = new ArrayList<>();
            List<ItemStack> missing = new ArrayList<>();
            classifyInputs(prod.inputs(), snap, needed, missing);
            if (missing.size() < bestMiss) {
                bestMiss = missing.size();
                bestNeeded = needed;
                bestMissing = missing;
                if (bestMiss == 0) break;
            }
        }
        return new Plan(false, bestNeeded, none, bestMissing, 0);
    }

    /** Aggregiert die Zutaten eines Rezepts (nach Item) und ermittelt, welche nicht vorrätig sind. */
    private static void classifyInputs(List<Ingredient> inputs, StorageSnapshot snap,
                                       List<ItemStack> outNeeded, List<ItemStack> outMissing) {
        List<DiskEntry> entries = snap.entries();
        long[] remaining = new long[entries.size()];
        for (int i = 0; i < entries.size(); i++) remaining[i] = entries.get(i).count();

        Map<Item, Integer> needed = new java.util.LinkedHashMap<>();
        Map<Item, Integer> missing = new java.util.LinkedHashMap<>();
        for (Ingredient ing : inputs) {
            if (ing.isEmpty()) continue;
            int idx = -1;
            for (int i = 0; i < entries.size(); i++) {
                if (remaining[i] >= 1 && ing.test(entries.get(i).item())) { idx = i; break; }
            }
            Item rep;
            if (idx >= 0) { rep = entries.get(idx).item().getItem(); remaining[idx]--; }
            else {
                ItemStack[] opts = ing.getItems();
                if (opts.length == 0 || opts[0].isEmpty()) continue;
                rep = opts[0].getItem();
                missing.merge(rep, 1, Integer::sum);
            }
            needed.merge(rep, 1, Integer::sum);
        }
        for (Map.Entry<Item, Integer> e : needed.entrySet()) outNeeded.add(new ItemStack(e.getKey(), e.getValue()));
        for (Map.Entry<Item, Integer> e : missing.entrySet()) outMissing.add(new ItemStack(e.getKey(), e.getValue()));
    }

    /** Direkter (nicht-rekursiver) Einzelschritt: erstes Rezept, dessen Zutaten alle vorrätig sind. */
    private static boolean planSingle(Level level, Sim sim, Item target, Set<Process> enabled, List<Production> steps) {
        Set<Item> forbidden = Set.of(target); // nicht das Zielitem selbst verbrauchen (kein Netto-Null)
        for (Production prod : productionsFor(level, target, enabled)) {
            Sim work = sim.copy();
            boolean all = true;
            for (Ingredient ing : prod.inputs()) {
                if (ing.isEmpty()) continue;
                if (!work.consumeOne(ing, forbidden)) { all = false; break; }
            }
            if (all) {
                work.add(prod.result());
                sim.replaceWith(work);
                steps.add(prod);
                return true;
            }
        }
        return false;
    }

    // === Rekursionsplanung (auf Snapshot-Simulation) ===

    private static boolean planProduce(Level level, Sim sim, Item target, int depth,
                                       Set<Item> visiting, List<Production> steps, Set<Process> enabled) {
        if (depth > MAX_DEPTH) return false;
        if (!visiting.add(target)) return false; // Zyklus (A braucht B braucht A)
        try {
            List<Production> prods = productionsFor(level, target, enabled);
            // Durchgang 1: Rezept, dessen Zutaten ALLE vorrätig sind (keine Vorstufe nötig) — bevorzugt.
            for (Production prod : prods) {
                if (tryConsumeDirect(sim, prod, steps, visiting)) return true;
            }
            // Durchgang 2: erlaube das Mitcraften von Vorstufen.
            for (Production prod : prods) {
                if (planRecipe(level, sim, prod, depth, visiting, steps, enabled)) return true;
            }
            return false;
        } finally {
            visiting.remove(target);
        }
    }

    /** Wendet ein Rezept an, wenn alle Zutaten direkt im Sim vorrätig sind (ohne Rekursion). */
    private static boolean tryConsumeDirect(Sim sim, Production prod, List<Production> steps, Set<Item> visiting) {
        Sim work = sim.copy();
        for (Ingredient ing : prod.inputs()) {
            if (ing.isEmpty()) continue;
            if (!work.consumeOne(ing, visiting)) return false;
        }
        work.add(prod.result());
        sim.replaceWith(work);
        steps.add(prod);
        return true;
    }

    private static boolean planRecipe(Level level, Sim sim, Production prod, int depth,
                                      Set<Item> visiting, List<Production> steps, Set<Process> enabled) {
        Sim work = sim.copy();
        List<Production> local = new ArrayList<>();
        for (Ingredient ing : prod.inputs()) {
            if (ing.isEmpty()) continue;
            if (work.consumeOne(ing, visiting)) continue;  // schon vorrätig (auch frisch gecraftet)
            Item candidate = craftableCandidate(level, ing, visiting, enabled);
            if (candidate == null) return false;
            if (!planProduce(level, work, candidate, depth + 1, visiting, local, enabled)) return false;
            if (!work.consumeOne(ing, visiting)) return false;
        }
        work.add(prod.result());
        sim.replaceWith(work);
        steps.addAll(local);
        steps.add(prod);
        return true;
    }

    /** Wählt für eine Zutat ein Item, das (nicht im Zyklus liegt und) selbst herstellbar ist. */
    private static Item craftableCandidate(Level level, Ingredient ing, Set<Item> visiting, Set<Process> enabled) {
        for (ItemStack opt : ing.getItems()) {
            if (opt.isEmpty() || visiting.contains(opt.getItem())) continue;
            if (!productionsFor(level, opt.getItem(), enabled).isEmpty()) return opt.getItem();
        }
        return null;
    }

    // === Craftbar-Index (rekursiv, memoisiert, präsenzbasiert) ===

    // memo-Zustände: 1=in Arbeit (Zyklus), 2=ja, 3=nein
    private static boolean buildableItem(Item item, List<DiskEntry> stock, Map<Item, List<Production>> byResult,
                                         Map<Item, Byte> memo, Map<Item, Production> chosen, int depth) {
        Byte st = memo.get(item);
        if (st != null) return st == 2;
        if (depth > MAX_DEPTH) return false;
        memo.put(item, (byte) 1);
        boolean result = false;
        List<Production> prods = byResult.get(item);
        if (prods != null) {
            // Durchgang 1: Rezept mit allen Zutaten direkt vorrätig (so wie der echte Craft wählt →
            // gleiche Ausbeute-Anzeige). Sonst würde z.B. bei copper_ingot das Block→9-Rezept gewählt.
            for (Production prod : prods) {
                if (allInStock(prod, stock)) { result = true; chosen.put(item, prod); break; }
            }
            if (!result) {
                // Durchgang 2: rekursiv (Zutaten ggf. selbst herstellbar).
                outer:
                for (Production prod : prods) {
                    for (Ingredient ing : prod.inputs()) {
                        if (ing.isEmpty()) continue;
                        if (!buildableIngredient(ing, stock, byResult, memo, chosen, depth)) continue outer;
                    }
                    result = true;
                    chosen.put(item, prod);
                    break;
                }
            }
        }
        memo.put(item, (byte) (result ? 2 : 3));
        return result;
    }

    /** Sind alle Zutaten eines Rezepts direkt im Bestand vorhanden (präsenzbasiert)? */
    private static boolean allInStock(Production prod, List<DiskEntry> stock) {
        for (Ingredient ing : prod.inputs()) {
            if (ing.isEmpty()) continue;
            boolean found = false;
            for (DiskEntry e : stock) {
                if (e.count() >= 1 && ing.test(e.item())) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean buildableIngredient(Ingredient ing, List<DiskEntry> stock, Map<Item, List<Production>> byResult,
                                               Map<Item, Byte> memo, Map<Item, Production> chosen, int depth) {
        for (DiskEntry e : stock) {
            if (e.count() >= 1 && ing.test(e.item())) return true;
        }
        for (ItemStack opt : ing.getItems()) {
            if (opt.isEmpty()) continue;
            if (buildableItem(opt.getItem(), stock, byResult, memo, chosen, depth + 1)) return true;
        }
        return false;
    }

    // === Herstellungswege sammeln ===

    /** Alle Herstellungswege für ein bestimmtes Ziel-Item. */
    private static List<Production> productionsFor(Level level, Item target, Set<Process> enabled) {
        List<Production> out = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> h : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack r = h.value().getResultItem(level.registryAccess());
            if (!r.isEmpty() && r.getItem() == target) {
                out.add(new Production(nonEmpty(h.value().getIngredients()), r.copy(), List.of()));
            }
        }
        if (enabled.contains(Process.SMELTING)) {
            addCooking(level, RecipeType.SMELTING, target, out);
            addCooking(level, RecipeType.BLASTING, target, out); // Hochofen-Rezepte (z.B. Crushed Ores → Barren)
        }
        if (enabled.contains(Process.SMOKING)) addCooking(level, RecipeType.SMOKING, target, out);
        if (enabled.contains(Process.MIXING)) addProcessing(level, AllRecipeTypes.MIXING, target, out);
        if (enabled.contains(Process.PRESSING)) addProcessing(level, AllRecipeTypes.PRESSING, target, out);
        if (enabled.contains(Process.DEPLOYING)) addProcessing(level, AllRecipeTypes.DEPLOYING, target, out);
        if (enabled.contains(Process.MILLING)) {
            addProcessing(level, AllRecipeTypes.MILLING, target, out);
            addProcessing(level, AllRecipeTypes.CRUSHING, target, out);
        }
        if (enabled.contains(Process.HAUNTING)) addProcessing(level, AllRecipeTypes.HAUNTING, target, out);
        if (enabled.contains(Process.SPLASHING)) addProcessing(level, AllRecipeTypes.SPLASHING, target, out);
        return out;
    }

    /** ALLE Herstellungswege (über alle Items) — für den Craftbar-Index. */
    private static List<Production> allProductions(Level level, Set<Process> enabled) {
        List<Production> out = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> h : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack r = h.value().getResultItem(level.registryAccess());
            if (!r.isEmpty()) out.add(new Production(nonEmpty(h.value().getIngredients()), r.copy(), List.of()));
        }
        if (enabled.contains(Process.SMELTING)) {
            addCookingAll(level, RecipeType.SMELTING, out);
            addCookingAll(level, RecipeType.BLASTING, out);
        }
        if (enabled.contains(Process.SMOKING)) addCookingAll(level, RecipeType.SMOKING, out);
        if (enabled.contains(Process.MIXING)) addProcessingAll(level, AllRecipeTypes.MIXING, out);
        if (enabled.contains(Process.PRESSING)) addProcessingAll(level, AllRecipeTypes.PRESSING, out);
        if (enabled.contains(Process.DEPLOYING)) addProcessingAll(level, AllRecipeTypes.DEPLOYING, out);
        if (enabled.contains(Process.MILLING)) {
            addProcessingAll(level, AllRecipeTypes.MILLING, out);
            addProcessingAll(level, AllRecipeTypes.CRUSHING, out);
        }
        if (enabled.contains(Process.HAUNTING)) addProcessingAll(level, AllRecipeTypes.HAUNTING, out);
        if (enabled.contains(Process.SPLASHING)) addProcessingAll(level, AllRecipeTypes.SPLASHING, out);
        return out;
    }

    private static void addCooking(Level level, RecipeType<? extends AbstractCookingRecipe> type, Item target, List<Production> out) {
        for (RecipeHolder<? extends AbstractCookingRecipe> h : level.getRecipeManager().getAllRecipesFor(type)) {
            ItemStack r = h.value().getResultItem(level.registryAccess());
            if (!r.isEmpty() && r.getItem() == target) {
                out.add(new Production(nonEmpty(h.value().getIngredients()), r.copy(), List.of()));
            }
        }
    }

    private static void addCookingAll(Level level, RecipeType<? extends AbstractCookingRecipe> type, List<Production> out) {
        for (RecipeHolder<? extends AbstractCookingRecipe> h : level.getRecipeManager().getAllRecipesFor(type)) {
            ItemStack r = h.value().getResultItem(level.registryAccess());
            if (!r.isEmpty()) out.add(new Production(nonEmpty(h.value().getIngredients()), r.copy(), List.of()));
        }
    }

    private static void addProcessing(Level level, IRecipeTypeInfo info, Item target, List<Production> out) {
        for (RecipeHolder<?> h : processingRecipes(level, info)) {
            if (!(h.value() instanceof ProcessingRecipe<?, ?> pr) || !pr.getFluidIngredients().isEmpty()) continue;
            List<ProcessingOutput> results = pr.getRollableResults();
            for (int i = 0; i < results.size(); i++) {
                ProcessingOutput po = results.get(i);
                if (po.getChance() >= 1f && !po.getStack().isEmpty() && po.getStack().getItem() == target) {
                    out.add(toProduction(pr, results, i));
                    break;
                }
            }
        }
    }

    private static void addProcessingAll(Level level, IRecipeTypeInfo info, List<Production> out) {
        for (RecipeHolder<?> h : processingRecipes(level, info)) {
            if (!(h.value() instanceof ProcessingRecipe<?, ?> pr) || !pr.getFluidIngredients().isEmpty()) continue;
            List<ProcessingOutput> results = pr.getRollableResults();
            for (int i = 0; i < results.size(); i++) {
                ProcessingOutput po = results.get(i);
                if (po.getChance() >= 1f && !po.getStack().isEmpty()) out.add(toProduction(pr, results, i));
            }
        }
    }

    private static Production toProduction(ProcessingRecipe<?, ?> pr, List<ProcessingOutput> results, int primaryIdx) {
        List<ProcessingOutput> extras = new ArrayList<>(results);
        ProcessingOutput primary = extras.remove(primaryIdx);
        return new Production(nonEmpty(pr.getIngredients()), primary.getStack(), extras);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<?>> processingRecipes(Level level, IRecipeTypeInfo info) {
        return (List) level.getRecipeManager().getAllRecipesFor((RecipeType) info.getType());
    }

    // === Ausführung / Planung ===

    private static boolean executeProduction(Level level, NetworkStorage storage, Production prod) {
        List<ItemStack> plan = planInputs(prod.inputs(), storage.snapshot());
        if (plan == null) return false;
        for (ItemStack proto : plan) {
            if (storage.extract(proto, 1, false) < 1) return false; // race → abbrechen
        }
        ItemStack res = prod.result().copy();
        storage.insert(res, res.getCount(), false);
        if (!prod.extras().isEmpty()) {
            RandomSource rng = level.getRandom();
            for (ProcessingOutput po : prod.extras()) {
                ItemStack rolled = po.rollOutput(rng);
                if (!rolled.isEmpty()) storage.insert(rolled, rolled.getCount(), false);
            }
        }
        return true;
    }

    /** Reserviert pro Zutat genau ein passendes Item aus dem Snapshot (ohne Mehrfachvergabe). */
    private static List<ItemStack> planInputs(List<Ingredient> inputs, StorageSnapshot snap) {
        List<DiskEntry> entries = snap.entries();
        long[] remaining = new long[entries.size()];
        for (int i = 0; i < entries.size(); i++) remaining[i] = entries.get(i).count();

        List<ItemStack> plan = new ArrayList<>();
        for (Ingredient ing : inputs) {
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

    private static List<Ingredient> nonEmpty(List<Ingredient> ings) {
        List<Ingredient> out = new ArrayList<>(ings.size());
        for (Ingredient ing : ings) if (!ing.isEmpty()) out.add(ing);
        return out;
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

        boolean consumeOne(Ingredient ing, Set<Item> forbidden) {
            for (int i = 0; i < protos.size(); i++) {
                if (counts.get(i) >= 1 && !forbidden.contains(protos.get(i).getItem()) && ing.test(protos.get(i))) {
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
