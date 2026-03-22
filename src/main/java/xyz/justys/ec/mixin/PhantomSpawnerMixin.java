package xyz.justys.ec.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.spawner.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.justys.ec.ModConfig;

@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerMixin {
    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
    private void justys$disablePhantoms(ServerWorld world, boolean spawnMonsters, boolean spawnAnimals, CallbackInfoReturnable<Integer> cir) {
        if (ModConfig.current().isDisablePhantoms()) {
            cir.setReturnValue(0);
        }
    }
}
