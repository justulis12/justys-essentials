package xyz.justys.ec.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.justys.ec.ModConfig;

@Mixin(CreeperEntity.class)
public abstract class CreeperEntityMixin {
    @ModifyArg(
            method = "explode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;createExplosion(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/world/World$ExplosionSourceType;)V"
            ),
            index = 5
    )
    private World.ExplosionSourceType justys$disableCreeperBlockDamage(World.ExplosionSourceType sourceType) {
        return ModConfig.current().isAntiCreeperGrief() ? World.ExplosionSourceType.NONE : sourceType;
    }
}
