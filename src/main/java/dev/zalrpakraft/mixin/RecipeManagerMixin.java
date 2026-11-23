package dev.zalrpakraft.mixin;

import dev.zalrpakraft.tree.GateHooks;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * Общий замок для машин: печи, механизмы Create (пресс, механический крафтер, жернова, вентилятор, дробилка, депло­йер)
 * и большинство машин других модов ищут рецепт через RecipeManager. Если результат ещё никем не открыт — рецепта «нет».
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "getRecipeFor", at = @At("RETURN"), cancellable = true, require = 0)
    private void zalepacraft$gateOne(CallbackInfoReturnable<Optional<?>> cir) {
        Optional<?> r = cir.getReturnValue();
        if (r != null && r.isPresent() && !GateHooks.serverAllows((RecipeManager) (Object) this, r.get()))
            cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "getRecipesFor", at = @At("RETURN"), cancellable = true, require = 0)
    private void zalepacraft$gateMany(CallbackInfoReturnable<List<?>> cir) {
        List<?> l = cir.getReturnValue();
        if (l == null || l.isEmpty()) return;
        List<?> f = GateHooks.filter((RecipeManager) (Object) this, l);
        if (f != l) cir.setReturnValue(f);
    }
}
