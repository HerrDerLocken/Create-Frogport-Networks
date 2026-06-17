package com.herrderlocken.frogportnetworks;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // === Netzwerk ===
    public static final ModConfigSpec.IntValue MAX_NETWORK_SIZE = BUILDER
            .comment("Maximum number of devices per network")
            .defineInRange("maxNetworkSize", 64, 1, 1024);

    public static final ModConfigSpec.IntValue PACKET_DELAY_TICKS = BUILDER
            .comment("Simulated network delay in ticks (0 = instant, 20 = 1 second)")
            .defineInRange("packetDelayTicks", 2, 0, 100);

    // === DHCP ===
    public static final ModConfigSpec.IntValue DHCP_POOL_START = BUILDER
            .comment("Start of DHCP pool (last octet, e.g. 100 = x.x.x.100)")
            .defineInRange("dhcpPoolStart", 100, 2, 254);

    public static final ModConfigSpec.IntValue DHCP_POOL_END = BUILDER
            .comment("End of DHCP pool (last octet, e.g. 200 = x.x.x.200)")
            .defineInRange("dhcpPoolEnd", 200, 2, 254);

    // === Kabel ===
    public static final ModConfigSpec.IntValue CABLE_MAX_LENGTH = BUILDER
            .comment("Maximum cable length in blocks before signal degrades")
            .defineInRange("cableMaxLength", 64, 8, 256);

    // === NAS ===
    public static final ModConfigSpec.IntValue NAS_SLOT_COUNT = BUILDER
            .comment("Number of item slots per NAS block")
            .defineInRange("nasSlotCount", 27, 9, 54);

    static final ModConfigSpec SPEC = BUILDER.build();
}
