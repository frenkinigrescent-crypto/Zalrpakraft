package dev.zalrpakraft.tree;

import dev.zalrpakraft.data.ProgressionMap;
import dev.zalrpakraft.net.StarSyncPayload;
import dev.zalrpakraft.net.TreeViewPayload;
import dev.zalrpakraft.player.Achievements;
import dev.zalrpakraft.player.PlayerProgress;
import dev.zalrpakraft.player.ProgressService;
import dev.zalrpakraft.world.WorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Серверная логика звёздной карты: очки, EXP, разблокировка, блокировка крафта. */
public final class StarService {

    // ---------- запросы гейтинга ----------
    public static boolean isGated(Item item) {
        return TreeBuilder.tree().nodes.containsKey(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    /** Для машин: рецепт работает, если предмет открыт хоть у кого-то. */
    public static boolean machineAllowed(ItemStack result) {
        if (result == null || result.isEmpty()) return true;
        String id = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        if (!TreeBuilder.tree().nodes.containsKey(id)) return true;
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null || WorldState.get(s).anyUnlocked.contains(id);
    }

    public static boolean playerAllowed(ServerPlayer p, ItemStack result) {
        if (result == null || result.isEmpty() || p.isCreative()) return true;
        String id = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        if (!TreeBuilder.tree().nodes.containsKey(id)) return true;
        return ProgressService.of(p).unlockedItems.contains(id);
    }

    // ---------- очки и опыт ----------
    public static void ensureStarted(ServerPlayer p) {
        PlayerProgress pr = ProgressService.of(p);
        if (!pr.starStarted) {
            pr.starStarted = true;
            pr.starPoints += TreeBuilder.tree().startingPoints;
        }
    }

    public static void onCrafted(ServerPlayer p, ItemStack stack) {
        if (stack.isEmpty()) return;
        Tree.Node n = TreeBuilder.tree().nodes.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (n == null) return;
        PlayerProgress pr = ProgressService.of(p);
        if (!pr.unlockedItems.contains(n.item)) return;
        addExp(p, n.exp);
    }

    public static void addExp(ServerPlayer p, int exp) {
        PlayerProgress pr = ProgressService.of(p);
        Tree t = TreeBuilder.tree();
        pr.starExp += exp;
        int gained = 0;
        while (pr.starExp >= t.expPerPoint) {
            pr.starExp -= t.expPerPoint;
            pr.starPoints++;
            gained++;
        }
        if (gained > 0) {
            p.displayClientMessage(Component.translatable("zalrpakraft.star.point", gained).withStyle(ChatFormatting.GOLD), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
        }
        sync(p);
    }

    public static void addPoints(ServerPlayer p, int pts) {
        if (pts <= 0) return;
        ProgressService.of(p).starPoints += pts;
        p.displayClientMessage(Component.translatable("zalrpakraft.star.point", pts).withStyle(ChatFormatting.GOLD), true);
        sync(p);
    }

    /** Доступен ли узел: открыты все части крафта (настоящие связи) и хоть одна связь пути, если они есть. */
    public static boolean available(Tree.Node n, java.util.Set<String> unlocked) {
        for (String par : n.parents) if (!unlocked.contains(par)) return false;
        if (n.soft.isEmpty()) return true;
        for (String par : n.soft) if (unlocked.contains(par)) return true;
        return false;
    }

    public static void unlock(ServerPlayer p, String item) {
        Tree.Node n = TreeBuilder.tree().nodes.get(item);
        if (n == null) return;
        PlayerProgress pr = ProgressService.of(p);
        if (pr.unlockedItems.contains(item)) return;
        Tree.SystemDef sys = TreeBuilder.tree().systems.get(n.system);
        Tree.Planet planet = sys.planets.get(n.planet);
        if (planet.requiresSun > 0) {
            int sun = 0;
            for (String id : sys.planets.get(0).nodes) if (pr.unlockedItems.contains(id)) sun++;
            if (sun < planet.requiresSun) {
                p.displayClientMessage(Component.translatable("zalrpakraft.star.planet_locked", planet.requiresSun).withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        if (!available(n, pr.unlockedItems)) {
            p.displayClientMessage(Component.translatable("zalrpakraft.star.need_parent").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (pr.starPoints < n.cost) {
            p.displayClientMessage(Component.translatable("zalrpakraft.star.no_points").withStyle(ChatFormatting.RED), true);
            return;
        }
        pr.starPoints -= n.cost;
        pr.unlockedItems.add(item);
        WorldState ws = WorldState.get(p.server);
        if (ws.anyUnlocked.add(item)) ws.setDirty();
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.3F);
        Achievements.checkStars(p, pr);
        boolean complete = true;
        for (String id : planet.nodes) if (!pr.unlockedItems.contains(id)) { complete = false; break; }
        if (complete && TreeBuilder.tree().planetCompleteBonus > 0) {
            pr.starPoints += TreeBuilder.tree().planetCompleteBonus;
            p.displayClientMessage(Component.translatable("zalrpakraft.star.planet_complete", planet.name, TreeBuilder.tree().planetCompleteBonus)
                    .withStyle(ChatFormatting.GOLD), false);
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        sync(p);
        openSystem(p, n.system);
    }

    // ---------- синхронизация ----------
    /** Полное состояние для клиента: очки, EXP, открытые предметы и подписи закрытых. */
    public static void sync(ServerPlayer p) {
        PlayerProgress pr = ProgressService.of(p);
        CompoundTag t = new CompoundTag();
        t.putInt("points", pr.starPoints);
        t.putInt("exp", pr.starExp);
        t.putInt("expPerPoint", TreeBuilder.tree().expPerPoint);
        ListTag u = new ListTag();
        for (String s : pr.unlockedItems) u.add(StringTag.valueOf(s));
        t.put("unlocked", u);
        PacketDistributor.sendToPlayer(p, new StarSyncPayload(t));
    }

    /** Список закрываемых предметов и где они на карте (для подсказок и предсказания на клиенте). */
    public static void syncGated(ServerPlayer p) {
        CompoundTag where = new CompoundTag();
        Tree t = TreeBuilder.tree();
        for (Tree.Node n : t.nodes.values()) {
            Tree.SystemDef s = t.systems.get(n.system);
            where.putString(n.item, s.name + " → " + s.planets.get(n.planet).name);
        }
        CompoundTag root = new CompoundTag();
        root.put("gated", where);
        PacketDistributor.sendToPlayer(p, new StarSyncPayload(root));
    }

    public static void openGalaxy(ServerPlayer p) {
        ensureStarted(p);
        PacketDistributor.sendToPlayer(p, new TreeViewPayload("galaxy", TreeJson.galaxy(TreeBuilder.tree(), ProgressService.of(p)),
                ProgressionMap.rawJson(), ProgressService.of(p).write()));
        sync(p);
    }

    public static void openSystem(ServerPlayer p, String system) {
        Tree.SystemDef s = TreeBuilder.tree().systems.get(system);
        if (s == null) return;
        PacketDistributor.sendToPlayer(p, new TreeViewPayload("system", TreeJson.system(TreeBuilder.tree(), s), "", new CompoundTag()));
        sync(p);
    }

    private StarService() {}
}
