package com.herrderlocken.frogportnetworks.registry;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * ModDataComponents — typisierte Item-Daten (1.21-Nachfolger von Item-NBT).
 *
 * TUNED_COLOR: Optionale "Wunschfarbe" eines Kabel-Items. Ist sie gesetzt, werden
 * platzierte Stränge in dieser Farbe erzeugt (statt automatisch der nächsten freien)
 * — so lässt sich eine bestehende Strecke gezielt verlängern.
 */
public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateFrogportNetworks.MODID);

    public static final Supplier<DataComponentType<DyeColor>> TUNED_COLOR =
            DATA_COMPONENTS.register("tuned_color",
                    () -> DataComponentType.<DyeColor>builder()
                            .persistent(DyeColor.CODEC)
                            .networkSynchronized(DyeColor.STREAM_CODEC)
                            .build());

    public static void register() {}
}
