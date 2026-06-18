package com.herrderlocken.frogportnetworks.item;

import com.herrderlocken.frogportnetworks.registry.ModDataComponents;
import com.herrderlocken.frogportnetworks.storage.DiskContents;
import com.herrderlocken.frogportnetworks.storage.DiskTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * StorageDiskItem — eine Speicher-Disk einer {@link DiskTier}. Der Inhalt liegt als
 * {@link ModDataComponents#DISK_CONTENTS} direkt auf dem ItemStack, sodass die Disk
 * ihre Items beim Transport behält. In einem NAS-Laufwerk stellt sie ihren Speicher
 * dem Netz bereit.
 */
public class StorageDiskItem extends Item {

    private final DiskTier tier;

    public StorageDiskItem(Properties properties, DiskTier tier) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public DiskTier getTier() { return tier; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DiskContents c = stack.getOrDefault(ModDataComponents.DISK_CONTENTS.get(), DiskContents.EMPTY);
        tooltip.add(Component.translatable("tooltip.frogportnetworks.disk_items",
                        fmt(c.totalCount()), fmt(tier.maxItems()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.frogportnetworks.disk_types",
                        c.typeCount(), tier.maxTypes())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fk", n / 1_000.0);
        return Long.toString(n);
    }
}
