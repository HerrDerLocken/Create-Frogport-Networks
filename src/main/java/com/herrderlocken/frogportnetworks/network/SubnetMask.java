package com.herrderlocken.frogportnetworks.network;

/**
 * SubnetMask — bestimmt welcher Teil einer IP das Netzwerk und welcher das Gerät ist.
 *
 * Beispiel /24 (255.255.255.0):
 *   IP:     10. 0. 0. 42
 *   Maske: 255.255.255. 0
 *   ─────────────────────
 *   Netz:   10. 0. 0.  ← die ersten 24 Bit identifizieren das Netzwerk
 *   Host:           42  ← die letzten 8 Bit identifizieren das Gerät
 *
 * /24 erlaubt 254 Geräte pro Subnetz (0 = Netzwerk-ID, 255 = Broadcast).
 * Für die meisten Minecraft-Basen ist /24 mehr als genug.
 */
public class SubnetMask {

    private final int[] octets = new int[4];

    public SubnetMask(int a, int b, int c, int d) {
        octets[0] = a;
        octets[1] = b;
        octets[2] = c;
        octets[3] = d;
    }

    public int getOctet(int index) { return octets[index]; }

    /**
     * Erstellt eine Maske aus CIDR-Notation.
     *
     * CIDR (Classless Inter-Domain Routing) gibt die Anzahl der Netzwerk-Bits an:
     *   /24 → 11111111.11111111.11111111.00000000 → 255.255.255.0
     *   /16 → 11111111.11111111.00000000.00000000 → 255.255.0.0
     *   /8  → 11111111.00000000.00000000.00000000 → 255.0.0.0
     *
     * Der Bitshift-Trick: 0xFFFFFFFF << (32 - prefix) erzeugt eine Zahl
     * mit 'prefix' vielen Einsen am Anfang und Nullen am Ende.
     */
    public static SubnetMask fromCIDR(int prefix) {
        if (prefix < 0 || prefix > 32)
            throw new IllegalArgumentException("CIDR prefix must be 0-32, got: " + prefix);
        int mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
        return new SubnetMask(
                (mask >> 24) & 0xFF,
                (mask >> 16) & 0xFF,
                (mask >>  8) & 0xFF,
                (mask      ) & 0xFF
        );
    }

    /** Parst einen String wie "255.255.255.0" zurück zur SubnetMask. */
    public static SubnetMask parse(String s) {
        String[] parts = s.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid subnet mask: " + s);
        return new SubnetMask(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
        );
    }

    @Override
    public String toString() {
        return octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
    }
}
