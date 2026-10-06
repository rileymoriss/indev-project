package indev2.test;

import indev2.client.FiniteIslandScreen;
import indev2.world.FiniteIslandGenerator;
import indev2.world.IslandShape;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public final class FiniteIslandGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        // Exercise the actual creation state, injected Customize editor, and validation.
        context.runOnClient(client -> CreateWorldScreen.openFresh(client, () -> client.setScreen(new TitleScreen())));
        context.waitForScreen(CreateWorldScreen.class);
        context.runOnClient(client -> {
            CreateWorldScreen screen = (CreateWorldScreen) client.screen;
            var state = screen.getUiState();
            var entry = state.getNormalPresetList().stream().filter(type -> type.preset() != null
                    && type.preset().unwrapKey().orElseThrow().identifier().equals(Identifier.parse("indev2:finite_island")))
                    .findFirst().orElseThrow(() -> new AssertionError("Finite Island missing from world types"));
            state.setWorldType(entry);
            check(state.getPresetEditor() != null, "Missing Customize editor");
            client.setScreen(state.getPresetEditor().createEditScreen(screen, state.getSettings()));
        });
        context.waitForScreen(FiniteIslandScreen.class);
        context.runOnClient(client -> {
            var inputs = client.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            inputs.get(0).setValue("0");
            Button done = client.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals("Done")).findFirst().orElseThrow();
            check(!done.active, "Invalid radius accepted");
            inputs.get(0).setValue("192");
            inputs.get(1).setValue("64");
            check(done.active, "Valid size rejected");
        });
        context.takeScreenshot("finite-island-menu");
        context.clickScreenButton("gui.done");
        context.runOnClient(client -> {
            var state = ((CreateWorldScreen) client.screen).getUiState();
            var generator = (FiniteIslandGenerator) state.getSettings().selectedDimensions().overworld();
            check(generator.radius() == 192 && generator.oceanMargin() == 64, "Menu sizes not applied");
        });
        context.setScreen(TitleScreen::new);

        // Generate real chunks, save a player change, then reopen the world.
        TestWorldSave save;
        try (var world = context.worldBuilder().adjustSettings(state -> {
            state.setSeed("12345");
            state.setGenerateStructures(true);
            state.updateDimensions((registries, dimensions) -> dimensions.replaceOverworldGenerator(registries,
                    new FiniteIslandGenerator(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.FOREST),
                            registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                            192, 64, registries.lookupOrThrow(Registries.NOISE))));
        }).create()) {
            save = world.getWorldSave();
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var generator = (FiniteIslandGenerator) level.getChunkSource().getGenerator();
                check(level.getWorldBorder().getSize() == 512, "Incorrect world border");
                check(level.getWorldBorder().getCenterX() == 0 && level.getWorldBorder().getCenterZ() == 0, "Incorrect centre");
                check(level.getWorldBorder().isWithinBounds(new BlockPos(255, 70, 0)), "Border excludes interior");
                check(!level.getWorldBorder().isWithinBounds(new BlockPos(257, 70, 0)), "Border allows exterior");
                check(level.getBiome(new BlockPos(0, 70, 0)).is(Biomes.FOREST), "Incorrect island biome");
                check(level.getBiome(new BlockPos(240, 70, 0)).is(Biomes.FOREST), "World has multiple biomes");
                check(level.getHeight(Heightmap.Types.OCEAN_FLOOR, 0, 0) > generator.getSeaLevel(), "Centre is submerged");
                for (int[] point : new int[][]{{240, 0}, {-240, 0}, {0, 240}, {0, -240}, {240, 240}}) {
                    check(level.getHeight(Heightmap.Types.OCEAN_FLOOR, point[0], point[1]) < generator.getSeaLevel(), "Land in ocean margin");
                    check(level.getBlockState(new BlockPos(point[0], generator.getSeaLevel() - 1, point[1])).is(Blocks.WATER), "Missing surrounding ocean");
                }
                var spawn = server.getRespawnData().pos();
                check(Math.hypot(spawn.getX(), spawn.getZ()) < 192, "Spawn is off the island");
                // Sample vanilla noise columns for several seeds, including supported size limits.
                var registries = server.registryAccess();
                for (int radius : new int[]{IslandShape.MIN_RADIUS, 1000, IslandShape.MAX_RADIUS}) {
                    for (long seed : new long[]{0, 1, -987654321}) {
                        var sample = new FiniteIslandGenerator(
                                registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.FOREST),
                                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                                radius, IslandShape.MIN_MARGIN, registries.lookupOrThrow(Registries.NOISE));
                        sample.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), level.getChunkSource().randomState(), seed);
                        check(sample.getBaseHeight(0, 0, Heightmap.Types.OCEAN_FLOOR_WG, level,
                                level.getChunkSource().randomState()) > sample.getSeaLevel(), "Submerged centre for seed " + seed);
                        for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int distance = radius + 16;
                            check(sample.getBaseHeight(direction[0] * distance, direction[1] * distance,
                                    Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState()) < sample.getSeaLevel(),
                                    "Land outside radius for seed " + seed);
                        }
                    }
                }
                level.setBlockAndUpdate(new BlockPos(0, 150, 0), Blocks.GOLD_BLOCK.defaultBlockState());
            });
        }
        try (var reopened = save.open()) {
            reopened.getServer().runOnServer(server -> {
                var level = server.overworld();
                var generator = (FiniteIslandGenerator) level.getChunkSource().getGenerator();
                check(generator.radius() == 192 && generator.oceanMargin() == 64, "Size settings lost on reload");
                check(generator.biome().is(Biomes.FOREST), "Biome lost on reload");
                check(level.getWorldBorder().getSize() == 512, "Border lost on reload");
                check(level.getBlockState(new BlockPos(0, 150, 0)).is(Blocks.GOLD_BLOCK), "Player changes lost on reload");
                check(level.getHeight(Heightmap.Types.OCEAN_FLOOR, -240, -240) < generator.getSeaLevel(), "New chunks lose island shape after reload");
            });
        }
        // The preset must remain opt-in: ordinary worlds keep their vanilla generator and border.
        try (var ordinary = context.worldBuilder().create()) {
            ordinary.getServer().runOnServer(server -> {
                check(!(server.overworld().getChunkSource().getGenerator() instanceof FiniteIslandGenerator), "Changed ordinary world generation");
                check(server.overworld().getWorldBorder().getSize() > 1_000_000, "Changed ordinary world border");
            });
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
