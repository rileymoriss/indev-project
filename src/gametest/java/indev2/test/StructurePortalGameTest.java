package indev2.test;

import indev2.portal.*;
import indev2.world.*;
import java.util.EnumMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.AABB;

public final class StructurePortalGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> { client.options.renderDistance().set(2); client.options.enableVsync().set(false); });
        TestWorldSave save;
        PortalRef entrance;
        EnumMap<StructureDestination, PortalRef> exits = new EnumMap<>(StructureDestination.class);
        try (var world = context.worldBuilder().adjustSettings(state -> {
            state.setSeed("90817");
            // Guarantees apply even with ordinary natural structure generation disabled.
            state.setGenerateStructures(false);
            state.updateDimensions((registries, dimensions) -> dimensions.replaceOverworldGenerator(registries,
                    new FiniteIslandGenerator(registries.lookupOrThrow(Registries.BIOME).getOrThrow(BiomeDestination.FOREST.biome()),
                            registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                            128, 32, registries.lookupOrThrow(Registries.NOISE))));
        }).create()) {
            save = world.getWorldSave();
            entrance = world.getServer().computeOnServer(server -> BiomePortals.createArrival(server.overworld(), BlockPos.ZERO, Direction.Axis.X, BiomeDestination.DESERT));
            check(entrance != null, "No source portal site");
            for (var destination : StructureDestination.values()) {
                indev2.Indev2Mod.LOGGER.info("Testing structure portal {}", destination.id());
                world.getServer().runOnServer(server -> {
                    var area = PortalArea.find(server.overworld(), entrance.anchor());
                    area.recolor(server.overworld(), Blocks.NETHER_PORTAL);
                    var target = destination.level(server);
                    check(target != null, "Missing special world " + destination);
                    var generator = (FiniteIslandGenerator) target.getChunkSource().getGenerator();
                    check(generator.special() == destination && !generator.island(), "Incorrect special terrain profile");
                    check(target.getWorldBorder().getSize() == 320, "Special world size differs");
                    if (destination == StructureDestination.END_CITY) check(!target.dimensionType().hasEnderDragonFight(), "End city world enables dragon fight");
                    check(GuaranteedStructures.prepare(target, destination), "Missing guaranteed structure " + destination);
                    for (int index = 0; index < destination.structures().size(); index++) {
                        var pos = GuaranteedStructures.source(destination, index);
                        var structure = target.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(Identifier.withDefaultNamespace(destination.structures().get(index)));
                        var start = target.getChunk(pos.x(), pos.z()).getStartForStructure(structure);
                        check(start != null && start.isValid(), "Structure start missing");
                        var box = start.getBoundingBox();
                        check(box.minX() >= -160 && box.maxX() < 160 && box.minZ() >= -160 && box.maxZ() < 160, "Structure crosses border");
                        if (destination == StructureDestination.END_CITY) {
                            check(GuaranteedStructures.hasTemplate(start, StructurePieceSerializationContext.fromLevel(target), "ship"), "End ship missing");
                            var frames = target.getEntitiesOfClass(ItemFrame.class, new AABB(-160, 0, -160, 160, 256, 160));
                            check(frames.stream().anyMatch(frame -> frame.getItem().is(Items.ELYTRA)), "End ship elytra missing");
                        }
                        if (destination == StructureDestination.IGLOO)
                            check(GuaranteedStructures.hasTemplate(start, StructurePieceSerializationContext.fromLevel(target), "igloo/bottom"), "Igloo basement missing");
                        if (destination == StructureDestination.DESERT_TEMPLE || destination == StructureDestination.END_CITY) {
                            long containers = 0;
                            for (int x = Math.floorDiv(box.minX(), 16); x <= Math.floorDiv(box.maxX(), 16); x++)
                                for (int z = Math.floorDiv(box.minZ(), 16); z <= Math.floorDiv(box.maxZ(), 16); z++)
                                    containers += target.getChunk(x, z).getBlockEntities().values().stream().filter(RandomizableContainerBlockEntity.class::isInstance).count();
                            check(containers > 0, "Native loot containers missing");
                        }
                    }
                    if (destination == StructureDestination.MONUMENT) {
                        long elders = target.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(-80, 0, -80, 80, 100, 80))
                                .stream().filter(entity -> entity.getType() == net.minecraft.world.entity.EntityType.ELDER_GUARDIAN).count();
                        check(elders == 3, "Native elder guardians missing");
                    }
                    if (destination == StructureDestination.TRIAL_CHAMBERS) {
                        long spawners = 0;
                        var pos = GuaranteedStructures.source(destination, 0);
                        var structure = target.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(Identifier.withDefaultNamespace("trial_chambers"));
                        var box = target.getChunk(pos.x(), pos.z()).getStartForStructure(structure).getBoundingBox();
                        for (int x = Math.floorDiv(box.minX(), 16); x <= Math.floorDiv(box.maxX(), 16); x++)
                            for (int z = Math.floorDiv(box.minZ(), 16); z <= Math.floorDiv(box.maxZ(), 16); z++)
                                spawners += target.getChunk(x, z).getBlockEntities().values().stream().filter(entity ->
                                        net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).getPath().equals("trial_spawner")).count();
                        check(spawners > 0, "Native trial spawners missing");
                    }
                    if (destination == StructureDestination.CORAL_REEF) {
                        int count = 0;
                        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = target.getMinY(); y < 63; y++)
                            if (target.getBlockState(new BlockPos(x, y, z)).is(BlockTags.CORAL_BLOCKS)) count++;
                        check(count >= 8, "Guaranteed coral reef missing");
                    }
                });
                if (destination == StructureDestination.MONUMENT || destination == StructureDestination.ANCIENT_CITY || destination == StructureDestination.END_CITY) {
                    context.runOnClient(client -> client.options.renderDistance().set(8));
                    world.getServer().runOnServer(server -> {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 12000, 0, false, false));
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(destination.level(server), 0, destination == StructureDestination.ANCIENT_CITY ? -10 : 115,
                                35, java.util.Set.of(), 180, destination == StructureDestination.ANCIENT_CITY ? 50 : 65, false);
                    });
                    context.waitTicks(30);
                    context.waitTicks(200);
                    context.takeScreenshot("special-" + destination.id());
                    context.runOnClient(client -> client.options.renderDistance().set(2));
                    world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                            .teleportTo(server.overworld(), entrance.anchor().getX(), entrance.anchor().getY(), entrance.anchor().getZ() + 3,
                                    java.util.Set.of(), 0, 0, false));
                }
                if (destination != StructureDestination.FORTRESS) {
                    int entity = BiomePortalGameTest.throwDye(world, entrance, destination.items().getFirst(), 2);
                    context.waitTicks(5);
                    world.getServer().runOnServer(server -> {
                        check(server.overworld().getBlockState(entrance.anchor()).is(BiomePortals.block(destination)), "Structure item did not activate portal");
                        check(server.overworld().getEntity(entity) instanceof ItemEntity remaining && remaining.getItem().getCount() == 1, "Wrong item consumption");
                    });
                }
                BiomePortalGameTest.enter(context, world, entrance, destination.dimension());
                PortalRef exit = world.getServer().computeOnServer(server -> PortalLinks.get(server).find(entrance, destination));
                check(exit != null, "No linked return portal");
                if (destination.settingsId().equals("nether")) check(exit.anchor().getY() < 122, "Nether arrival is above bedrock roof");
                if (destination == StructureDestination.FORTRESS) world.getServer().runOnServer(server ->
                        PortalArea.find(destination.level(server), exit.anchor()).recolor(destination.level(server), Blocks.NETHER_PORTAL));
                exits.put(destination, exit);
                BiomePortalGameTest.enter(context, world, exit, Level.OVERWORLD);
                world.getServer().runOnServer(server -> destination.level(server).setBlockAndUpdate(new BlockPos(145, 80, 145), Blocks.GOLD_BLOCK.defaultBlockState()));
            }
            world.getServer().runOnServer(server -> {
                for (Item fish : new Item[]{Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH})
                    check(StructureDestination.forItem(new ItemStack(fish)).orElseThrow() == StructureDestination.MONUMENT, "Raw fish not recognised");
                check(StructureDestination.forItem(new ItemStack(Items.COOKED_COD)).isEmpty(), "Cooked fish accepted");
            });
        }
        try (var reopened = save.open()) {
            reopened.getServer().runOnServer(server -> {
                for (var destination : StructureDestination.values()) {
                    var target = destination.level(server);
                    check(((FiniteIslandGenerator) target.getChunkSource().getGenerator()).special() == destination, "Special profile lost on reload");
                    check(target.getBlockState(new BlockPos(145, 80, 145)).is(Blocks.GOLD_BLOCK), "Player change lost");
                    check(PortalLinks.get(server).find(entrance, destination).equals(exits.get(destination)), "Special portal link lost");
                    check(GuaranteedStructures.prepare(target, destination), "Structure lost on reload");
                }
            });
        }
        context.runOnClient(client -> client.options.renderDistance().set(5));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
