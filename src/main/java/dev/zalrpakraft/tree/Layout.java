package dev.zalrpakraft.tree;

import java.util.*;

/** Раскладка: узлы планеты — кольцами по глубине; системы галактики — спиралью. */
final class Layout {
    static final float RING0 = 78f, RING_STEP = 50f, SPACING = 40f;

    /**
     * Делает ветку интереснее: узлы без родителей группируются по материалу (brass_*, copper_* …) в цепочки,
     * а входов на планете остаётся не больше maxRoots — остальные цепочки подвешиваются к ним.
     */
    static void branches(Tree.Planet pl, Map<String, Tree.Node> all, Map<net.minecraft.world.item.Item, Integer> globalDepth, int maxRoots) {
        List<String> roots = new ArrayList<>();
        for (String id : pl.nodes) if (all.get(id).parents.isEmpty()) roots.add(id);
        if (roots.size() <= 1) return;
        java.util.function.ToIntFunction<String> gd = id -> globalDepth.getOrDefault(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)), 0);
        // 1. цепочки по материалу
        Map<String, List<String>> byStem = new LinkedHashMap<>();
        for (String id : roots) byStem.computeIfAbsent(stem(id), k -> new ArrayList<>()).add(id);
        for (List<String> g : byStem.values()) {
            if (g.size() < 2) continue;
            g.sort(Comparator.<String>comparingInt(gd).thenComparing(Comparator.naturalOrder()));
            for (int i = 1; i < g.size(); i++) all.get(g.get(i)).parents.add(g.get(i - 1));
        }
        // 2. не больше maxRoots входов: самые «ветвистые» становятся входами
        List<String> left = new ArrayList<>();
        for (String id : pl.nodes) if (all.get(id).parents.isEmpty()) left.add(id);
        if (left.size() <= maxRoots) return;
        Map<String, Integer> desc = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (String id : pl.nodes) for (String p : all.get(id).parents) children.computeIfAbsent(p, k -> new ArrayList<>()).add(id);
        for (String r : left) desc.put(r, countDesc(r, children, new HashSet<>()));
        left.sort(Comparator.<String>comparingInt(r -> -desc.get(r)).thenComparingInt(gd).thenComparing(Comparator.naturalOrder()));
        List<String> hubs = new ArrayList<>(left.subList(0, maxRoots));
        int[] load = new int[hubs.size()];
        for (String r : left.subList(maxRoots, left.size())) {
            int best = 0;
            for (int i = 1; i < hubs.size(); i++) if (load[i] < load[best]) best = i;
            load[best] += 1 + desc.get(r);
            all.get(r).parents.add(hubs.get(best));
        }
    }

    private static int countDesc(String id, Map<String, List<String>> children, Set<String> seen) {
        int n = 0;
        for (String c : children.getOrDefault(id, List.of())) if (seen.add(c)) n += 1 + countDesc(c, children, seen);
        return n;
    }

    /** Материал/семейство: brass_casing → brass, copper_valve_handle → copper, cogwheel → cogwheel. */
    static String stem(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        for (String pre : new String[]{"large_", "small_", "encased_", "mechanical_", "portable_", "smart_", "andesite_", "brass_", "copper_", "iron_", "golden_", "gold_", "zinc_", "netherite_", "diamond_", "stone_", "wooden_", "train_", "steam_", "fluid_", "item_", "redstone_"}) {
            if (path.startsWith(pre)) return pre;
        }
        int u = path.indexOf('_');
        return u > 0 ? path.substring(0, u) : path;
    }

    static void planet(Tree.Planet pl, Map<String, Tree.Node> all) {
        // глубина внутри планеты: BFS от узлов без родителей
        Map<String, Integer> ring = new HashMap<>();
        Deque<String> q = new ArrayDeque<>();
        Map<String, List<String>> children = new HashMap<>();
        for (String id : pl.nodes) {
            Tree.Node n = all.get(id);
            if (n.parents.isEmpty()) { ring.put(id, 0); q.add(id); }
            for (String p : n.parents) children.computeIfAbsent(p, k -> new ArrayList<>()).add(id);
        }
        if (q.isEmpty() && !pl.nodes.isEmpty()) { ring.put(pl.nodes.get(0), 0); q.add(pl.nodes.get(0)); }
        while (!q.isEmpty()) {
            String id = q.poll();
            int r = ring.get(id);
            for (String c : children.getOrDefault(id, List.of())) if (!ring.containsKey(c)) { ring.put(c, r + 1); q.add(c); }
        }
        for (String id : pl.nodes) ring.putIfAbsent(id, 0); // циклы — на первое кольцо
        // родители — только с более внутреннего кольца (иначе блок↔слиток заперли бы друг друга)
        for (String id : pl.nodes) {
            int r = ring.get(id);
            all.get(id).parents.removeIf(p -> ring.getOrDefault(p, 0) >= r);
            all.get(id).depth = r;
        }
        // узлы без родителей, но попавшие во внутренние циклы — родители только с меньшего кольца
        int maxRing = 0;
        for (int r : ring.values()) maxRing = Math.max(maxRing, r);
        List<List<String>> rings = new ArrayList<>();
        for (int i = 0; i <= maxRing; i++) rings.add(new ArrayList<>());
        for (String id : pl.nodes) rings.get(ring.get(id)).add(id);

        // раскладываем кольцо за кольцом; если не влезает — переносим на «подкольцо»
        Map<String, Float> angle = new HashMap<>();
        float radius = RING0;
        int visualRing = 0;
        for (List<String> r : rings) {
            if (r.isEmpty()) continue;
            // сортировка по среднему углу родителей, чтобы связи не путались
            r.sort(Comparator.comparingDouble(id -> {
                Tree.Node n = all.get(id);
                double sx = 0, sy = 0; int k = 0;
                for (String p : n.parents) { Float a = angle.get(p); if (a != null) { sx += Math.cos(a); sy += Math.sin(a); k++; } }
                return k == 0 ? (id.hashCode() & 0xFFFF) / 65535.0 * Math.PI * 2 : Math.atan2(sy, sx);
            }));
            int idx = 0;
            while (idx < r.size()) {
                int cap = Math.max(6, (int) (2 * Math.PI * radius / SPACING));
                int n = Math.min(cap, r.size() - idx);
                float offset = (visualRing % 2) * (float) (Math.PI / Math.max(1, n));
                for (int i = 0; i < n; i++) {
                    String id = r.get(idx + i);
                    float a = (float) (-Math.PI / 2 + offset + 2 * Math.PI * i / n);
                    Tree.Node node = all.get(id);
                    node.ring = visualRing;
                    node.x = (float) Math.cos(a) * radius;
                    node.y = (float) Math.sin(a) * radius;
                    angle.put(id, a);
                }
                idx += n;
                radius += RING_STEP;
                visualRing++;
            }
        }
    }

    static void galaxy(Tree tree) {
        List<Tree.SystemDef> list = new ArrayList<>(tree.systems.values());
        list.sort((a, b) -> b.size() - a.size());
        double golden = Math.toRadians(137.5);
        for (int i = 0; i < list.size(); i++) {
            Tree.SystemDef s = list.get(i);
            double r = i == 0 ? 0 : 230 * Math.sqrt(i);
            s.x = (float) (Math.cos(i * golden) * r);
            s.y = (float) (Math.sin(i * golden) * r);
        }
    }

    private Layout() {}
}
