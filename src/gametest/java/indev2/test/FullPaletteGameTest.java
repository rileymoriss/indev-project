package indev2.test;

import indev2.portal.*;
import indev2.world.FiniteIslandGenerator;
import java.util.EnumMap;
import java.util.HashSet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public final class FullPaletteGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.renderDistance().set(2);
            client.options.enableVsync().set(false);
        });
        check(BiomeDestination.values().length == 16, "Missing dye families");
        var seenDyes = new HashSet<Object>();
        for (var family : BiomeDestination.values()) {
            var variants = new HashSet<ResourceKey<Biome>>();
            for (long seed = 0; seed < 200; seed++) variants.add(family.selectBiome(seed));
            check(variants.containsAll(family.biomes()), "Unreachable variant in " + family);
            check(seenDyes.add(family.dye()), "Duplicate dye mapping");
        }
        EnumMap<BiomeDestination, ResourceKey<Biome>> selected = new EnumMap<>(BiomeDestination.class);
        TestWorldSave save;
        try (var world = context.worldBuilder().adjustSettings(state -> {
            state.setSeed("90817");
            state.updateDimensions((registries, dimensions) -> dimensions.replaceOverworldGenerator(registries,
                    new FiniteIslandGenerator(registries.lookupOrThrow(Registries.BIOME).getOrThrow(BiomeDestination.FOREST.biome()),
                            registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                            128, 32, 0L, true, true, registries.lookupOrThrow(Registries.NOISE))));
        }).create()) {
            save = world.getWorldSave();
            world.getServer().runOnServer(server -> {
                check(BiomeDestination.forDye(new ItemStack(Items.COBBLESTONE)).isEmpty(), "Unexpected activation item");
                check(server.getLevel(BiomeDestination.FOREST.dimension()) == null, "Duplicate home family");
                for (var family : BiomeDestination.values()) {
                    check(BiomeDestination.forDye(new ItemStack(family.dye())).orElseThrow() == family, "Wrong dye mapping");
                    var level = family.level(server);
                    check(level != null, "Missing dimension " + family);
                    var generator = (FiniteIslandGenerator) level.getChunkSource().getGenerator();
                    var biome = generator.biome().unwrapKey().orElseThrow();
                    check(biome.equals(family.selectBiome(90817)), "Wrong seeded selection " + family);
                    check(BiomeDestination.forBiome(generator.biome()).orElseThrow() == family, "Variant lost family identity");
                    check(generator.island() && generator.worldWidth() == 320 && level.getWorldBorder().getSize() == 320, "Settings differ " + family);
                    var savedStem = server.getWorldGenSettings().dimensions().dimensions().get(Registries.levelToLevelStem(level.dimension()));
                    check(savedStem != null, "Missing saved stem " + family);
                    var encoded = FiniteIslandGenerator.CODEC.codec().encodeStart(
                            net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries(server)),
                            (FiniteIslandGenerator) savedStem.generator()).getOrThrow().getAsJsonObject();
                    check(encoded.get("biome").getAsString().equals(biome.identifier().toString()), "Selected biome not saved " + family);
                    check(!encoded.has("random_variant") || !encoded.get("random_variant").getAsBoolean(), "Saved world can reroll " + family);
                    selected.put(family, biome);
                }
            });
            PortalRef entrance = world.getServer().computeOnServer(server -> BiomePortals.createArrival(
                    server.overworld(), BlockPos.ZERO, Direction.Axis.X, BiomeDestination.DESERT));
            check(entrance != null, "No starting portal site");
            var travelOrder = new java.util.ArrayList<>(java.util.List.of(BiomeDestination.values()));
            travelOrder.remove(BiomeDestination.PALE_GARDEN);
            travelOrder.addFirst(BiomeDestination.PALE_GARDEN);
            for (var family : travelOrder) {
                if (family == BiomeDestination.FOREST) continue;
                indev2.Indev2Mod.LOGGER.info("Testing palette round trip: {}", family.id());
                BiomePortalGameTest.throwDye(world, entrance, family.dye(), 1);
                BiomePortalGameTest.awaitColor(context, world, entrance, family);
                if (family == BiomeDestination.PALE_GARDEN || family == BiomeDestination.MUSHROOM || family == BiomeDestination.ICE_SPIKES) {
                    BiomePortalGameTest.viewPortal(context, world, entrance, "palette-" + family.id());
                }
                BiomePortalGameTest.enter(context, world, entrance, family.dimension());
                PortalRef exit = world.getServer().computeOnServer(server -> {
                    var destination = family.level(server);
                    var link = PortalLinks.get(server).find(entrance, family);
                    check(link != null && link.level(server) == destination, "Missing destination link " + family);
                    check(destination.getBiome(link.anchor()).unwrapKey().orElseThrow().equals(selected.get(family)), "Actual chunk has wrong biome " + family);
                    check(PortalLinks.get(server).find(link, BiomeDestination.FOREST).equals(entrance), "Missing return link " + family);
                    return link;
                });
                BiomePortalGameTest.enter(context, world, exit, Level.OVERWORLD);
            }
            world.getServer().runOnServer(server -> {
                var generator = (FiniteIslandGenerator) server.overworld().getChunkSource().getGenerator();
                check(generator.biome().unwrapKey().orElseThrow().equals(selected.get(BiomeDestination.FOREST)), "Home rerolled");
            });
        }
        try (var reopened = save.open()) {
            reopened.getServer().runOnServer(server -> {
                for (var family : BiomeDestination.values()) {
                    var generator = (FiniteIslandGenerator) family.level(server).getChunkSource().getGenerator();
                    check(generator.biome().unwrapKey().orElseThrow().equals(selected.get(family)), "Biome changed on reload " + family);
                }
            });
        }
        context.runOnClient(client -> client.options.renderDistance().set(5));
    }
    private static net.minecraft.core.RegistryAccess registries(net.minecraft.server.MinecraftServer server) { return server.registryAccess(); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
