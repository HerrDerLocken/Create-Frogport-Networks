package com.herrderlocken.frogportnetworks.compat.jei;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.screen.TerminalScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Optionale JEI-Integration für das Netzwerk-Terminal:
 * <ul>
 *   <li><b>A</b> – Items im Browser sind für JEI klickbar (R = Rezept, U = Verwendung).</li>
 *   <li><b>B</b> – die JEI-Suchleiste ist mit der Terminal-Suche synchronisiert
 *       (Steuerung in {@code TerminalScreen} über {@link JeiBridge}).</li>
 *   <li><b>C</b> – ein aus JEI auf den Browser gezogenes Item wird in die Craft-Queue
 *       eingereiht.</li>
 * </ul>
 */
@JeiPlugin
public class FrogportJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        IIngredientManager im = registration.getJeiHelpers().getIngredientManager();

        // A: klickbare Zutaten – meldet das Item unter dem Cursor an JEI (R/U-Lookup).
        registration.addGuiContainerHandler(TerminalScreen.class, new IGuiContainerHandler<TerminalScreen>() {
            @Override
            public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(
                    TerminalScreen screen, double mouseX, double mouseY) {
                ItemStack stack = screen.jeiStackAt(mouseX, mouseY);
                if (stack == null || stack.isEmpty()) return Optional.empty();
                Rect2i cell = screen.jeiCellAt(mouseX, mouseY);
                if (cell == null) cell = new Rect2i((int) mouseX, (int) mouseY, 0, 0);
                Rect2i area = cell;
                return im.createTypedIngredient(VanillaTypes.ITEM_STACK, stack)
                        .<IClickableIngredient<?>>map(t -> new SimpleClickable<>(t, area));
            }
        });

        // C: Ghost-Drag – Item aus JEI auf das Raster ziehen reiht es in die Craft-Queue ein.
        registration.addGhostIngredientHandler(TerminalScreen.class, new CraftGhostHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiBridge.setRuntime(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiBridge.clearRuntime();
    }

    /** Minimaler {@link IClickableIngredient} für A. */
    private record SimpleClickable<T>(ITypedIngredient<T> typed, Rect2i rect)
            implements IClickableIngredient<T> {
        @Override
        public ITypedIngredient<T> getTypedIngredient() { return typed; }

        @Override
        public Rect2i getArea() { return rect; }
    }

    /** Ghost-Handler für C: ganze Browser-Fläche als ein Ziel, das ein Craft anreiht. */
    private static final class CraftGhostHandler implements IGhostIngredientHandler<TerminalScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(TerminalScreen screen, ITypedIngredient<I> ingredient,
                                                   boolean doStart) {
            if (!ingredient.getType().equals(VanillaTypes.ITEM_STACK)) return List.of();
            Rect2i area = screen.jeiBrowserArea();
            Target<I> target = new Target<>() {
                @Override
                public Rect2i getArea() { return area; }

                @Override
                public void accept(I ingredient) {
                    if (ingredient instanceof ItemStack stack) screen.jeiEnqueue(stack);
                }
            };
            return List.of(target);
        }

        @Override
        public void onComplete() {}

        @Override
        public boolean shouldHighlightTargets() { return true; }
    }
}
