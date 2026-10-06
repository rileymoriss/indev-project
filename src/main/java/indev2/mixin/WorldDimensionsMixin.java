package indev2.mixin;

import com.mojang.serialization.Lifecycle;
import indev2.portal.BiomeDestination;
import indev2.world.FiniteIslandGenerator;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
    // Also applies when older finite saves are loaded with the newly added dimension datapack.
    @Inject(method = "bake", at = @At("RETURN"), cancellable = true)
    private void indev2$sharedIslandSizes(Registry<LevelStem> base, CallbackInfoReturnable<WorldDimensions.Complete> callback) {
        var result = callback.getReturnValue();
        var overworld = result.dimensions().getValue(LevelStem.OVERWORLD);
        FiniteIslandGenerator home = overworld != null && overworld.generator() instanceof FiniteIslandGenerator island ? island : null;
        var homeDestination = home == null ? java.util.Optional.<BiomeDestination>empty() : BiomeDestination.forBiome(home.biome());
        MappedRegistry<LevelStem> updated = new MappedRegistry<>(Registries.LEVEL_STEM, homeDestination.isPresent() ? Lifecycle.experimental() : Lifecycle.stable());
        for (var entry : result.dimensions().entrySet()) {
            LevelStem stem = entry.getValue();
            BiomeDestination destination = null;
            for (var candidate : BiomeDestination.values()) {
                if (entry.getKey().identifier().equals(candidate.dimensionId())) destination = candidate;
            }
            indev2.portal.StructureDestination special = null;
            for (var candidate : indev2.portal.StructureDestination.values()) {
                if (entry.getKey().identifier().equals(candidate.dimensionId())) special = candidate;
            }
            if (special != null && homeDestination.isEmpty()) continue;
            if (special != null && stem.generator() instanceof FiniteIslandGenerator island) {
                stem = new LevelStem(stem.type(), island.resized(home.radius(), home.oceanMargin(), false));
            }
            // The Overworld already is this biome's destination; do not create a duplicate island.
            if (destination != null && (homeDestination.isEmpty() || destination == homeDestination.get())) continue;
            if (destination != null && stem.generator() instanceof FiniteIslandGenerator island) {
                stem = new LevelStem(stem.type(), island.resized(home.radius(), home.oceanMargin(), home.island()));
            }
            updated.register(entry.getKey(), stem, result.dimensions().registrationInfo(entry.getKey()).orElse(RegistrationInfo.BUILT_IN));
        }
        callback.setReturnValue(new WorldDimensions.Complete(updated.freeze(), result.specialWorldProperty()));
    }
}
