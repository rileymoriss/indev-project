package indev2.mixin;

import indev2.world.FiniteIslandGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureManager.class)
public abstract class StructureManagerMixin {
    @Shadow @Final private LevelAccessor level;
    @Inject(method = "shouldGenerateStructures", at = @At("HEAD"), cancellable = true)
    private void indev2$guaranteedStructures(CallbackInfoReturnable<Boolean> callback) {
        ServerLevel server = level instanceof ServerLevel direct ? direct : level instanceof WorldGenRegion region ? region.getLevel() : null;
        if (server != null && server.getChunkSource().getGenerator() instanceof FiniteIslandGenerator finite && finite.special() != null)
            callback.setReturnValue(true);
    }
}
