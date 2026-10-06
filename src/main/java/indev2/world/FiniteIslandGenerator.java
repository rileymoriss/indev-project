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
            Codec.LONG.optionalFieldOf("seed_salt", 0L).forGetter(g -> g.seedSalt),
            Codec.BOOL.optionalFieldOf("island", true).forGetter(FiniteIslandGenerator::island),
            Codec.BOOL.optionalFieldOf("random_variant", false).forGetter(g -> g.randomVariant),
            RegistryOps.<NormalNoise.NoiseParameters, FiniteIslandGenerator>retrieveGetter(Registries.NOISE)
    ).apply(i, FiniteIslandGenerator::new));

    private Holder<Biome> biome;
    private boolean randomVariant;
    private final Holder<NoiseGeneratorSettings> settings;
    private final int radius;
    private final int oceanMargin;
    private final HolderGetter<NormalNoise.NoiseParameters> noises;
    private final long seedSalt;
    private final boolean island;
    private volatile Runtime runtime;

    public FiniteIslandGenerator(Holder<Biome> biome, Holder<NoiseGeneratorSettings> settings,
                                 int radius, int oceanMargin, HolderGetter<NormalNoise.NoiseParameters> noises) {
        this(biome, settings, radius, oceanMargin, 0L, noises);
    }

    public FiniteIslandGenerator(Holder<Biome> biome, Holder<NoiseGeneratorSettings> settings,
                                 int radius, int oceanMargin, long seedSalt, HolderGetter<NormalNoise.NoiseParameters> noises) {
        this(biome, settings, radius, oceanMargin, seedSalt, true, noises);
    }

    public FiniteIslandGenerator(Holder<Biome> biome, Holder<NoiseGeneratorSettings> settings,
                                 int radius, int oceanMargin, long seedSalt, boolean island, HolderGetter<NormalNoise.NoiseParameters> noises) {
        this(biome, settings, radius, oceanMargin, seedSalt, island, false, noises);
    }

    public FiniteIslandGenerator(Holder<Biome> biome, Holder<NoiseGeneratorSettings> settings,
                                 int radius, int oceanMargin, long seedSalt, boolean island, boolean randomVariant, HolderGetter<NormalNoise.NoiseParameters> noises) {
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
        this.seedSalt = seedSalt;
        this.island = island;
        this.randomVariant = randomVariant;
    }

    public Holder<Biome> biome() { return biome; }
    public boolean island() { return island; }
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

    public FiniteIslandGenerator resized(int radius, int margin) {
        return resized(radius, margin, island);
    }

    public FiniteIslandGenerator resized(int radius, int margin, boolean island) {
        return new FiniteIslandGenerator(biome, settings, radius, margin, seedSalt, island, randomVariant, noises);
    }

    /** Resolve before ServerLevel constructs its chunk source; serialize the selected biome in the original stem too. */
    public FiniteIslandGenerator resolveVariant(long seed, HolderLookup.Provider registries) {
        if (!randomVariant) return this;
        var family = indev2.portal.BiomeDestination.forBiome(biome).orElseThrow();
        biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(family.selectBiome(seed));
        randomVariant = false;
        return new FiniteIslandGenerator(biome, settings, radius, oceanMargin, seedSalt, island, noises);
    }

    private Runtime createRuntime(long seed) {
        NoiseGeneratorSettings original = settings.value();
        if (!island) return new Runtime(new NoiseBasedChunkGenerator(biomeSource, settings),
                RandomState.create(original, noises, seed));
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
        runtime = createRuntime(seed ^ seedSalt);
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
        runtime().generator().applyCarvers(region, seed ^ seedSalt, runtime().state(), biomes, structures, chunk);
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
        lines.add("Finite world: island " + island + ", border width " + worldWidth());
        runtime().generator().addDebugScreenInfo(lines, runtime().state(), pos);
    }
    @Override public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager structures,
            ChunkAccess chunk, StructureTemplateManager templates, ResourceKey<Level> dimension) {
        // Land structures must not start in the surrounding ocean or straddle the coast.
        if (!island || Math.hypot(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ()) < radius * 0.72) {
            super.createStructures(registries, state, structures, chunk, templates, dimension);
        }
    }
    private record Runtime(NoiseBasedChunkGenerator generator, RandomState state) {}
}
