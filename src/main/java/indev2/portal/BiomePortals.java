package indev2.portal;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import indev2.world.FiniteIslandGenerator;

public final class BiomePortals {
    private static final Map<PortalDestination, Block> BLOCKS = new java.util.HashMap<>();
    private BiomePortals() {}
    public static void register() {
        for (var destination : PortalDestination.all().toList()) {
            if (destination == StructureDestination.FORTRESS) { BLOCKS.put(destination, Blocks.NETHER_PORTAL); continue; }
            Identifier id = Identifier.fromNamespaceAndPath("indev2", destination.id() + "_portal");
            var key = ResourceKey.create(Registries.BLOCK, id);
            DyedPortalBlock block = new DyedPortalBlock(destination,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_PORTAL).setId(key));
            BLOCKS.put(destination, Registry.register(BuiltInRegistries.BLOCK, key, block));
        }
    }
    public static NetherPortalBlock block(PortalDestination destination) { return (NetherPortalBlock) BLOCKS.get(destination); }

    /** Intercept a dye or special activation item instead of sending it through the portal. */
    public static boolean handleDye(ServerLevel level, BlockPos pos, Entity entity) {
        if (!(entity instanceof ItemEntity item) || PortalDestination.forLevel(level).isEmpty()) return false;
        java.util.Optional<PortalDestination> destination = BiomeDestination.forDye(item.getItem()).map(value -> value);
        if (destination.isEmpty()) destination = StructureDestination.forItem(item.getItem()).map(value -> value);
        if (destination.isEmpty()) return false;
        if (item.getPortalCooldown() > 0) return true;
        PortalArea area = PortalArea.find(level, pos);
        if (area == null) return true;
        Block color = block(destination.get());
        if (destination.get() instanceof StructureDestination && !area.blocks().stream().allMatch(part -> level.getBlockState(part).is(Blocks.NETHER_PORTAL))
                && !area.blocks().stream().allMatch(part -> level.getBlockState(part).is(color))) return false;
        if (area.blocks().stream().allMatch(part -> level.getBlockState(part).is(color))) {
            ejectDye(item, area);
            return true;
        }
        area.recolor(level, color);
        ItemStack remaining = item.getItem().copy();
        remaining.shrink(1);
        if (remaining.isEmpty()) item.discard();
        else {
            item.setItem(remaining);
            ejectDye(item, area);
        }
        level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1, 1);
        return true;
    }

    private static void ejectDye(ItemEntity item, PortalArea area) {
        item.setPortalCooldown(40);
        // Also clear matching dye, so it cannot recolour the portal when a later dye arrives.
        BlockPos anchor = area.ref().anchor();
        if (area.ref().axis() == Direction.Axis.X) item.setPos(item.getX(), item.getY(), anchor.getZ() + 1.5);
        else item.setPos(anchor.getX() + 1.5, item.getY(), item.getZ());
        item.setDeltaMovement(Vec3.ZERO);
    }

    public static TeleportTransition destination(ServerLevel source, Entity entity, BlockPos entry, PortalDestination destination) {
        var origin = PortalDestination.forLevel(source);
        if (origin.isEmpty() || origin.get() == destination) return null;
        ServerLevel target = destination.level(source.getServer());
        if (target == null || !(target.getChunkSource().getGenerator() instanceof FiniteIslandGenerator)) return null;
        if (destination instanceof StructureDestination special && !indev2.world.GuaranteedStructures.prepare(target, special)) {
            if (entity instanceof ServerPlayer player) player.sendSystemMessage(Component.translatable("indev2.portal.structure_unavailable"));
            return null;
        }
        PortalArea sourceArea = PortalArea.find(source, entry);
        if (sourceArea == null) return null;
        PortalLinks links = PortalLinks.get(source.getServer());
        PortalRef exit = links.find(sourceArea.ref(), destination);
        PortalArea exitArea = validExit(target, exit, origin.get());
        if (exitArea == null) {
            exit = createArrival(target, sourceArea.ref().anchor(), sourceArea.ref().axis(), origin.get());
            if (exit == null) {
                if (entity instanceof ServerPlayer player) player.sendSystemMessage(Component.translatable("indev2.portal.no_space"));
                return null;
            }
            links.connect(sourceArea.ref(), destination, exit, origin.get());
            exitArea = PortalArea.find(target, exit.anchor());
        }
        // The landing is just outside the portal plane, with zero inherited momentum.
        Vec3 landing = landing(target, exitArea, entity);
        if (landing == null) {
            if (entity instanceof ServerPlayer player) player.sendSystemMessage(Component.translatable("indev2.portal.blocked"));
            return null;
        }
        return new TeleportTransition(target, landing, Vec3.ZERO, entity.getYRot(), entity.getXRot(),
                TeleportTransition.PLAY_PORTAL_SOUND.then(travelled -> {
                    travelled.setPortalCooldown();
                    travelled.placePortalTicket(BlockPos.containing(travelled.position()));
                }));
    }

    private static PortalArea validExit(ServerLevel target, PortalRef ref, PortalDestination returnColor) {
        if (ref == null || !target.dimension().identifier().equals(ref.dimension())) return null;
        PortalArea area = PortalArea.find(target, ref.anchor());
        if (area == null || !area.ref().equals(ref)
                || area.blocks().stream().anyMatch(pos -> !target.getBlockState(pos).is(block(returnColor)))) return null;
        return area;
    }

    private static Vec3 landing(ServerLevel level, PortalArea area, Entity entity) {
        Direction.Axis axis = area.ref().axis();
        for (BlockPos pos : area.blocks()) {
            if (pos.getY() != area.ref().anchor().getY()) continue;
            for (int side : new int[]{1, -1}) {
                BlockPos stand = pos.relative(axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST, side);
                Vec3 candidate = stand.getBottomCenter();
                if (level.getWorldBorder().isWithinBounds(stand)
                        && level.getBlockState(stand.below()).isSolidRender()
                        && level.getFluidState(stand).isEmpty()
                        && level.noCollision(entity, entity.getBoundingBox().move(candidate.subtract(entity.position())))) return candidate;
            }
        }
        // A player's original portal may have no side platform; its own supported interior is safe.
        for (BlockPos pos : area.blocks()) {
            if (pos.getY() != area.ref().anchor().getY()) continue;
            Vec3 candidate = pos.getBottomCenter();
            if (level.getWorldBorder().isWithinBounds(pos) && level.getBlockState(pos.below()).isSolidRender()
                    && level.noCollision(entity, entity.getBoundingBox().move(candidate.subtract(entity.position())))) return candidate;
        }
        return null;
    }

    /** Builds a 2×3 portal and landing pad only where terrain is natural and clearance is replaceable. */
    public static PortalRef createArrival(ServerLevel level, BlockPos approximate, Direction.Axis axis, PortalDestination returnColor) {
        var generator = (FiniteIslandGenerator) level.getChunkSource().getGenerator();
        if (generator.special() != null) return indev2.world.GuaranteedStructures.arrival(level, approximate, axis, block(returnColor));
        int limit = generator.island() ? (int) (generator.radius() * .6) : generator.worldWidth() / 2 - 4;
        int centerX = Math.clamp(approximate.getX(), -limit / 2, limit / 2);
        int centerZ = Math.clamp(approximate.getZ(), -limit / 2, limit / 2);
        for (int ring = 0; ring <= 24; ring++) {
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                int x = centerX + dx * 8, z = centerZ + dz * 8;
                if (generator.island() ? Math.hypot(x, z) > limit : Math.abs(x) > limit || Math.abs(z) > limit) continue;
                // Height queries alone do not generate unloaded chunks, especially on a first visit.
                level.getChunkAt(new BlockPos(x, 0, z));
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos anchor = new BlockPos(x, y, z);
                if (y <= generator.getSeaLevel() || y + 4 >= level.getMaxY() || !canBuild(level, anchor, axis)) continue;
                BlockState portal = block(returnColor).defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
                for (int across = -1; across <= 2; across++) for (int forward = -1; forward <= 1; forward++) {
                    level.setBlock(offset(anchor, axis, across, -1, forward), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                }
                // Complete the frame before filling its interior, exactly as vanilla does.
                for (int across = -1; across <= 2; across++) for (int up = 0; up <= 3; up++) {
                    if (across == -1 || across == 2 || up == 3) {
                        level.setBlock(offset(anchor, axis, across, up, 0), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                for (int across = 0; across <= 1; across++) for (int up = 0; up <= 2; up++) {
                    level.setBlock(offset(anchor, axis, across, up, 0), portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                return new PortalRef(level.dimension().identifier(), anchor, axis);
            }
        }
        return null;
    }

    private static boolean canBuild(ServerLevel level, BlockPos anchor, Direction.Axis axis) {
        for (int across = -1; across <= 2; across++) for (int forward = -1; forward <= 1; forward++) {
            BlockPos floor = offset(anchor, axis, across, -1, forward);
            if (!level.getWorldBorder().isWithinBounds(floor)) return false;
            boolean supported = false;
            for (int depth = 0; depth <= 2; depth++) {
                BlockPos pos = floor.below(depth);
                BlockState state = level.getBlockState(pos);
                if (!level.getFluidState(pos).isEmpty() || level.getBlockEntity(pos) != null) return false;
                if (naturalGround(state)) { supported = true; break; }
                if (!state.isAir() && !state.canBeReplaced()) return false;
            }
            if (!supported) return false;
            for (int up = 0; up <= 3; up++) {
                BlockPos pos = floor.above(up + 1);
                if (!level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty()
                        || level.getBlockEntity(pos) != null || !level.getBlockState(pos).canBeReplaced()) return false;
            }
        }
        return true;
    }
    private static boolean naturalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PALE_MOSS_BLOCK) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK);
    }
    private static BlockPos offset(BlockPos anchor, Direction.Axis axis, int across, int up, int forward) {
        return axis == Direction.Axis.X ? anchor.offset(across, up, forward) : anchor.offset(forward, up, across);
    }
}
