package com.herrderlocken.frogportnetworks.network;

import java.util.Arrays;

/**
 * IPAddress — repräsentiert eine IPv4-Adresse als 4 Oktette.
 *
 * Immutable-Klasse: einmal erstellt, kann die IP nicht geändert werden.
 * Das verhindert Bugs wo verschiedene Stellen im Code versehentlich
 * dieselbe IP-Instanz modifizieren. Will man eine neue IP → neues Objekt.
 *
 * Implementiert equals() und hashCode() damit IPs als HashMap-Keys
 * funktionieren (wichtig für NetworkManager.deviceRegistry).
 */
public class IPAddress {

    private final int[] octets = new int[4];

    public IPAddress(int a, int b, int c, int d) {
        octets[0] = clamp(a);
        octets[1] = clamp(b);
        octets[2] = clamp(c);
        octets[3] = clamp(d);
    }

    /** Stellt sicher dass jedes Oktett im gültigen Bereich 0-255 liegt. */
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public int getOctet(int index) { return octets[index]; }

    /**
     * Prüft ob diese IP und eine andere im selben Subnetz liegen.
     *
     * Funktioniert über bitweises AND: die Netzwerkteile beider IPs
     * werden mit der Maske extrahiert und verglichen.
     *
     * Beispiel: 10.0.0.5 und 10.0.0.200 mit Maske 255.255.255.0
     *   10.0.0.5   AND 255.255.255.0 = 10.0.0.0
     *   10.0.0.200 AND 255.255.255.0 = 10.0.0.0
     *   → gleiches Netzwerk!
     */
    public boolean isInSameSubnet(IPAddress other, SubnetMask mask) {
        for (int i = 0; i < 4; i++) {
            if ((octets[i] & mask.getOctet(i)) != (other.octets[i] & mask.getOctet(i)))
                return false;
        }
        return true;
    }

    /** Parst einen String wie "10.0.0.1" zurück zu einem IPAddress-Objekt. */
    public static IPAddress parse(String s) {
        String[] parts = s.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid IP: " + s);
        return new IPAddress(
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IPAddress other)) return false;
        return Arrays.equals(octets, other.octets);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(octets);
    }
}
