package dev.zalrpakraft.block;

import dev.zalrpakraft.player.ProgressService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Звёздный стол: ПКМ открывает звёздную карту прогрессии. */
public class StarTableBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 16, 16),
            Block.box(1, 0, 1, 4, 12, 4),
            Block.box(12, 0, 1, 15, 12, 4),
            Block.box(1, 0, 12, 4, 12, 15),
            Block.box(12, 0, 12, 15, 12, 15));

    public StarTableBlock(Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F);
            ProgressService.openMap(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.2 + r.nextDouble() * 0.6, pos.getY() + 1.05,
                    pos.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.01 + r.nextDouble() * 0.02, 0);
        }
    }
}
