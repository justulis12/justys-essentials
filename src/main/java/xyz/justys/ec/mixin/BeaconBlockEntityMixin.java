package xyz.justys.ec.mixin;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.justys.ec.BeaconRangeState;

@Mixin(BeaconBlockEntity.class)
public class BeaconBlockEntityMixin {
    @ModifyVariable(
            method = "applyPlayerEffects",
            at = @At("STORE"),
            ordinal = 0
    )
    private static double addConfiguredBonusRange(double originalRange, World world) {
        if (world == null || world.getServer() == null) {
            return originalRange;
        }

        int bonusChunks = BeaconRangeState.get(world.getServer()).getBonusChunks();
        return originalRange + (bonusChunks * 16.0);
    }
}
