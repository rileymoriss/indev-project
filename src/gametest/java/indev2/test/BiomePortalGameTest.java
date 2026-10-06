package indev2.test;

import indev2.portal.*;
import indev2.world.FiniteIslandGenerator;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class BiomePortalGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        PortalRef source;
        PortalRef rebuilt;
        try (var world = create(context, BiomeDestination.FOREST, 192, 64)) {
            save = world.getWorldSave();
            source = world.getServer().computeOnServer(server -> {
                check(BiomeDestination.FOREST.level(server) == server.overworld(), "Forest does not route to starting world");
                check(server.getLevel(BiomeDestination.FOREST.dimension()) == null, "Duplicate forest island exists");
                var desert = BiomeDestination.DESERT.level(server);
                check(desert != null, "Desert dimension missing");
                var generator = (FiniteIslandGenerator) desert.getChunkSource().getGenerator();
                check(generator.radius() == 192 && generator.oceanMargin() == 64, "Destination size does not match home");
                check(desert.getWorldBorder().getSize() == 512, "Destination border does not match home");
                check(generator.biome().is(Biomes.DESERT), "Destination biome is incorrect");
                PortalRef ref = BiomePortals.createArrival(server.overworld(), new BlockPos(24, 0, 24), Direction.Axis.X, BiomeDestination.FOREST);
                check(ref != null, "Cannot find source portal site");
                // Light a vanilla portal in the new frame before throwing dye into it.
                var area = PortalArea.find(server.overworld(), ref.anchor());
                for (var pos : area.blocks()) server.overworld().setBlock(pos,
                        Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, ref.axis()), 18);
                return ref;
            });
            int itemId = throwDye(world, source, Items.YELLOW_DYE, 3);
            awaitColor(context, world, source, BiomeDestination.DESERT);
            context.waitTicks(5);
            world.getServer().runOnServer(server -> {
                var entity = server.overworld().getEntity(itemId);
                check(entity instanceof ItemEntity, "Remaining dye stack vanished or travelled");
                check(((ItemEntity) entity).getItem().getCount() == 2, "Did not consume exactly one dye");
                check(entity.portalProcess == null, "Dye was scheduled for portal travel");
                check(PortalArea.find(server.overworld(), source.anchor()).blocks().size() == 6, "Portal recolour was incomplete");
            });
            int sameColor = throwDye(world, source, Items.YELLOW_DYE, 2);
            context.waitTicks(5);
            world.getServer().runOnServer(server -> check(((ItemEntity) server.overworld().getEntity(sameColor)).getItem().getCount() == 2,
                    "Matching dye was unnecessarily consumed"));
            throwDye(world, source, Items.GREEN_DYE, 1);
            awaitColor(context, world, source, BiomeDestination.FOREST);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                check(BiomePortals.block(BiomeDestination.FOREST).getPortalDestination(server.overworld(), player, source.anchor()) == null,
                        "A portal to the current biome should not create a duplicate destination");
            });
            throwDye(world, source, Items.YELLOW_DYE, 1);
            awaitColor(context, world, source, BiomeDestination.DESERT);
            viewPortal(context, world, source, "yellow-desert-portal");
            enter(context, world, source, BiomeDestination.DESERT.dimension());
            PortalRef firstExit = world.getServer().computeOnServer(server -> {
                PortalRef exit = PortalLinks.get(server).find(source, BiomeDestination.DESERT);
                check(exit != null, "Missing forward link");
                check(PortalLinks.get(server).find(exit, BiomeDestination.FOREST).equals(source), "Missing return link");
                check(exit.level(server).getBlockState(exit.anchor()).is(BiomePortals.block(BiomeDestination.FOREST)), "Return portal is not green");
                return exit;
            });
            viewPortal(context, world, firstExit, "green-forest-return-portal");
            enter(context, world, firstExit, Level.OVERWORLD);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                check(player.blockPosition().distSqr(source.anchor()) < 16, "Return arrived at the wrong source portal");
            });

            // A second entrance must receive its own return link, without changing the first one.
            PortalRef second = world.getServer().computeOnServer(server -> {
                var ref = BiomePortals.createArrival(server.overworld(), source.anchor().offset(32, 0, 0), Direction.Axis.Z, BiomeDestination.DESERT);
                check(ref != null && !ref.equals(source), "Cannot create second entrance");
                return ref;
            });
            enter(context, world, second, BiomeDestination.DESERT.dimension());
            PortalRef secondExit = world.getServer().computeOnServer(server -> {
                PortalRef exit = PortalLinks.get(server).find(second, BiomeDestination.DESERT);
                check(!exit.equals(firstExit), "Separate entrances shared a return portal");
                check(PortalLinks.get(server).find(firstExit, BiomeDestination.FOREST).equals(source), "First return link was overwritten");
                return exit;
            });
            enter(context, world, secondExit, Level.OVERWORLD);

            // Destroy a destination frame and verify safe creation of a replacement on the next trip.
            world.getServer().runOnServer(server -> firstExit.level(server).setBlock(firstExit.anchor().above(3), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            context.waitTicks(5);
            enter(context, world, source, BiomeDestination.DESERT.dimension());
            rebuilt = world.getServer().computeOnServer(server -> {
                var replacement = PortalLinks.get(server).find(source, BiomeDestination.DESERT);
                check(!replacement.equals(firstExit), "Broken destination was not replaced");
                check(PortalLinks.get(server).find(replacement, BiomeDestination.FOREST).equals(source), "Replacement has incorrect return link");
                replacement.level(server).setBlockAndUpdate(replacement.anchor().offset(7, 0, 7), Blocks.GOLD_BLOCK.defaultBlockState());
                return replacement;
            });
        }
        try (var reopened = save.open()) {
            reopened.getServer().runOnServer(server -> {
                check(PortalLinks.get(server).find(source, BiomeDestination.DESERT).equals(rebuilt), "Portal link lost on reload");
                var target = BiomeDestination.DESERT.level(server);
                var generator = (FiniteIslandGenerator) target.getChunkSource().getGenerator();
                check(generator.radius() == 192 && generator.oceanMargin() == 64, "Destination size lost on reload");
                check(target.getBlockState(rebuilt.anchor()).is(BiomePortals.block(BiomeDestination.FOREST)), "Portal colour lost on reload");
                check(target.getBlockState(rebuilt.anchor().offset(7, 0, 7)).is(Blocks.GOLD_BLOCK), "Destination build lost on reload");
            });
            enter(context, reopened, rebuilt, Level.OVERWORLD);
        }

        // Reverse the home biome with island terrain off; both portal directions must still work.
        try (var desertHome = create(context, BiomeDestination.DESERT, 128, 32)) {
            PortalRef entrance = desertHome.getServer().computeOnServer(server -> {
                check(BiomeDestination.DESERT.level(server) == server.overworld(), "Desert does not route home");
                check(server.getLevel(BiomeDestination.DESERT.dimension()) == null, "Duplicate desert island exists");
                var forest = BiomeDestination.FOREST.level(server);
                var generator = (FiniteIslandGenerator) forest.getChunkSource().getGenerator();
                check(!generator.island(), "Forest ignored vanilla terrain mode");
                check(generator.radius() == 128 && forest.getWorldBorder().getSize() == 320, "Forest size does not match desert home");
                return BiomePortals.createArrival(server.overworld(), BlockPos.ZERO, Direction.Axis.Z, BiomeDestination.FOREST);
            });
            check(entrance != null, "Cannot create desert-home portal");
            enter(context, desertHome, entrance, BiomeDestination.FOREST.dimension());
            PortalRef returnPortal = desertHome.getServer().computeOnServer(server -> PortalLinks.get(server).find(entrance, BiomeDestination.FOREST));
            enter(context, desertHome, returnPortal, Level.OVERWORLD);
        }
    }

    private static TestSingleplayerContext create(ClientGameTestContext context, BiomeDestination home, int radius, int margin) {
        return context.worldBuilder().adjustSettings(state -> {
            state.setSeed("90817");
            state.updateDimensions((registries, dimensions) -> dimensions.replaceOverworldGenerator(registries,
                    new FiniteIslandGenerator(registries.lookupOrThrow(Registries.BIOME).getOrThrow(home.biome()),
                            registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                            radius, margin, 0L, home != BiomeDestination.DESERT, registries.lookupOrThrow(Registries.NOISE))));
        }).create();
    }
    static int throwDye(TestSingleplayerContext world, PortalRef portal, Item dye, int count) {
        return world.getServer().computeOnServer(server -> {
            var level = portal.level(server);
            var player = server.getPlayerList().getPlayers().getFirst();
            var nearby = portal.anchor().getBottomCenter().add(portal.axis() == Direction.Axis.X ? 0 : 3, 0,
                    portal.axis() == Direction.Axis.X ? 3 : 0);
            player.teleport(new TeleportTransition(level, nearby, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            ItemEntity item = new ItemEntity(level, portal.anchor().getX() + .5, portal.anchor().getY() + .5,
                    portal.anchor().getZ() + .5, new ItemStack(dye, count));
            item.setDeltaMovement(Vec3.ZERO);
            item.setNoGravity(true);
            item.setPickUpDelay(32767);
            level.addFreshEntity(item);
            return item.getId();
        });
    }
    static void awaitColor(ClientGameTestContext context, TestSingleplayerContext world, PortalRef portal, BiomeDestination color) {
        context.waitTicks(5);
        world.getServer().runOnServer(server -> check(portal.level(server).getBlockState(portal.anchor()).is(BiomePortals.block(color)), "Thrown dye did not recolour portal"));
    }
    static void enter(ClientGameTestContext context, TestSingleplayerContext world, PortalRef portal, net.minecraft.resources.ResourceKey<Level> expected) {
        world.getServer().runOnServer(server -> {
            var level = portal.level(server);
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.CREATIVE);
            level.getGameRules().set(GameRules.PLAYERS_NETHER_PORTAL_CREATIVE_DELAY, 1, server);
            player.teleport(new TeleportTransition(level, portal.anchor().getBottomCenter(), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            player.portalProcess = null;
            player.setPortalCooldown(0);
        });
        context.waitFor(client -> client.level != null && client.level.dimension().equals(expected), 1200);
        context.waitTicks(2);
    }
    static void viewPortal(ClientGameTestContext context, TestSingleplayerContext world, PortalRef portal, String screenshot) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            boolean xAxis = portal.axis() == Direction.Axis.X;
            player.teleportTo(portal.level(server), portal.anchor().getX() + (xAxis ? .8 : 4.5), portal.anchor().getY(),
                    portal.anchor().getZ() + (xAxis ? 4.5 : .8), java.util.Set.of(), xAxis ? 180 : 90, 0, false);
        });
        context.waitTicks(5);
        context.waitTicks(20);
        context.takeScreenshot(screenshot);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
