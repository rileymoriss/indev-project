package indev2.portal;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;

/** The block variant stores its destination durably in normal chunk block data. */
public final class DyedPortalBlock extends NetherPortalBlock {
    private final BiomeDestination destination;
    private final MapCodec<NetherPortalBlock> codec;

    public DyedPortalBlock(BiomeDestination destination, BlockBehaviour.Properties properties) {
        super(properties);
        this.destination = destination;
        codec = simpleCodec(props -> new DyedPortalBlock(destination, props));
    }
    public BiomeDestination destination() { return destination; }
    @Override public MapCodec<NetherPortalBlock> codec() { return codec; }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {}
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effects, boolean precise) {
        if (level instanceof ServerLevel server && BiomePortals.handleDye(server, pos, entity)) return;
        super.entityInside(state, level, pos, entity, effects, precise);
    }
    @Override public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos entry) {
        return BiomePortals.destination(level, entity, entry, destination);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, .5F, random.nextFloat() * .4F + .8F, false);
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(new DustParticleOptions(destination == BiomeDestination.PALE_GARDEN ? 0xffffff : destination.color(), .8F),
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, .02, 0);
        }
    }
}
