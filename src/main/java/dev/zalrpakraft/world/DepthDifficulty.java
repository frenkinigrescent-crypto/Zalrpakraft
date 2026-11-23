package dev.zalrpakraft.world;

import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.ZServerConfig;
import dev.zalrpakraft.data.BossTable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Пещеры обычного мира: чем ниже, тем дольше копать, сильнее голод и износ,
 * а монстры, появившиеся на глубине, крепче, злее и дают больше опыта.
 */
public final class DepthDifficulty {
    public static final ResourceLocation HEALTH = Zalrpakraft.id("depth_health");
    public static final ResourceLocation DAMAGE = Zalrpakraft.id("depth_damage");
    public static final ResourceLocation ARMOR = Zalrpakraft.id("depth_armor");
    public static final ResourceLocation SPEED = Zalrpakraft.id("depth_speed");
    private static final String TAG = "zalrpakraft_depth";
    private static final Map<UUID, Integer> TIER = new HashMap<>();

    /** 0 — выше startY, 1 — дно мира. Вне выбранных измерений — всегда 0. */
    public static float depth(Level level, double y) {
        if (!ZServerConfig.SPEC.isLoaded() || !ZServerConfig.CAVES_ENABLED.get()) return 0f;
        if (!ZServerConfig.CAVES_DIMENSIONS.get().contains(level.dimension().location().toString())) return 0f;
        int start = ZServerConfig.CAVES_START_Y.get();
        int bottom = level.getMinBuildHeight();
        if (start <= bottom) return 0f;
        return Mth.clamp((float) ((start - y) / (start - bottom)), 0f, 1f);
    }

    /** Во сколько раз медленнее копать на этой глубине (1 — как обычно). */
    public static float miningFactor(Level level, double y) {
        return 1f - (float) (ZServerConfig.SPEC.isLoaded() ? ZServerConfig.CAVES_MAX_SLOWDOWN.get() : 0) * depth(level, y);
    }

    // ---------- копание ----------
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        Player p = e.getEntity();
        if (p.isCreative() || e.getPosition().isEmpty()) return;
        float f = miningFactor(p.level(), e.getPosition().get().getY());
        if (f < 1f) e.setNewSpeed(e.getNewSpeed() * f);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || p.isCreative()) return;
        float d = depth(p.level(), e.getPos().getY());
        if (d <= 0f) return;
        if (e.getState().getDestroySpeed(e.getLevel(), e.getPos()) <= 0f) return; // факелы, трава — без штрафа
        p.causeFoodExhaustion((float) (ZServerConfig.CAVES_HUNGER.get() * d));
        ItemStack tool = p.getMainHandItem();
        if (tool.isDamageableItem() && p.getRandom().nextFloat() < ZServerConfig.CAVES_TOOL_WEAR.get() * d) {
            tool.hurtAndBreak(1, p, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
    }

    // ---------- монстры ----------
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || e.loadedFromDisk()) return;
        if (!(e.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)) return;
        buff(mob);
    }

    /** Усиливает монстра по глубине, где он появился. Возвращает долю глубины (0 — не тронут). */
    public static float buff(Mob mob) {
        float d = depth(mob.level(), mob.getY());
        if (d <= 0f) return 0f;
        if (mob.getType().is(Tags.EntityTypes.BOSSES) || BossTable.find(BossTable.idOf(mob.getType())) != null) return 0f;
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null || hp.hasModifier(HEALTH)) return 0f;
        add(mob, Attributes.MAX_HEALTH, HEALTH, ZServerConfig.CAVES_MOB_HEALTH.get() * d, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add(mob, Attributes.ATTACK_DAMAGE, DAMAGE, ZServerConfig.CAVES_MOB_DAMAGE.get() * d, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add(mob, Attributes.ARMOR, ARMOR, ZServerConfig.CAVES_MOB_ARMOR.get() * d, AttributeModifier.Operation.ADD_VALUE);
        add(mob, Attributes.MOVEMENT_SPEED, SPEED, ZServerConfig.CAVES_MOB_SPEED.get() * d, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mob.setHealth(mob.getMaxHealth());
        mob.getPersistentData().putFloat(TAG, d);
        return d;
    }

    private static void add(LivingEntity le, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        if (amount <= 0) return;
        AttributeInstance a = le.getAttribute(attr);
        if (a != null && !a.hasModifier(id)) a.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    @SubscribeEvent
    public static void onXp(LivingExperienceDropEvent e) {
        float d = e.getEntity().getPersistentData().getFloat(TAG);
        if (d > 0f && ZServerConfig.SPEC.isLoaded()) {
            e.setDroppedExperience((int) Math.round(e.getDroppedExperience() * (1.0 + ZServerConfig.CAVES_MOB_XP.get() * d)));
        }
    }

    // ---------- записи путника при спуске ----------
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        if (!ZServerConfig.SPEC.isLoaded() || !ZServerConfig.CAVES_MESSAGES.get()) return;
        float d = depth(p.level(), p.getY());
        int tier = d >= 0.85f ? 3 : d >= 0.55f ? 2 : d >= 0.2f ? 1 : 0;
        Integer last = TIER.put(p.getUUID(), tier);
        if (last != null && tier > last) {
            p.displayClientMessage(Component.translatable("zalrpakraft.depth.tier" + tier).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
    }

    private DepthDifficulty() {}
}
