package indev2.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** The lower corner of a portal's interior, independent of which block an entity touched. */
public record PortalRef(Identifier dimension, BlockPos anchor, Direction.Axis axis) {
    public static final Codec<PortalRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("dimension").forGetter(PortalRef::dimension),
            BlockPos.CODEC.fieldOf("anchor").forGetter(PortalRef::anchor),
            Direction.Axis.CODEC.fieldOf("axis").forGetter(PortalRef::axis)
    ).apply(i, PortalRef::new));

    public ServerLevel level(MinecraftServer server) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }
}
