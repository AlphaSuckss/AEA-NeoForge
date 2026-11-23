package dev.alphasucks.aea.compat.appleskin;

import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.power.builtin.ModifyFoodHandler;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.neoforge.common.NeoForge;
import squeek.appleskin.api.event.FoodValuesEvent;

/**
 * AppleSkin compatibility. Only called when AppleSkin is installed; nothing else in AEA refers to this package.
 *
 * <p>Apoli's {@code apoli:modify_food} changes nutrition and saturation when food is eaten, not in the item itself, so
 * AppleSkin's tooltip and hunger bar preview show the unchanged values. AppleSkin asks for a food's values through
 * {@link FoodValuesEvent}; this answers with the same calculation Apoli uses when the food is eaten.
 */
public final class AppleSkinCompat {
    private AppleSkinCompat() {}

    private static boolean failed;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FoodValuesEvent.class, AppleSkinCompat::onFoodValues);
        AEA.LOGGER.info("[AEA] AppleSkin found: it shows apoli:modify_food values.");
    }

    private static void onFoodValues(FoodValuesEvent event) {
        if (failed || event.player == null || event.modifiedFoodProperties == null || event.itemStack.isEmpty()) return;
        try {
            FoodProperties modified = ModifyFoodHandler.modify(event.player, event.itemStack, event.modifiedFoodProperties);
            if (modified != null) event.modifiedFoodProperties = modified;
        } catch (RuntimeException | LinkageError e) {
            // A display hook must never break AppleSkin's tooltip; stop trying after the first failure.
            failed = true;
            AEA.LOGGER.warn("[AEA] AppleSkin compat turned off: {}", e.toString());
        }
    }
}
