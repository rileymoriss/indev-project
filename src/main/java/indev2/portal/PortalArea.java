package indev2.portal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;

public record PortalArea(PortalRef ref, List<BlockPos> blocks) {
    public static PortalArea find(ServerLevel level, BlockPos entry) {
        BlockState initial = level.getBlockState(entry);
        if (!(initial.getBlock() instanceof NetherPortalBlock)) return null;
        Direction.Axis axis = initial.getValue(NetherPortalBlock.AXIS);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        List<BlockPos> blocks = new ArrayList<>();
        pending.add(entry.immutable());
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (!seen.add(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof NetherPortalBlock) || state.getValue(NetherPortalBlock.AXIS) != axis) continue;
            blocks.add(pos);
            if (blocks.size() > 21 * 21) return null;
            pending.add(pos.above()); pending.add(pos.below());
            pending.add(pos.relative(axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH));
            pending.add(pos.relative(axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH));
        }
        if (blocks.isEmpty() || !PortalShape.findAnyShape(level, entry, axis).isComplete()) return null;
        int minX = blocks.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int minY = blocks.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int minZ = blocks.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        return new PortalArea(new PortalRef(level.dimension().identifier(), new BlockPos(minX, minY, minZ), axis), List.copyOf(blocks));
    }
    public void recolor(ServerLevel level, DyedPortalBlock block) {
        BlockState state = block.defaultBlockState().setValue(NetherPortalBlock.AXIS, ref.axis());
        // Apply the whole interior before neighbour validation, avoiding a partial portal collapse.
        for (BlockPos pos : blocks) level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
