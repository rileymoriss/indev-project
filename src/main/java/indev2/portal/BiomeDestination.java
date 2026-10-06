package indev2.portal;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import indev2.world.FiniteIslandGenerator;

/** Palette shared by the creation menu, dye detection, blocks, and dimension routing. */
public enum BiomeDestination implements PortalDestination {
    FOREST("forest", Items.GREEN_DYE, 0x59b83c, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST),
    DESERT("desert", Items.YELLOW_DYE, 0xf9df45, Biomes.DESERT),
    JUNGLE("jungle", Items.LIME_DYE, 0x80c71f, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE),
    TAIGA("taiga", Items.BROWN_DYE, 0x835432, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA),
    BADLANDS("badlands", Items.ORANGE_DYE, 0xf9801d, Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS),
    SAVANNA("savanna", Items.RED_DYE, 0xd93636, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA),
    DARK_FOREST("dark_forest", Items.BLACK_DYE, 0x45404d, Biomes.DARK_FOREST),
    SNOWY_LOWLANDS("snowy_lowlands", Items.WHITE_DYE, 0xf2f5ff, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA),
    ALPINE("alpine", Items.LIGHT_GRAY_DYE, 0xb8bdc7, Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS),
    HIGHLANDS("highlands", Items.GRAY_DYE, 0x707780, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.STONY_PEAKS),
    CHERRY("cherry", Items.PINK_DYE, 0xf38baa, Biomes.CHERRY_GROVE),
    SWAMP("swamp", Items.CYAN_DYE, 0x169c9c, Biomes.SWAMP, Biomes.MANGROVE_SWAMP),
    ICE_SPIKES("ice_spikes", Items.LIGHT_BLUE_DYE, 0x63c4ef, Biomes.ICE_SPIKES),
    MUSHROOM("mushroom", Items.MAGENTA_DYE, 0xc74ebd, Biomes.MUSHROOM_FIELDS),
    PALE_GARDEN("pale_garden", Items.PURPLE_DYE, 0xd9c4f2, Biomes.PALE_GARDEN),
    COUNTRYSIDE("countryside", Items.BLUE_DYE, 0x465fe0, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW);

    private final String id;
    private final java.util.List<ResourceKey<Biome>> biomes;
    private final Item dye;
    private final int color;

    @SafeVarargs
    BiomeDestination(String id, Item dye, int color, ResourceKey<Biome>... biomes) {
        this.id = id;
        this.biomes = java.util.List.of(biomes);
        this.dye = dye;
        this.color = color;
    }
    public String id() { return id; }
    public ResourceKey<Biome> biome() { return biomes.getFirst(); }
    public java.util.List<ResourceKey<Biome>> biomes() { return biomes; }
    public ResourceKey<Biome> selectBiome(long seed) {
        long mixed = seed ^ (0x9e3779b97f4a7c15L * id.hashCode());
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return biomes.get((int) Math.floorMod(mixed ^ (mixed >>> 31), biomes.size()));
    }
    public Item dye() { return dye; }
    public int color() { return color; }
    public int textureTint() { return color; }
    public Identifier dimensionId() { return Identifier.fromNamespaceAndPath("indev2", id); }
    public ResourceKey<Level> dimension() { return ResourceKey.create(Registries.DIMENSION, dimensionId()); }
    public static Optional<BiomeDestination> forDye(ItemStack stack) {
        return Arrays.stream(values()).filter(destination -> stack.is(destination.dye)).findFirst();
    }
    public static Optional<BiomeDestination> forBiome(Holder<Biome> biome) {
        return Arrays.stream(values()).filter(destination -> destination.biomes.stream().anyMatch(biome::is)).findFirst();
    }
    public static Optional<BiomeDestination> forLevel(ServerLevel level) {
        if (!(level.getServer().overworld().getChunkSource().getGenerator() instanceof FiniteIslandGenerator home)
                || forBiome(home.biome()).isEmpty()) return Optional.empty();
        if (level.dimension() == Level.OVERWORLD) return forBiome(home.biome());
        return Arrays.stream(values()).filter(destination -> level.dimension().equals(destination.dimension())).findFirst();
    }
    public ServerLevel level(MinecraftServer server) {
        if (server.overworld().getChunkSource().getGenerator() instanceof FiniteIslandGenerator home
                && forBiome(home.biome()).orElse(null) == this) return server.overworld();
        return server.getLevel(dimension());
    }
}
