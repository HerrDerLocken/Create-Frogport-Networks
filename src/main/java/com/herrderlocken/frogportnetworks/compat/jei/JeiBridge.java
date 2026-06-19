package com.herrderlocken.frogportnetworks.compat.jei;

import mezz.jei.api.runtime.IJeiRuntime;

/**
 * Schmale Brücke zur JEI-Laufzeit. Wird ausschließlich aufgerufen, wenn JEI installiert
 * ist (geschützt durch ein {@code ModList}-Flag in {@code TerminalScreen}), daher entsteht
 * keine harte Abhängigkeit: ohne JEI wird diese Klasse nie geladen.
 *
 * <p>Die öffentlichen Methoden verwenden nur Vanilla-/JDK-Typen, sodass aufrufende
 * Stellen keine JEI-Klassen referenzieren.
 */
public final class JeiBridge {

    private static IJeiRuntime runtime;

    private JeiBridge() {}

    static void setRuntime(IJeiRuntime r) { runtime = r; }

    static void clearRuntime() { runtime = null; }

    /** Aktueller Filtertext der JEI-Zutatenleiste, oder null wenn nicht verfügbar. */
    public static String pullSearch() {
        return runtime == null ? null : runtime.getIngredientFilter().getFilterText();
    }

    /** Setzt den Filtertext der JEI-Zutatenleiste (No-op ohne Laufzeit). */
    public static void pushSearch(String text) {
        if (runtime != null) runtime.getIngredientFilter().setFilterText(text);
    }
}
