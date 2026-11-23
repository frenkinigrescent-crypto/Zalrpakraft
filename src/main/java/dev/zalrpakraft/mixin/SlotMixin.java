package dev.zalrpakraft.mixin;

import dev.zalrpakraft.tree.GateHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Верстак и сетка 2×2: закрытый предмет нельзя забрать из слота результата (ингредиенты остаются на месте). */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void zalepacraft$gatePickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ResultSlot slot && !GateHooks.playerAllows(player, slot.getItem()))
            cir.setReturnValue(false);
    }
}
