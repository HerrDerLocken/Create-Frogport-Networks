package com.herrderlocken.frogportnetworks.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * RoutingTable — bestimmt wohin ein Item-Paket als nächstes geschickt wird.
 *
 * Funktioniert wie eine echte Routing-Tabelle: jeder Eintrag sagt
 * "Wenn das Ziel im Netzwerk X liegt, schick es an Next-Hop Y".
 *
 * Verwendet Longest Prefix Match: wenn mehrere Routen passen,
 * wird die spezifischste gewählt (die mit der größten Subnetzmaske).
 * Das ist genau so wie echte Router arbeiten.
 *
 * Beispiel:
 *   Route 1: 10.0.0.0/24 → Next-Hop 10.0.0.1
 *   Route 2: 10.0.0.0/16 → Next-Hop 10.0.1.1
 *   Ziel 10.0.0.50 → matched beide, aber /24 ist spezifischer → Next-Hop 10.0.0.1
 */
public class RoutingTable {

    public record Route(IPAddress network, SubnetMask mask, IPAddress nextHop) {
        /**
         * Zählt die CIDR-Prefix-Länge der Maske.
         * Wird für Longest Prefix Match gebraucht.
         * 255.255.255.0 → 24, 255.255.0.0 → 16
         */
        public int prefixLength() {
            int bits = 0;
            for (int i = 0; i < 4; i++) {
                int octet = mask.getOctet(i);
                while (octet > 0) {
                    bits += (octet & 1);
                    octet >>= 1;
                }
            }
            return bits;
        }
    }

    private final List<Route> routes = new ArrayList<>();

    public void addRoute(IPAddress network, SubnetMask mask, IPAddress nextHop) {
        // Duplikate vermeiden
        routes.removeIf(r -> r.network().equals(network) && r.nextHop().equals(nextHop));
        routes.add(new Route(network, mask, nextHop));
    }

    public void removeRoute(IPAddress network) {
        routes.removeIf(r -> r.network().equals(network));
    }

    /**
     * Findet den besten Next-Hop für eine Ziel-IP.
     *
     * Longest Prefix Match: sortiert passende Routen nach Prefix-Länge absteigend,
     * nimmt die erste (= spezifischste). Wenn keine Route passt → Optional.empty().
     */
    public Optional<IPAddress> resolve(IPAddress destination) {
        return routes.stream()
                .filter(r -> destination.isInSameSubnet(r.network(), r.mask()))
                .max(Comparator.comparingInt(Route::prefixLength))
                .map(Route::nextHop);
    }

    public List<Route> getRoutes() {
        return List.copyOf(routes);
    }

    public void clear() {
        routes.clear();
    }
}
