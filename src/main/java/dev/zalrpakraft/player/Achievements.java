package dev.zalrpakraft.player;

import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.data.ProgressionMap;
import dev.zalrpakraft.data.ProgressionMap.Kind;
import dev.zalrpakraft.data.ProgressionMap.Node;
import dev.zalrpakraft.net.PlaquePayload;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Выдача достижений мода. Все они с критерием minecraft:impossible и выдаются только отсюда. */
public final class Achievements {

    public static void award(ServerPlayer p, String id) {
        AdvancementHolder h = p.server.getAdvancements().get(Zalrpakraft.id(id));
        if (h == null) {
            Zalrpakraft.LOG.warn("Zalepacraft: нет достижения {}", id);
            return;
        }
        AdvancementProgress pr = p.getAdvancements().getOrStartProgress(h);
        if (pr.isDone()) return;
        p.getAdvancements().award(h, "done");
        h.value().display().ifPresent(d -> PacketDistributor.sendToPlayer(p, new PlaquePayload(
                id,
                BuiltInRegistries.ITEM.getKey(d.getIcon().getItem()).toString(),
                d.getType().getSerializedName())));
    }

    /** Достижения за метеоры и врата (вызывается после изучения узла старой карты). */
    public static void checkMap(ServerPlayer p, PlayerProgress pr) {
        if (pr.tableOpened) award(p, "table");
        int meteors = 0;
        for (Node n : ProgressionMap.nodes().values()) if (n.kind == Kind.METEOR && pr.studied.contains(n.id)) meteors++;
        if (meteors >= 1) award(p, "first_meteor");
        if (meteors >= 5) award(p, "meteors_5");
        if (pr.studied.contains("m_dragon")) award(p, "the_end");
    }

    /** Достижения звёздной карты: первая звезда, 25 звёзд, целиком изученная система. */
    public static void checkStars(ServerPlayer p, PlayerProgress pr) {
        int n = pr.unlockedItems.size();
        if (n >= 1) award(p, "first_planet");
        if (n >= 25) award(p, "planets_10");
        dev.zalrpakraft.tree.Tree t = dev.zalrpakraft.tree.TreeBuilder.tree();
        for (dev.zalrpakraft.tree.Tree.SystemDef s : t.systems.values()) {
            if (s.size() < 10) continue;
            boolean all = true;
            for (dev.zalrpakraft.tree.Tree.Planet pl : s.planets) {
                for (String id : pl.nodes) if (!pr.unlockedItems.contains(id)) { all = false; break; }
                if (!all) break;
            }
            if (all) { award(p, "stargazer"); break; }
        }
    }

    private Achievements() {}
}
