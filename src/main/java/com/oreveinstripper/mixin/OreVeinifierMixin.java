package com.oreveinstripper.mixin;

import com.oreveinstripper.DevTools;
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
            if (state == null) return null;
            BlockState placed = VeinRules.apply(state);
            if (DevTools.ENABLED) DevTools.record(state, placed, ctx.blockX(), ctx.blockY(), ctx.blockZ());
            return placed;
        });
    }
}
