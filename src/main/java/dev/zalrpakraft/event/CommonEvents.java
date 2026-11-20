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
        int found = BossTable.validate();
        Zalrpakraft.LOG.info("Zalrpakraft: найдено боссов из таблицы в сборке: {}", found);

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
        ok &= nodes > 40;
        for (ProgressionMap.Node n : ProgressionMap.nodes().values()) {
            for (String r : n.requires) if (!ProgressionMap.nodes().containsKey(r)) { ok = false; sb.append("bad_require ").append(n.id).append("->").append(r).append('\n'); }
            for (String r : n.requiresAny) if (!ProgressionMap.nodes().containsKey(r)) { ok = false; sb.append("bad_require ").append(n.id).append("->").append(r).append('\n'); }
        }
        sb.append("recipes=").append(server.getRecipeManager().getRecipes().stream()
                .filter(h -> h.id().getNamespace().equals(Zalrpakraft.MODID)).count()).append('\n');
        sb.append(ok ? "RESULT OK\n" : "RESULT FAIL\n");
        try {
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            Zalrpakraft.LOG.error("selftest write failed", ex);
        }
        Zalrpakraft.LOG.info("Zalrpakraft selftest:\n{}", sb);
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
        if (pr.journalRead && !pr.houseFound) {
            WorldState ws = WorldState.get(sp.server);
            if (ws.housePos != null && sp.level().dimension() == Level.OVERWORLD
                    && sp.blockPosition().distSqr(ws.housePos) < 12 * 12) {
                pr.houseFound = true;
                sp.displayClientMessage(Component.translatable("zalrpakraft.house.found").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
                dirty = true;
            }
        }
        if (pr.tableOpened && ProgressService.update(sp)) dirty = true;
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
        for (ServerPlayer p : near) {
            PlayerProgress pr = ProgressService.of(p);
            pr.kills.add(id.toString());
            ProgressService.update(p);
            ProgressService.sync(p);
        }
    }

    private CommonEvents() {}
}
