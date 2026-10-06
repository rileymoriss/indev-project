package indev2.portal;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import indev2.world.FiniteIslandGenerator;

public interface PortalDestination {
    String id();
    int color();
    ServerLevel level(MinecraftServer server);
    static Stream<PortalDestination> all() {
        return Stream.concat(Stream.of(BiomeDestination.values()), Stream.of(StructureDestination.values()));
    }
    static Optional<PortalDestination> forLevel(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof FiniteIslandGenerator finite && finite.special() != null)
            return Optional.of(finite.special());
        return BiomeDestination.forLevel(level).map(value -> value);
    }
}
