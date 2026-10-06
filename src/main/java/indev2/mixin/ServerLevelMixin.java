package indev2.mixin;

import indev2.world.FiniteIslandGenerator;
import java.util.concurrent.Executor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static LevelStem indev2$selectVariant(LevelStem stem, MinecraftServer server, Executor executor,
            net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess storage,
            net.minecraft.world.level.storage.ServerLevelData data, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            LevelStem original, boolean debug, long biomeSeed, java.util.List<net.minecraft.world.level.CustomSpawner> spawners, boolean tickTime) {
        if (stem.generator() instanceof FiniteIslandGenerator finite) {
            return new LevelStem(stem.type(), finite.resolveVariant(server.getWorldGenSettings().options().seed(), server.registryAccess()));
        }
        return stem;
    }
}
