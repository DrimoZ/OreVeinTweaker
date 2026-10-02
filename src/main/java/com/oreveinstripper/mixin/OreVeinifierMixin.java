package com.oreveinstripper.mixin;

import com.oreveinstripper.VeinRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.OreVeinifier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Wraps the vanilla filler instead of touching its internals: only the final block is filtered/replaced.
@Mixin(OreVeinifier.class)
public abstract class OreVeinifierMixin {
    @Inject(method = "create", at = @At("RETURN"), cancellable = true)
    private static void oreveinstripper$wrap(DensityFunction toggle, DensityFunction ridged, DensityFunction gap,
                                             PositionalRandomFactory random,
                                             CallbackInfoReturnable<NoiseChunk.BlockStateFiller> cir) {
        NoiseChunk.BlockStateFiller vanilla = cir.getReturnValue();
        cir.setReturnValue(ctx -> {
            BlockState state = vanilla.calculate(ctx);
            return state == null ? null : VeinRules.apply(state);
        });
    }
}
