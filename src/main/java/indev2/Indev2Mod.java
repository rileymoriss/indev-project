package indev2;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import indev2.world.FiniteIslandGenerator;
import indev2.world.IslandDensity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Indev2Mod implements ModInitializer {
    public static final String MOD_ID = "indev2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath(MOD_ID, "finite_island"), FiniteIslandGenerator.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "island_density"), IslandDensity.CODEC.codec());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (var level : server.getAllLevels()) {
                if (level.getChunkSource().getGenerator() instanceof FiniteIslandGenerator island) {
                    var border = level.getWorldBorder();
                    border.setCenter(0, 0);
                    border.setSize(island.worldWidth());
                    border.setWarningBlocks(16);
                }
            }
        });
        LOGGER.info("Indev++ initialized.");
    }
}
