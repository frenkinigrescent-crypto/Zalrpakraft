package dev.zalrpakraft.event;

import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.ZConfig;
import dev.zalrpakraft.data.BossTable;
import dev.zalrpakraft.data.ProgressionMap;
import dev.zalrpakraft.net.SimplePayload;
import dev.zalrpakraft.player.PlayerProgress;
import dev.zalrpakraft.player.ProgressService;
import dev.zalrpakraft.registry.ZItems;
import dev.zalrpakraft.world.HouseBuilder;
import dev.zalrpakraft.world.WorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommonEvents {
    private static final ResourceLocation HEALTH_MOD = Zalrpakraft.id("boss_health");
    private static final ResourceLocation DAMAGE_MOD = Zalrpakraft.id("boss_damage");
    private static final Map<UUID, Long> LAST_LOCK_MSG = new HashMap<>();

    // ---------- Запуск сервера: домик, проверка таблиц, самотест ----------
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        MinecraftServer server = e.getServer();
        HouseBuilder.ensureBuilt(server.overworld());
        dev.zalrpakraft.tree.TreeBuilder.build(server);
        dev.zalrpakraft.data.QuestBook.validate();
        int found = BossTable.validate();
        Zalrpakraft.LOG.info("Zalepacraft: найдено боссов из таблицы в сборке: {}", found);

        String selftest = System.getProperty("zalrpakraft.selftest", "");
        if (!selftest.isEmpty()) runSelfTest(server, Path.of(selftest));
    }

    private static void runSelfTest(MinecraftServer server, Path out) {
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        WorldState ws = WorldState.get(server);
        sb.append("house=").append(ws.housePos).append('\n');
        boolean table = ws.tablePos != null && server.overworld().getBlockState(ws.tablePos).getBlock() == dev.zalrpakraft.registry.ZBlocks.STAR_TABLE.get();
        sb.append("table_placed=").append(table).append('\n');
        ok &= table;
        int nodes = ProgressionMap.nodes().size();
        sb.append("map_nodes=").append(nodes).append('\n');
        ok &= nodes > 20;
        for (ProgressionMap.Node n : ProgressionMap.nodes().values()) {
            for (String r : n.requires) if (!ProgressionMap.nodes().containsKey(r)) { ok = false; sb.append("bad_require ").append(n.id).append("->").append(r).append('\n'); }
            for (String r : n.requiresAny) if (!ProgressionMap.nodes().containsKey(r)) { ok = false; sb.append("bad_require ").append(n.id).append("->").append(r).append('\n'); }
        }
        sb.append("recipes=").append(server.getRecipeManager().getRecipes().stream()
                .filter(h -> h.id().getNamespace().equals(Zalrpakraft.MODID)).count()).append('\n');
        long adv = server.getAdvancements().getAllAdvancements().stream()
                .filter(h -> h.id().getNamespace().equals(Zalrpakraft.MODID)).count();
        sb.append("advancements=").append(adv).append('\n');
        int quests = dev.zalrpakraft.data.QuestBook.book().quests.size();
        sb.append("quests_valid=").append(quests).append(" of ").append(76).append('\n');
        ok &= quests >= 20;
        ok &= adv >= 16;
        dev.zalrpakraft.tree.Tree tree = dev.zalrpakraft.tree.TreeBuilder.tree();
        sb.append("tree_systems=").append(tree.systems.size()).append(" nodes=").append(tree.nodes.size()).append('\n');
        for (var s : tree.systems.values()) {
            sb.append("  system ").append(s.id).append(" (").append(s.name).append("):");
            for (var pl : s.planets) sb.append(' ').append(pl.name).append('=').append(pl.nodes.size());
            sb.append('\n');
        }
        ok &= tree.nodes.size() > 50;
        // гейтинг: печь не плавит закрытый результат, пока его никто не открыл
        var input = new net.minecraft.world.item.crafting.SingleRecipeInput(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        boolean gatedIron = tree.nodes.containsKey("minecraft:iron_ingot");
        boolean before = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, server.overworld()).isPresent();
        ws.anyUnlocked.add("minecraft:iron_ingot");
        boolean after = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, server.overworld()).isPresent();
        ws.anyUnlocked.remove("minecraft:iron_ingot");
        sb.append("gate_test iron_gated=").append(gatedIron).append(" smelt_before=").append(before).append(" smelt_after_unlock=").append(after).append('\n');
        ok &= gatedIron && !before && after;
        sb.append(ok ? "RESULT OK\n" : "RESULT FAIL\n");
        try {
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            Zalrpakraft.LOG.error("selftest write failed", ex);
        }
        Zalrpakraft.LOG.info("Zalepacraft selftest:\n{}", sb);
        server.halt(false);
    }

    // ---------- Первый вход: пробуждение и дневник ----------
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PlayerProgress pr = ProgressService.of(sp);
        if (!pr.introSeen) {
            pr.introSeen = true;
            ItemStack book = new ItemStack(ZItems.STRANGER_JOURNAL.get());
            int slot = sp.getInventory().selected;
            if (sp.getInventory().getItem(slot).isEmpty()) sp.getInventory().setItem(slot, book);
            else if (!sp.getInventory().add(book)) sp.drop(book, false);
            if (ZConfig.INTRO_ENABLED.get()) PacketDistributor.sendToPlayer(sp, new SimplePayload("intro"));
        }

        pr.visitedDims.add(sp.level().dimension().location().toString());
        dev.zalrpakraft.tree.StarService.ensureStarted(sp);
        dev.zalrpakraft.tree.StarService.syncGated(sp);
        dev.zalrpakraft.tree.StarService.sync(sp);
        ProgressService.update(sp);
        ProgressService.sync(sp);
    }

    @SubscribeEvent
    public static void onChangedDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ProgressService.of(sp).visitedDims.add(e.getTo().location().toString());
        if (ProgressService.update(sp)) ProgressService.sync(sp);
        else ProgressService.sync(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) ProgressService.sync(sp);
    }

    // ---------- Раз в 2 секунды: домик найден? новые предметы модов? ----------
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 40 != 0) return;
        PlayerProgress pr = ProgressService.of(sp);
        boolean dirty = false;
        // «Пробуждение» — после анимации, чтобы плашка не легла поверх неё
        if (sp.tickCount > 240) dev.zalrpakraft.player.Achievements.award(sp, "root");
        if (pr.journalRead && !pr.houseFound) {
            WorldState ws = WorldState.get(sp.server);
            if (ws.housePos != null && sp.level().dimension() == Level.OVERWORLD
                    && sp.blockPosition().distSqr(ws.housePos) < 12 * 12) {
                pr.houseFound = true;
                dev.zalrpakraft.player.Achievements.award(sp, "house");
                sp.displayClientMessage(Component.translatable("zalrpakraft.house.found").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
                dirty = true;
            }
        }
        if (pr.tableOpened && ProgressService.update(sp)) dirty = true;
        if (dev.zalrpakraft.player.QuestService.check(sp)) dirty = true;
        if (!pr.tracked.isEmpty()) dirty = true; // счётчики в HUD
        // глаза в инвентаре
        for (ItemStack s : sp.getInventory().items) {
            if (s.getItem() instanceof dev.zalrpakraft.item.EndEyeItem eye) {
                if (pr.eyesSeen.add(String.valueOf(eye.index()))) {
                    dev.zalrpakraft.player.Achievements.award(sp, "first_eye");
                    if (pr.eyesSeen.size() >= 12) dev.zalrpakraft.player.Achievements.award(sp, "twelve_eyes");
                }
            }
        }
        if (dirty) ProgressService.sync(sp);
    }

    // ---------- Замки на измерения ----------
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) return;
        String dim = e.getDimension().location().toString();
        boolean locked = (ZConfig.LOCK_NETHER.get() && dim.equals("minecraft:the_nether"))
                || (ZConfig.LOCK_PARADISE.get() && dim.equals("paradise_lost:paradise_lost"));
        if (!locked) return;
        // возвращаться домой можно всегда
        if (ProgressService.of(sp).unlockedDims.contains(dim)) return;
        e.setCanceled(true);
        long now = sp.level().getGameTime();
        Long last = LAST_LOCK_MSG.get(sp.getUUID());
        if (last == null || now - last > 60) {
            LAST_LOCK_MSG.put(sp.getUUID(), now);
            String key = dim.equals("minecraft:the_nether") ? "zalrpakraft.lock.nether" : "zalrpakraft.lock.paradise";
            sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    // ---------- Обычное око Эндера больше не вставляется ----------
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!ZConfig.CUSTOM_END_EYES.get()) return;
        if (!e.getItemStack().is(Items.ENDER_EYE)) return;
        if (!e.getLevel().getBlockState(e.getPos()).is(Blocks.END_PORTAL_FRAME)) return;
        e.setCanceled(true);
        if (!e.getLevel().isClientSide)
            e.getEntity().displayClientMessage(Component.translatable("zalrpakraft.eye.vanilla").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    // ---------- Усиление боссов ----------
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof LivingEntity le) || le instanceof net.minecraft.world.entity.player.Player) return;
        if (!isBoss(le)) return;
        AttributeInstance hp = le.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null || hp.hasModifier(HEALTH_MOD)) return;

        int players = 0;
        if (e.getLevel() instanceof ServerLevel sl) {
            double r = ZConfig.BOSS_PLAYER_RADIUS.get();
            players = sl.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(le) < r * r).size();
        }
        double mult = ZConfig.BOSS_HEALTH_MULT.get() * (1.0 + ZConfig.BOSS_HEALTH_PER_PLAYER.get() * Math.max(0, players - 1));
        hp.addPermanentModifier(new AttributeModifier(HEALTH_MOD, mult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        le.setHealth(le.getMaxHealth());
        AttributeInstance dmg = le.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && !dmg.hasModifier(DAMAGE_MOD)) {
            dmg.addPermanentModifier(new AttributeModifier(DAMAGE_MOD, ZConfig.BOSS_DAMAGE_MULT.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static boolean isBoss(LivingEntity le) {
        ResourceLocation id = BossTable.idOf(le.getType());
        BossTable.Entry entry = BossTable.find(id);
        boolean tagged = le.getType().is(Tags.EntityTypes.BOSSES);
        if (entry != null && !entry.wildcard()) return true;
        if (entry != null) return tagged || le.getMaxHealth() >= 150;
        return ZConfig.SCALE_TAGGED_BOSSES.get() && tagged;
    }

    // ---------- Лут с боссов + засчитать метеор ----------
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide || !(le.level() instanceof ServerLevel sl)) return;
        ResourceLocation id = BossTable.idOf(le.getType());
        BossTable.Entry entry = BossTable.find(id);
        if (entry == null) return;
        if (entry.wildcard() && !(le.getType().is(Tags.EntityTypes.BOSSES) || le.getMaxHealth() >= 150)) return;

        double r = ZConfig.BOSS_PLAYER_RADIUS.get();
        List<ServerPlayer> near = sl.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(le) < r * r);
        if (near.isEmpty() && e.getSource().getEntity() instanceof ServerPlayer killer) near = List.of(killer);

        Item drop = BossTable.dropItem(entry);
        if (drop != null) {
            int copies = Math.max(1, near.size()) * Math.max(1, entry.count());
            for (int i = 0; i < copies; i++) {
                ItemEntity ie = new ItemEntity(sl, le.getX(), le.getY() + 0.5, le.getZ(), new ItemStack(drop));
                ie.setGlowingTag(true);
                ie.setUnlimitedLifetime();
                e.getDrops().add(ie);
            }
        }
        int fighters = sl.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(le) < 64 * 64).size();
        for (ServerPlayer p : near) {
            ProgressService.of(p).killCounts.merge(id.toString(), 1, Integer::sum);
            dev.zalrpakraft.player.QuestService.check(p);
            if (fighters >= 4) dev.zalrpakraft.player.Achievements.award(p, "fellowship");
            if (!entry.wildcard() || le.getType().is(Tags.EntityTypes.BOSSES))
                dev.zalrpakraft.tree.StarService.addPoints(p, dev.zalrpakraft.tree.TreeBuilder.tree().pointsPerMeteor);
            PlayerProgress pr = ProgressService.of(p);
            pr.kills.add(id.toString());
            ProgressService.update(p);
            ProgressService.sync(p);
        }
    }

    // ---------- Счётчик убийств для заданий (боссы считаются в onDrops для всех рядом) ----------
    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer)) return;
        LivingEntity le = e.getEntity();
        if (le instanceof net.minecraft.world.entity.player.Player) return;
        ResourceLocation id = BossTable.idOf(le.getType());
        BossTable.Entry boss = BossTable.find(id);
        if (boss != null && (!boss.wildcard() || le.getType().is(Tags.EntityTypes.BOSSES) || le.getMaxHealth() >= 150)) return;
        ProgressService.of(killer).killCounts.merge(id.toString(), 1, Integer::sum);
    }

    // ---------- Звёздная карта: перестроить после /reload, EXP за крафт ----------
    @SubscribeEvent
    public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent e) {
        if (e.getPlayer() == null) dev.zalrpakraft.tree.TreeBuilder.build(e.getPlayerList().getServer());
        e.getRelevantPlayers().forEach(p -> {
            dev.zalrpakraft.tree.StarService.syncGated(p);
            dev.zalrpakraft.tree.StarService.sync(p);
        });
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) dev.zalrpakraft.tree.StarService.onCrafted(sp, e.getCrafting());
    }

    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) dev.zalrpakraft.tree.StarService.onCrafted(sp, e.getSmelting());
    }

    private CommonEvents() {}
}
