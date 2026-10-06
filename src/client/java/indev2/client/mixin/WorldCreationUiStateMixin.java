package indev2.client.mixin;

import indev2.client.FiniteIslandScreen;
import indev2.world.FiniteIslandGenerator;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @Inject(method = "getPresetEditor", at = @At("HEAD"), cancellable = true)
    private void indev2$islandEditor(CallbackInfoReturnable<PresetEditor> callback) {
        WorldCreationUiState state = (WorldCreationUiState) (Object) this;
        if (state.getSettings().selectedDimensions().overworld() instanceof FiniteIslandGenerator) {
            callback.setReturnValue(FiniteIslandScreen::new);
        }
    }
}
