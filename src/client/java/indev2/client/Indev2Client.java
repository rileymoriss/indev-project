package indev2.client;

import java.util.List;
import indev2.portal.BiomeDestination;
import indev2.portal.BiomePortals;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSources;

public final class Indev2Client implements ClientModInitializer {
    @Override public void onInitializeClient() {
        for (var destination : indev2.portal.PortalDestination.all().filter(destination -> destination != indev2.portal.StructureDestination.FORTRESS).toList()) {
            BlockColorRegistry.register(List.of(BlockTintSources.constant(0xff000000 | destination.color())),
                    BiomePortals.block(destination));
        }
    }
}
