package xyz.justys.ec.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.justys.ec.HarvestingHandler;
import xyz.justys.ec.ModConfig;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Unique
    private static final Logger JUSTYS_ESSENTIALS_LOGGER = LoggerFactory.getLogger("justys-essentials");

    @Shadow
    protected ServerWorld world;

    @Shadow
    protected ServerPlayerEntity player;

    @Unique
    private BlockState justys$lastBrokenState;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"))
    private void justys$captureState(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        justys$lastBrokenState = world.getBlockState(pos);
    }

    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void justys$handleChainedHarvest(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (cir.getReturnValueZ()) {
                HarvestingHandler.handleBlockBreak(
                        (ServerPlayerInteractionManager) (Object) this,
                        world,
                        player,
                        pos,
                        justys$lastBrokenState,
                        ModConfig.current(),
                        JUSTYS_ESSENTIALS_LOGGER
                );
            }
        } finally {
            justys$lastBrokenState = null;
        }
    }
}
