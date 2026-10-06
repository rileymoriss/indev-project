package indev2.world;

import indev2.portal.PortalRef;
import indev2.portal.StructureDestination;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Structures remain real chunk structure starts: native placement, loot, mobs, references, and persistence. */
public final class GuaranteedStructures {
    private static final Map<ServerLevel, Boolean> READY = Collections.synchronizedMap(new WeakHashMap<>());
    private GuaranteedStructures() {}
    public static ChunkPos source(StructureDestination destination, int index) {
        return destination == StructureDestination.SHIPWRECK ? new ChunkPos(index == 0 ? -3 : 3, 0) : new ChunkPos(0, 0);
    }
    public static void generate(FiniteIslandGenerator generator, StructureDestination destination, RegistryAccess registries,
            ChunkGeneratorStructureState state, StructureManager manager, ChunkAccess chunk, StructureTemplateManager templates,
            ResourceKey<Level> dimension, RandomState randomState) {
        for (int index = 0; index < destination.structures().size(); index++) {
            ChunkPos source = source(destination, index);
            if (!source.equals(chunk.getPos())) continue;
            var holder = registries.lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,
                    Identifier.withDefaultNamespace(destination.structures().get(index))));
            Structure structure = holder.value();
            var context = new StructurePieceSerializationContext(null, registries, templates);
            for (int attempt = 0; attempt < 4096; attempt++) {
                long seed = state.getLevelSeed() + attempt * 0x9e3779b97f4a7c15L;
                StructureStart start = structure.generate(holder, dimension, registries, generator, generator.getBiomeSource(),
                        randomState, templates, seed, source, 0, chunk, biome -> true);
                if (!start.isValid()) continue;
                if (destination == StructureDestination.END_CITY && !hasTemplate(start, context, "ship")) continue;
                if (destination == StructureDestination.IGLOO && !hasTemplate(start, context, "igloo/bottom")) continue;
                BoundingBox box = start.getBoundingBox();
                // Minecraft structure references search eight chunks around the source. Keep every piece in that range too.
                int half = Math.min(generator.worldWidth() / 2 - 24, 120);
                if (box.getXSpan() > half * 2 || box.getZSpan() > half * 2) continue;
                if (destination != StructureDestination.MONUMENT) {
                    int centerX = destination == StructureDestination.SHIPWRECK ? source.getMiddleBlockX() : 0;
                    int dx = centerX - box.getCenter().getX(), dz = -box.getCenter().getZ();
                    for (var piece : start.getPieces()) {
                        piece.move(dx, 0, dz);
                        if (piece instanceof PoolElementStructurePiece pooled) {
                            pooled.getJunctions().replaceAll(junction -> new net.minecraft.world.level.levelgen.structure.pools.JigsawJunction(
                                    junction.getSourceX() + dx, junction.getSourceGroundY(), junction.getSourceZ() + dz,
                                    junction.getDeltaY(), junction.getDestProjection()));
                        }
                    }
                    start = new StructureStart(structure, source, 0, new PiecesContainer(start.getPieces()));
                }
                box = start.getBoundingBox();
                int edge = generator.worldWidth() / 2 - 16;
                if (box.minX() < -edge || box.maxX() >= edge || box.minZ() < -edge || box.maxZ() >= edge) continue;
                if (box.minY() < chunk.getMinY() || box.maxY() >= chunk.getMaxY()) continue;
                manager.setStartForStructure(SectionPos.bottomOf(chunk), structure, start, chunk);
                indev2.Indev2Mod.LOGGER.info("Guaranteed {} at {} after {} candidate(s)", destination.structures().get(index), box, attempt + 1);
                return;
            }
            throw new IllegalStateException("Cannot fit guaranteed " + destination.id() + " in finite world");
        }
    }
    public static boolean hasTemplate(StructureStart start, StructurePieceSerializationContext context, String suffix) {
        return start.getPieces().stream().anyMatch(piece -> piece.createTag(context).getStringOr("Template", "").endsWith(suffix));
    }
    public static boolean prepare(ServerLevel level, StructureDestination destination) {
        if (READY.containsKey(level)) return true;
        if (destination == StructureDestination.CORAL_REEF) level.getChunk(0, 0);
        for (int index = 0; index < destination.structures().size(); index++) {
            ChunkPos pos = source(destination, index);
            var chunk = level.getChunk(pos.x(), pos.z());
            var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getValue(Identifier.withDefaultNamespace(destination.structures().get(index)));
            StructureStart start = chunk.getStartForStructure(structure);
            if (start == null || !start.isValid()) return false;
            BoundingBox box = start.getBoundingBox();
            for (int x = Math.floorDiv(box.minX(), 16); x <= Math.floorDiv(box.maxX(), 16); x++) {
                for (int z = Math.floorDiv(box.minZ(), 16); z <= Math.floorDiv(box.maxZ(), 16); z++) level.getChunk(x, z);
            }
        }
        READY.put(level, true);
        return true;
    }
    public static void coral(WorldGenLevel level, ChunkGenerator generator) {
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x434f52414cL);
        for (int x = 2; x <= 12; x += 2) for (int z = 2; z <= 12; z += 2) {
            Feature.CORAL_TREE.place(NoneFeatureConfiguration.INSTANCE, level, generator, random, new BlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z), z));
        }
    }
    public static PortalRef arrival(ServerLevel level, BlockPos approximate, Direction.Axis axis, Block portalBlock) {
        var generator = (FiniteIslandGenerator) level.getChunkSource().getGenerator();
        var profile = generator.special();
        int edge = generator.worldWidth() / 2 - 12;
        for (int inset = 0; inset < 4; inset++) for (int side = 0; side < 4; side++) for (int step = 0; step < 24; step++) {
            int reach = edge - inset * Math.min(32, edge / 4);
            int along = Math.clamp(approximate.getX() + (step / 2) * 8 * (step % 2 == 0 ? 1 : -1), -reach, reach);
            int x = side == 0 ? reach : side == 1 ? -reach : along;
            int z = side == 2 ? reach : side == 3 ? -reach : along;
            level.getChunkAt(new BlockPos(x, 0, z));
            int y = Math.max(generator.getSeaLevel() + 1, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            if (profile.settingsId().equals("end") && y <= level.getMinY() + 1) continue;
            if (profile.settingsId().equals("nether")) {
                y = -1;
                for (int candidate = 118; candidate > generator.getSeaLevel(); candidate--) {
                    BlockPos stand = new BlockPos(x, candidate, z);
                    if (level.getBlockState(stand.below()).isSolidRender() && level.getBlockState(stand).isAir()
                            && level.getBlockState(stand.above(3)).isAir()) { y = candidate; break; }
                }
                if (y < 0) continue;
            }
            BlockPos anchor = new BlockPos(x, y, z);
            boolean clear = y + 4 < level.getMaxY();
            for (int a = -1; a <= 2; a++) for (int b = -1; b <= 1; b++) for (int up = -1; up <= 3; up++) {
                BlockPos p = offset(anchor, axis, a, up, b);
                var state = level.getBlockState(p);
                if (!level.getWorldBorder().isWithinBounds(p) || level.getBlockEntity(p) != null) clear = false;
                if (up >= 0 && (!level.getFluidState(p).isEmpty() || !state.canBeReplaced())) clear = false;
                if (up == -1 && !state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)
                        && !state.is(BlockTags.DIRT) && !state.is(BlockTags.SAND) && !state.is(Blocks.GRASS_BLOCK)
                        && !state.is(Blocks.GRAVEL) && !state.is(Blocks.SNOW_BLOCK) && !state.is(Blocks.MOSS_BLOCK)
                        && !state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(Blocks.NETHERRACK)
                        && !state.is(Blocks.END_STONE) && level.getFluidState(p).isEmpty()) clear = false;
            }
            if (!clear) continue;
            for (int a = -1; a <= 2; a++) for (int b = -1; b <= 1; b++)
                level.setBlock(offset(anchor, axis, a, -1, b), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            for (int a = -1; a <= 2; a++) for (int up = 0; up <= 3; up++) {
                if (a == -1 || a == 2 || up == 3) level.setBlock(offset(anchor, axis, a, up, 0), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            }
            var state = portalBlock.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
            for (int a = 0; a <= 1; a++) for (int up = 0; up <= 2; up++)
                level.setBlock(offset(anchor, axis, a, up, 0), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            return new PortalRef(level.dimension().identifier(), anchor, axis);
        }
        return null;
    }
    private static BlockPos offset(BlockPos p, Direction.Axis axis, int a, int y, int b) {
        return axis == Direction.Axis.X ? p.offset(a, y, b) : p.offset(b, y, a);
    }
}
