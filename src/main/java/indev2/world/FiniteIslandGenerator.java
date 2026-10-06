package indev2.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** A persistent single-biome generator that delegates the generation pipeline to vanilla noise. */
public final class FiniteIslandGenerator extends ChunkGenerator {
    public static final MapCodec<FiniteIslandGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(FiniteIslandGenerator::biome),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(g -> g.settings),
            Codec.intRange(IslandShape.MIN_RADIUS, IslandShape.MAX_RADIUS).fieldOf("radius").forGetter(FiniteIslandGenerator::radius),
            Codec.intRange(IslandShape.MIN_MARGIN, IslandShape.MAX_MARGIN).fieldOf("ocean_margin").forGetter(FiniteIslandGenerator::oceanMargin),
            RegistryOps.<NormalNoise.NoiseParameters, FiniteIslandGenerator>retrieveGetter(Registries.NOISE)
    ).apply(i, FiniteIslandGenerator::new));

    private final Holder<Biome> biome;
    private final Holder<NoiseGeneratorSettings> settings;
    private final int radius;
    private final int oceanMargin;
    private final HolderGetter<NormalNoise.NoiseParameters> noises;
    private volatile Runtime runtime;

    public FiniteIslandGenerator(Holder<Biome> biome, Holder<NoiseGeneratorSettings> settings,
                                 int radius, int oceanMargin, HolderGetter<NormalNoise.NoiseParameters> noises) {
        super(new FixedBiomeSource(biome));
        if (radius < IslandShape.MIN_RADIUS || radius > IslandShape.MAX_RADIUS
                || oceanMargin < IslandShape.MIN_MARGIN || oceanMargin > IslandShape.MAX_MARGIN) {
            throw new IllegalArgumentException("Island size is outside the supported range");
        }
        this.biome = biome;
        this.settings = settings;
        this.radius = radius;
        this.oceanMargin = oceanMargin;
        this.noises = noises;
    }

    public Holder<Biome> biome() { return biome; }
    public int radius() { return radius; }
    public int oceanMargin() { return oceanMargin; }
    public int worldWidth() { return IslandShape.worldWidth(radius, oceanMargin); }

    // Registry holders may be unbound while presets are decoded. Resolve them only after loading.
    private Runtime runtime() {
        Runtime current = runtime;
        if (current == null) {
            synchronized (this) {
                if (runtime == null) runtime = createRuntime(0);
                current = runtime;
            }
        }
        return current;
    }

    private Runtime createRuntime(long seed) {
        NoiseGeneratorSettings original = settings.value();
        NoiseRouter n = original.noiseRouter();
        NoiseRouter island = new NoiseRouter(n.barrierNoise(), n.fluidLevelFloodednessNoise(),
                n.fluidLevelSpreadNoise(), n.lavaNoise(), n.temperature(), n.vegetation(),
                n.continents(), n.erosion(), n.depth(), n.ridges(),
                new IslandDensity(n.preliminarySurfaceLevel(), radius, seed, original.seaLevel(), true),
                new IslandDensity(n.finalDensity(), radius, seed, original.seaLevel(), false),
                n.veinToggle(), n.veinRidged(), n.veinGap());
        NoiseGeneratorSettings shaped = new NoiseGeneratorSettings(original.noiseSettings(), original.defaultBlock(),
                original.defaultFluid(), island, original.surfaceRule(), original.spawnTarget(), original.seaLevel(),
                original.disableMobGeneration(), original.isAquifersEnabled(), original.oreVeinsEnabled(), original.useLegacyRandomSource());
        return new Runtime(new NoiseBasedChunkGenerator(biomeSource, Holder.direct(shaped)),
                RandomState.create(shaped, noises, seed));
    }

    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState ignored, long seed) {
        runtime = createRuntime(seed);
        return super.createState(structures, runtime().state(), seed);
    }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public CompletableFuture<ChunkAccess> createBiomes(RandomState ignored, Blender blender, StructureManager structures, ChunkAccess chunk) {
        return runtime().generator().createBiomes(runtime().state(), blender, structures, chunk);
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState ignored, StructureManager structures, ChunkAccess chunk) {
        return runtime().generator().fillFromNoise(blender, runtime().state(), structures, chunk);
    }
    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState ignored, ChunkAccess chunk) {
        runtime().generator().buildSurface(region, structures, runtime().state(), chunk);
    }
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState ignored, BiomeManager biomes, StructureManager structures, ChunkAccess chunk) {
        runtime().generator().applyCarvers(region, seed, runtime().state(), biomes, structures, chunk);
    }
    @Override public void spawnOriginalMobs(WorldGenRegion region) { runtime().generator().spawnOriginalMobs(region); }
    @Override public int getGenDepth() { return settings.value().noiseSettings().height(); }
    @Override public int getSeaLevel() { return settings.value().seaLevel(); }
    @Override public int getMinY() { return settings.value().noiseSettings().minY(); }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState ignored) {
        return runtime().generator().getBaseHeight(x, z, type, height, runtime().state());
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState ignored) {
        return runtime().generator().getBaseColumn(x, z, height, runtime().state());
    }
    @Override public void addDebugScreenInfo(List<String> lines, RandomState ignored, BlockPos pos) {
        lines.add("Finite island: radius " + radius + ", ocean margin " + oceanMargin);
        runtime().generator().addDebugScreenInfo(lines, runtime().state(), pos);
    }
    @Override public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager structures,
            ChunkAccess chunk, StructureTemplateManager templates, ResourceKey<Level> dimension) {
        // Land structures must not start in the surrounding ocean or straddle the coast.
        if (Math.hypot(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()) < radius * 0.72) {
            super.createStructures(registries, state, structures, chunk, templates, dimension);
        }
    }
    private record Runtime(NoiseBasedChunkGenerator generator, RandomState state) {}
}
