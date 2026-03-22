package xyz.justys.ec.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.justys.ec.ModConfig;

@Mixin(targets = "net.minecraft.entity.mob.EndermanEntity$PickUpBlockGoal")
public abstract class EndermanPickupGoalMixin {
    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void justys$blockPickup(CallbackInfoReturnable<Boolean> cir) {
        if (ModConfig.current().isAntiEndermanGrief()) {
            cir.setReturnValue(false);
        }
    }
}
