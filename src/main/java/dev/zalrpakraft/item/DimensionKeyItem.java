package dev.zalrpakraft.item;

import dev.zalrpakraft.player.ProgressService;
import dev.zalrpakraft.registry.ZAttachments;
import dev.zalrpakraft.player.PlayerProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Ключ измерения: ПКМ навсегда открывает измерение для этого игрока. */
public class DimensionKeyItem extends Item {
    private final String dimension;

    public DimensionKeyItem(Properties p, String dimension) {
        super(p);
        this.dimension = dimension;
    }

    public String dimension() {
        return dimension;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        PlayerProgress pr = sp.getData(ZAttachments.PROGRESS);
        if (pr.unlockedDims.contains(dimension)) {
            sp.displayClientMessage(Component.translatable("zalrpakraft.key.already").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        pr.unlockedDims.add(dimension);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, sp.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
        sp.displayClientMessage(Component.translatable("zalrpakraft.key.unlocked." + getDescriptionId().substring(getDescriptionId().lastIndexOf('.') + 1))
                .withStyle(ChatFormatting.GOLD), false);
        dev.zalrpakraft.player.Achievements.award(sp, dimension.startsWith("minecraft:") ? "nether_key" : "paradise_key");
        ProgressService.sync(sp);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("zalrpakraft.key.use").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
