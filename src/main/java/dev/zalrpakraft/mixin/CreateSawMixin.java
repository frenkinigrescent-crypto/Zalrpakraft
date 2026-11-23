package dev.zalrpakraft.mixin;

import dev.zalrpakraft.tree.GateHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Create: механическая пила. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.saw.SawBlockEntity", remap = false)
public abstract class CreateSawMixin {
    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void zalepacraft$gateSaw(CallbackInfoReturnable<List<?>> cir) {
        List<?> l = cir.getReturnValue();
        if (l == null || l.isEmpty()) return;
        List<?> f = GateHooks.filterAnyServer(l);
        if (f != l) cir.setReturnValue(f);
    }
}
