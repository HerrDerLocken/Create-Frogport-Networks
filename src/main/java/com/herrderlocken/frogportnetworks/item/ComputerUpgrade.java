package com.herrderlocken.frogportnetworks.item;

/**
 * Upgrade-Typen für den Network Computer. Der KI-Chip schaltet rekursives Crafting frei
 * (und kostet +16 SU); alle anderen schalten je einen Create-Prozess als Crafting-Schritt
 * frei (+2 SU pro Stück). Die Stress-Kosten summiert das {@code ComputerBlockEntity}.
 */
public enum ComputerUpgrade {
    AI("ai_chip", true),            // rekursives Crafting (inkl. Vorstufen)
    MIXING("mixing", false),        // Mixer
    PRESSING("pressing", false),    // Mechanische Presse
    DEPLOYING("deploying", false),  // Deployer
    MILLING("milling", false),      // Mühlstein
    HAUNTING("haunting", false),    // Seelenfeuer-Ventilator
    SMELTING("smelting", false),    // Lava-Ventilator (Schmelzen)
    SMOKING("smoking", false),      // Rauch/Lagerfeuer-Ventilator
    WASHING("washing", false);      // Wasser-Ventilator (Waschen)

    /** SU-Zusatzkosten des KI-Chips. */
    public static final int AI_STRESS = 16;
    /** SU-Zusatzkosten je sonstigem Upgrade. */
    public static final int OTHER_STRESS = 4;

    private final String id;
    private final boolean ai;

    ComputerUpgrade(String id, boolean ai) {
        this.id = id;
        this.ai = ai;
    }

    public String id() { return id; }
    public boolean isAI() { return ai; }
    public int stressCost() { return ai ? AI_STRESS : OTHER_STRESS; }
}
