package indev2.mixin;

import indev2.portal.BiomePortals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void indev2$finiteNether(ServerLevel level, Entity entity, BlockPos entry,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.level.portal.TeleportTransition> callback) {
        if (indev2.portal.PortalDestination.forLevel(level).isPresent()) {
            indev2.portal.PortalDestination destination = indev2.portal.PortalDestination.forLevel(level).orElseThrow() == indev2.portal.StructureDestination.FORTRESS
                    ? indev2.portal.BiomeDestination.forLevel(level.getServer().overworld()).orElseThrow()
                    : indev2.portal.StructureDestination.FORTRESS;
            callback.setReturnValue(BiomePortals.destination(level, entity, entry, destination));
        }
    }
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void indev2$thrownDye(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effects, boolean precise, CallbackInfo callback) {
        if (level instanceof ServerLevel server && BiomePortals.handleDye(server, pos, entity)) callback.cancel();
    }
}
