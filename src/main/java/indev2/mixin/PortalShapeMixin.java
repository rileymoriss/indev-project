package indev2.mixin;

import indev2.portal.DyedPortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PortalShape.class)
public abstract class PortalShapeMixin {
    @Redirect(method = {"isEmpty", "getDistanceUntilTop"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private static boolean indev2$coloredPortal(BlockState state, Object block) {
        return state.is((Block) block) || block == Blocks.NETHER_PORTAL && state.getBlock() instanceof DyedPortalBlock;
    }
}
