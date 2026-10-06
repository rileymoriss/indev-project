package indev2.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Samples an unchanged vanilla noise function at a different horizontal location. */
public record TerrainOffset(DensityFunction input, int x, int z) implements DensityFunction {
    public static final KeyDispatchDataCodec<TerrainOffset> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(TerrainOffset::input),
            Codec.INT.fieldOf("x").forGetter(TerrainOffset::x), Codec.INT.fieldOf("z").forGetter(TerrainOffset::z)
    ).apply(i, TerrainOffset::new)));
    @Override public double compute(FunctionContext context) {
        return input.compute(new SinglePointContext(context.blockX() + x, context.blockY(), context.blockZ() + z));
    }
    @Override public void fillArray(double[] output, ContextProvider provider) { provider.fillAllDirectly(output, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new TerrainOffset(input.mapAll(visitor), x, z)); }
    @Override public double minValue() { return input.minValue(); }
    @Override public double maxValue() { return input.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
