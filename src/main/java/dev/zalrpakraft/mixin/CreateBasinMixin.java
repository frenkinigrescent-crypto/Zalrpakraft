package dev.zalrpakraft.mixin;

import dev.zalrpakraft.tree.GateHooks;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Create: смешивание и прессование в чаше ищут рецепты своим кэшем — проверяем результат здесь. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity", remap = false)
public abstract class CreateBasinMixin {
    @Inject(method = "matchBasinRecipe", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void zalepacraft$gateBasin(Recipe<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        if (recipe != null && !GateHooks.machineAllowsRecipe(recipe)) cir.setReturnValue(false);
    }
}
