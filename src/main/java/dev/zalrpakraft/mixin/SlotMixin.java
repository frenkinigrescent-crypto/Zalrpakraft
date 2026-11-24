package dev.zalrpakraft.mixin;

import dev.zalrpakraft.tree.GateHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Верстак, сетка 2×2 и кузня Lookin' Sharp: закрытый предмет нельзя забрать из слота результата (ингредиенты остаются на месте). */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void zalepacraft$gatePickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        boolean result = self instanceof ResultSlot
                || (self.container instanceof net.minecraft.world.inventory.ResultContainer && self.getClass().getName().startsWith("earth.terrarium.lookinsharp."));
        if (result && !GateHooks.playerAllows(player, self.getItem())) cir.setReturnValue(false);
    }
}
