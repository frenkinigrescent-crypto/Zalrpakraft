package dev.zalrpakraft.mixin;

import dev.zalrpakraft.tree.GateHooks;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Create: последовательная сборка (точный механизм и т. п.) — проверяем конечный результат. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe", remap = false)
public abstract class CreateSequencedMixin {
    @Inject(method = "appliesTo", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void zalepacraft$gateSequenced(CallbackInfoReturnable<Boolean> cir) {
        if (!GateHooks.machineAllowsRecipe((Recipe<?>) (Object) this)) cir.setReturnValue(false);
    }
}
