package indev2.mixin;

import indev2.world.FiniteIslandGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow @Final @Mutable private net.minecraft.world.level.levelgen.WorldGenSettings worldGenSettings;

    // Datapack dimensions are not automatically part of saved worldgen settings. Persist
    // every resolved generator so future palette changes cannot reroll an existing world.
    @Inject(method = "createLevels", at = @At("RETURN"))
    private void indev2$saveResolvedDimensions(CallbackInfo callback) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (!(server.overworld().getChunkSource().getGenerator() instanceof FiniteIslandGenerator)) return;
        var dimensions = new java.util.HashMap<net.minecraft.resources.ResourceKey<net.minecraft.world.level.dimension.LevelStem>, net.minecraft.world.level.dimension.LevelStem>();
        var registry = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM);
        for (var level : server.getAllLevels()) {
            var key = net.minecraft.core.registries.Registries.levelToLevelStem(level.dimension());
            var stem = registry.getOrThrow(key).value();
            dimensions.put(key, new net.minecraft.world.level.dimension.LevelStem(stem.type(), level.getChunkSource().getGenerator()));
        }
        worldGenSettings = new net.minecraft.world.level.levelgen.WorldGenSettings(worldGenSettings.options(),
                new net.minecraft.world.level.levelgen.WorldDimensions(dimensions));
        server.getDataStorage().set(net.minecraft.world.level.levelgen.WorldGenSettings.TYPE, worldGenSettings);
    }

    // Keep vanilla's safe-spawn search and bonus chest, but start within our bounded world.
    @Redirect(method = "setInitialSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Climate$Sampler;findSpawnPosition()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos indev2$centralSpawn(Climate.Sampler sampler, ServerLevel level,
            ServerLevelData data, boolean bonusChest, boolean debug, LevelLoadListener listener) {
        return level.getChunkSource().getGenerator() instanceof FiniteIslandGenerator
                ? BlockPos.ZERO : sampler.findSpawnPosition();
    }
}
