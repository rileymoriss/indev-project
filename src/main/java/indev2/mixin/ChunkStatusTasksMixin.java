package indev2.mixin;

import indev2.world.FiniteIslandGenerator;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {
    @Redirect(method = "generateStructureStarts", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldOptions;generateStructures()Z"))
    private static boolean indev2$guaranteedStructures(WorldOptions options, WorldGenContext context, ChunkStep step,
            StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        return options.generateStructures() || context.generator() instanceof FiniteIslandGenerator finite && finite.special() != null;
    }
}
