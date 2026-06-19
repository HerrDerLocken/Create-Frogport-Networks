package com.herrderlocken.frogportnetworks.client;

import com.herrderlocken.frogportnetworks.storage.DiskEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ItemSorting — client-seitige Sortier- und Gruppierlogik für die Terminal-Itemliste.
 * Sortierung (Menge/Name/ID) und Gruppierung nach Mod (Namespace) oder Item-Tag (kuratierte Liste).
 */
public final class ItemSorting {

    private ItemSorting() {}

    public enum Sort {
        COUNT_DESC("Most"),
        COUNT_ASC("Least"),
        NAME("A-Z"),
        ID("ID");

        public final String label;
        Sort(String label) { this.label = label; }
    }

    public enum GroupBy {
        NONE("None"),
        MOD("Mod"),
        TAG("Tag");

        public final String label;
        GroupBy(String label) { this.label = label; }
    }

    /** Kuratierte, geordnete Liste von Item-Tags für die Tag-Gruppierung (Label → Tag). */
    private static final LinkedHashMap<String, TagKey<Item>> CURATED_TAGS = new LinkedHashMap<>();
    static {
        tag("Ingots", "c", "ingots");
        tag("Gems", "c", "gems");
        tag("Nuggets", "c", "nuggets");
        tag("Raw materials", "c", "raw_materials");
        tag("Ores", "c", "ores");
        tag("Dusts", "c", "dusts");
        tag("Storage blocks", "c", "storage_blocks");
        tag("Plates", "c", "plates");
        tag("Gears", "c", "gears");
        tag("Rods", "c", "rods");
        tag("Seeds", "c", "seeds");
        tag("Crops", "c", "crops");
        tag("Foods", "c", "foods");
        tag("Logs", "minecraft", "logs");
        tag("Planks", "minecraft", "planks");
        tag("Wool", "minecraft", "wool");
        tag("Saplings", "minecraft", "saplings");
        tag("Flowers", "minecraft", "flowers");
        tag("Stairs", "minecraft", "stairs");
        tag("Slabs", "minecraft", "slabs");
    }

    private static void tag(String label, String ns, String path) {
        CURATED_TAGS.put(label, TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ns, path)));
    }

    private static Comparator<DiskEntry> comparator(Sort sort) {
        return switch (sort) {
            case COUNT_DESC -> Comparator.comparingLong((DiskEntry e) -> e.count()).reversed()
                    .thenComparing(ItemSorting::name);
            case COUNT_ASC -> Comparator.comparingLong((DiskEntry e) -> e.count())
                    .thenComparing(ItemSorting::name);
            case NAME -> Comparator.comparing(ItemSorting::name);
            case ID -> Comparator.comparing(ItemSorting::id);
        };
    }

    /** Sortiert die Liste (in place) nach dem gewählten Modus. */
    public static void sort(List<DiskEntry> entries, Sort sort) {
        entries.sort(comparator(sort));
    }

    /** Gruppiert die Einträge nach dem Modus; jede Gruppe wird intern sortiert. */
    public static List<StorageBrowser.Group> group(List<DiskEntry> entries, GroupBy by, Sort sort) {
        Map<String, List<DiskEntry>> groups = new LinkedHashMap<>();
        if (by == GroupBy.TAG) {
            // Gruppen in kuratierter Reihenfolge anlegen (auch leere bleiben außen vor unten)
            for (String label : CURATED_TAGS.keySet()) groups.put(label, new ArrayList<>());
        }
        for (DiskEntry e : entries) {
            String key = by == GroupBy.MOD ? modLabel(e.item()) : tagLabel(e.item());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<StorageBrowser.Group> out = new ArrayList<>();
        Comparator<DiskEntry> cmp = comparator(sort);
        if (by == GroupBy.MOD) {
            // Mods nach Gesamtmenge (größte zuerst), dann alphabetisch
            groups.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, List<DiskEntry>>>comparingLong(
                            en -> -en.getValue().stream().mapToLong(DiskEntry::count).sum())
                            .thenComparing(Map.Entry::getKey))
                    .forEach(en -> { en.getValue().sort(cmp); out.add(new StorageBrowser.Group(en.getKey(), en.getValue())); });
        } else {
            // Tag-Gruppen in kuratierter Reihenfolge, "Other" zuletzt
            for (Map.Entry<String, List<DiskEntry>> en : groups.entrySet()) {
                if (en.getValue().isEmpty() || en.getKey().equals("Other")) continue;
                en.getValue().sort(cmp);
                out.add(new StorageBrowser.Group(en.getKey(), en.getValue()));
            }
            List<DiskEntry> other = groups.get("Other");
            if (other != null && !other.isEmpty()) {
                other.sort(cmp);
                out.add(new StorageBrowser.Group("Other", other));
            }
        }
        return out;
    }

    private static String modLabel(ItemStack stack) {
        String ns = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return ns.isEmpty() ? ns : Character.toUpperCase(ns.charAt(0)) + ns.substring(1).replace('_', ' ');
    }

    private static String tagLabel(ItemStack stack) {
        for (Map.Entry<String, TagKey<Item>> en : CURATED_TAGS.entrySet()) {
            if (stack.is(en.getValue())) return en.getKey();
        }
        return "Other";
    }

    private static String name(DiskEntry e) {
        return e.item().getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    private static String id(DiskEntry e) {
        return BuiltInRegistries.ITEM.getKey(e.item().getItem()).toString();
    }
}
