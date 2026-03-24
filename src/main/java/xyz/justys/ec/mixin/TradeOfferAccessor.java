package xyz.justys.ec.mixin;

import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TradeOffer.class)
public interface TradeOfferAccessor {
    @Accessor("uses")
    void justys$setUses(int uses);

    @Mutable
    @Accessor("maxUses")
    void justys$setMaxUses(int maxUses);

    @Accessor("demandBonus")
    void justys$setDemandBonus(int demandBonus);
}
