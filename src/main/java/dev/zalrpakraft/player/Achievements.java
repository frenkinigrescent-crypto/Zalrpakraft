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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/** Выдача достижений мода. Все они с критерием minecraft:impossible и выдаются только отсюда. */
public final class Achievements {

    public static void award(ServerPlayer p, String id) {
        AdvancementHolder h = p.server.getAdvancements().get(Zalrpakraft.id(id));
        if (h == null) {
            Zalrpakraft.LOG.warn("Zalrpakraft: нет достижения {}", id);
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

    /** Достижения, зависящие от карты: вызывается после каждого изучения узла. */
    public static void checkMap(ServerPlayer p, PlayerProgress pr) {
        if (pr.tableOpened) award(p, "table");
        int planets = 0, meteors = 0, total = 0, done = 0;
        for (Node n : ProgressionMap.nodes().values()) {
            boolean hidden = n.kind == Kind.PLANET && !n.mod.isEmpty() && !ModList.get().isLoaded(n.mod);
            if (hidden || n.kind == Kind.ROOT || n.kind == Kind.GALAXY) continue;
            boolean st = pr.studied.contains(n.id);
            total++;
            if (st) done++;
            if (st && n.kind == Kind.PLANET) planets++;
            if (st && n.kind == Kind.METEOR) meteors++;
        }
        if (planets >= 1) award(p, "first_planet");
        if (planets >= 10) award(p, "planets_10");
        if (meteors >= 1) award(p, "first_meteor");
        if (meteors >= 5) award(p, "meteors_5");
        if (total > 0 && done >= total) award(p, "stargazer");
        if (pr.studied.contains("m_dragon")) award(p, "the_end");

        for (Node g : ProgressionMap.nodes().values()) {
            if (g.kind != Kind.GALAXY) continue;
            boolean any = false, all = true;
            for (Node n : ProgressionMap.nodes().values()) {
                if (n.kind != Kind.PLANET || !g.id.equals(n.galaxy)) continue;
                if (!n.mod.isEmpty() && !ModList.get().isLoaded(n.mod)) continue;
                any = true;
                if (!pr.studied.contains(n.id)) { all = false; break; }
            }
            if (any && all) award(p, "galaxy_" + g.id.replaceFirst("^g_", ""));
        }
    }

    private Achievements() {}
}
