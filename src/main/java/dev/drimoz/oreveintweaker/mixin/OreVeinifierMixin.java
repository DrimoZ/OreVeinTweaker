package dev.drimoz.oreveintweaker.mixin;

import dev.drimoz.oreveintweaker.DevTools;
import dev.drimoz.oreveintweaker.VeinSizeToggle;
import dev.drimoz.oreveintweaker.VeinRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.OreVeinifier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Wraps vanilla instead of rewriting it: the toggle going in (size), the block coming out (everything
// else). Vanilla's vein algorithm itself is untouched, which is why one mixin ports across versions.
@Mixin(OreVeinifier.class)
public abstract class OreVeinifierMixin {
    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static DensityFunction oreveintweaker$scaleToggle(DensityFunction toggle) {
        return new VeinSizeToggle(toggle);
    }

    @Inject(method = "create", at = @At("RETURN"), cancellable = true)
    private static void oreveintweaker$wrap(DensityFunction toggle, DensityFunction ridged, DensityFunction gap,
                                             PositionalRandomFactory random,
                                             CallbackInfoReturnable<NoiseChunk.BlockStateFiller> cir) {
        NoiseChunk.BlockStateFiller vanilla = cir.getReturnValue();
        // Our own seeded stream: reusing vanilla's per-position random would correlate our rolls with
        // the ones that already decided ore vs filler.
        PositionalRandomFactory ours = random.fromHashOf("oreveintweaker:veins").forkPositional();
        PositionalRandomFactory[] extraRandoms = VeinRules.extraRandoms(random);
        // The extra veins size themselves: they need the toggle as vanilla computes it.
        DensityFunction rawToggle = toggle instanceof VeinSizeToggle sized ? sized.vanilla() : toggle;
        cir.setReturnValue(ctx -> {
            BlockState state = vanilla.calculate(ctx);
            if (state == null) {
                BlockState extra = VeinRules.applyExtra(ctx, rawToggle, ridged, gap, extraRandoms);
                if (extra != null && DevTools.ENABLED) DevTools.recordExtra(extra);
                return extra;
            }
            BlockState placed = VeinRules.apply(state, ours.at(ctx.blockX(), ctx.blockY(), ctx.blockZ()));
            if (DevTools.ENABLED) DevTools.record(state, placed, ctx.blockX(), ctx.blockY(), ctx.blockZ());
            return placed;
        });
    }
}
