package dev.zalrpakraft.item;

import dev.zalrpakraft.world.WorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockPattern;

import java.util.List;
import java.util.Map;

/** Один из 12 глаз. Вставляется в рамку портала Энда; в одном портале каждый глаз — только один раз. */
public class EndEyeItem extends Item {
    private final int index;

    public EndEyeItem(Properties p, int index) {
        super(p);
        this.index = index;
    }

    public int index() {
        return index;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.END_PORTAL_FRAME) || state.getValue(EndPortalFrameBlock.HAS_EYE)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        WorldState ws = WorldState.get(((ServerLevel) level).getServer());
        // тот же глаз уже стоит в этом портале?
        for (Map.Entry<BlockPos, Integer> e : ws.frameEyes.entrySet()) {
            if (e.getValue() == index && e.getKey().distManhattan(pos) <= 8
                    && level.getBlockState(e.getKey()).is(Blocks.END_PORTAL_FRAME)
                    && level.getBlockState(e.getKey()).getValue(EndPortalFrameBlock.HAS_EYE)) {
                if (ctx.getPlayer() != null)
                    ctx.getPlayer().displayClientMessage(Component.translatable("zalrpakraft.eye.duplicate").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
        }

        BlockState filled = state.setValue(EndPortalFrameBlock.HAS_EYE, true);
        Block.pushEntitiesUp(state, filled, level, pos);
        level.setBlock(pos, filled, 2);
        level.updateNeighbourForOutputSignal(pos, Blocks.END_PORTAL_FRAME);
        ws.frameEyes.put(pos.immutable(), index);
        ws.setDirty();
        ctx.getItemInHand().shrink(1);
        level.levelEvent(1503, pos, 0);

        BlockPattern.BlockPatternMatch match = EndPortalFrameBlock.getOrCreatePortalShape().find(level, pos);
        if (match != null) {
            BlockPos corner = match.getFrontTopLeft().offset(-3, 0, -3);
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++)
                    level.setBlock(corner.offset(i, 0, j), Blocks.END_PORTAL.defaultBlockState(), 2);
            level.globalLevelEvent(1038, corner.offset(1, 0, 1), 0);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("zalrpakraft.eye.use", index + 1).withStyle(ChatFormatting.DARK_PURPLE));
    }
}
