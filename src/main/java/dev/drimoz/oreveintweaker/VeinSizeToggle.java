package dev.drimoz.oreveintweaker;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The vanilla vein toggle, pushed away from or towards zero per vein type. A vein exists where
 * |toggle| reaches 0.4, so shifting |toggle| moves that threshold - and with it how much of the
 * underground is vein - while keeping vanilla's shapes. Only ever handed to OreVeinifier, which only
 * calls compute().
 */
public record VeinSizeToggle(DensityFunction vanilla) implements DensityFunction {
    @Override
    public double compute(FunctionContext ctx) {
        return VeinRules.resizeToggle(vanilla.compute(ctx));
    }

    @Override
    public void fillArray(double[] values, ContextProvider provider) {
        provider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new VeinSizeToggle(vanilla.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return vanilla.minValue() - VeinRules.MAX_SHIFT;
    }

    @Override
    public double maxValue() {
        return vanilla.maxValue() + VeinRules.MAX_SHIFT;
    }

    // Never serialized: this function only exists inside a live NoiseChunk.
    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return vanilla.codec();
    }
}
