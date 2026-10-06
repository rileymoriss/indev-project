package indev2.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Wraps vanilla density without discarding its caves and ore noise. */
public record IslandDensity(DensityFunction input, int radius, long seed, int seaLevel, boolean surface)
        implements DensityFunction {
    public static final KeyDispatchDataCodec<IslandDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(IslandDensity::input),
                    Codec.intRange(IslandShape.MIN_RADIUS, IslandShape.MAX_RADIUS).fieldOf("radius").forGetter(IslandDensity::radius),
                    Codec.LONG.fieldOf("seed").forGetter(IslandDensity::seed),
                    Codec.INT.fieldOf("sea_level").forGetter(IslandDensity::seaLevel),
                    Codec.BOOL.fieldOf("surface").forGetter(IslandDensity::surface)
            ).apply(i, IslandDensity::new)));

    @Override
    public double compute(FunctionContext context) {
        IslandShape shape = new IslandShape(radius, seed, seaLevel);
        double floor = shape.floor(context.blockX(), context.blockZ());
        double ceiling = shape.ceiling(context.blockX(), context.blockZ());
        double value = input.compute(context);
        if (surface) return Math.clamp(value, floor, ceiling);
        // Cap above the seabed outside the island, with a smooth coast transition.
        value = Math.min(value, (ceiling - context.blockY()) / 16.0);
        // Guarantee dry land near the centre while retaining deep caves.
        if (context.blockY() >= seaLevel - 16 && floor > seaLevel) {
            value = Math.max(value, (floor - context.blockY()) / 16.0);
        }
        return value;
    }

    @Override public void fillArray(double[] output, ContextProvider provider) {
        provider.fillAllDirectly(output, this);
    }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new IslandDensity(input.mapAll(visitor), radius, seed, seaLevel, surface));
    }
    @Override public double minValue() { return surface ? seaLevel - 28 : Math.min(input.minValue(), -128); }
    @Override public double maxValue() { return surface ? seaLevel + 110 : Math.max(input.maxValue(), 128); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
