package com.herrderlocken.frogportnetworks.item;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.block.NetworkCableBlock;
import com.herrderlocken.frogportnetworks.blockentity.NetworkCableBlockEntity;
import com.herrderlocken.frogportnetworks.network.CableType;
import com.herrderlocken.frogportnetworks.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * CableBlockItem — platziert ein Kabel und merkt sich seinen Typ (Kupfer/Gold/Glasfaser).
 *
 * <p>Standard: platzierte Stränge bekommen automatisch die nächste freie Farbe.
 * <p>Tunen: Shift+Rechtsklick auf einen vorhandenen Strang stellt das Kabel-Item auf
 * dessen Farbe ein ({@link ModDataComponents#TUNED_COLOR}). Danach platzierte Stränge
 * nutzen diese Farbe — so verlängert man eine Strecke gezielt (verbindet farbgleich,
 * unabhängig von der Platzierungsreihenfolge).
 */
public class CableBlockItem extends BlockItem {

    private final CableType cableType;

    public CableBlockItem(Block block, Properties properties, CableType cableType) {
        super(block, properties);
        this.cableType = cableType;
    }

    public CableType getCableType() {
        return cableType;
    }

    public static DyeColor getTunedColor(ItemStack stack) {
        return stack.get(ModDataComponents.TUNED_COLOR.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        Component base = Component.translatable("item." + CreateFrogportNetworks.MODID + "."
                + cableType.getSerializedName() + "_cable");
        DyeColor tuned = getTunedColor(stack);
        if (tuned == null) return base;
        return Component.empty()
                .append(base)
                .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("color.minecraft." + tuned.getSerializedName())
                        .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        Direction attachFace = ctx.getClickedFace().getOpposite();

        // Shift+Rechtsklick auf vorhandenen Strang → Item auf dessen Farbe tunen.
        if (ctx.isSecondaryUseActive()
                && level.getBlockEntity(clicked) instanceof NetworkCableBlockEntity be && !be.isEmpty()) {
            DyeColor picked = NetworkCableBlock.pickStrandAt(be,
                    ctx.getClickLocation().subtract(clicked.getX(), clicked.getY(), clicked.getZ()));
            if (picked != null) {
                if (!level.isClientSide) stack.set(ModDataComponents.TUNED_COLOR.get(), picked);
                level.playSound(ctx.getPlayer(), clicked, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.7f, 1.5f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // Auf vorhandenes Kabel geklickt → weiteren Strang im selben Block ergänzen.
        if (level.getBlockEntity(clicked) instanceof NetworkCableBlockEntity be) {
            DyeColor color = chooseColor(be, stack);
            if (color != null) {
                Direction face = be.isEmpty() ? attachFace : be.getCommonAttachFace();
                if (!level.isClientSide) be.addSegment(color, cableType, face);
                consume(ctx, stack);
                playPlaceSound(ctx, clicked);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            // Block voll / Wunschfarbe schon vorhanden → normal weiter
        }

        BlockPos placePos = new BlockPlaceContext(ctx).getClickedPos();
        InteractionResult result = super.useOn(ctx);
        if (result.consumesAction() && !level.isClientSide
                && level.getBlockEntity(placePos) instanceof NetworkCableBlockEntity be && be.isEmpty()) {
            DyeColor color = chooseColor(be, stack);
            if (color != null) be.addSegment(color, cableType, attachFace);
        }
        return result;
    }

    /** Wunschfarbe (getunt) falls im Block noch frei, sonst die nächste freie Farbe. */
    private static DyeColor chooseColor(NetworkCableBlockEntity be, ItemStack stack) {
        DyeColor tuned = getTunedColor(stack);
        if (tuned != null) {
            return be.hasSegment(tuned) ? null : tuned;
        }
        return be.nextFreeColor();
    }

    private static void consume(UseOnContext ctx, ItemStack stack) {
        Player player = ctx.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private static void playPlaceSound(UseOnContext ctx, BlockPos pos) {
        ctx.getLevel().playSound(ctx.getPlayer(), pos,
                SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
    }
}
