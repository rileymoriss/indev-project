package indev2.mixin;

import indev2.world.FiniteIslandGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    // Keep vanilla's safe-spawn search and bonus chest, but start on our central land.
    @Redirect(method = "setInitialSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Climate$Sampler;findSpawnPosition()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos indev2$centralSpawn(Climate.Sampler sampler, ServerLevel level,
            ServerLevelData data, boolean bonusChest, boolean debug, LevelLoadListener listener) {
        return level.getChunkSource().getGenerator() instanceof FiniteIslandGenerator
                ? BlockPos.ZERO : sampler.findSpawnPosition();
    }
}
