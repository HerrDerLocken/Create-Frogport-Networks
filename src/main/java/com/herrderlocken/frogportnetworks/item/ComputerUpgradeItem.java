package com.herrderlocken.frogportnetworks.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Upgrade-Item für den Network Computer (in dessen Upgrade-Slots gesteckt). Trägt einen
 * {@link ComputerUpgrade}-Typ; der Computer wertet die gesteckten Upgrades aus (Funktion + SU).
 */
public class ComputerUpgradeItem extends Item {

    private final ComputerUpgrade upgrade;

    public ComputerUpgradeItem(Properties properties, ComputerUpgrade upgrade) {
        super(properties.stacksTo(16));
        this.upgrade = upgrade;
    }

    public ComputerUpgrade getUpgrade() { return upgrade; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frogportnetworks.upgrade_stress", upgrade.stressCost())
                .withStyle(ChatFormatting.GRAY));
        if (upgrade.isAI()) {
            tooltip.add(Component.translatable("tooltip.frogportnetworks.upgrade_ai").withStyle(ChatFormatting.AQUA));
        }
    }
}
