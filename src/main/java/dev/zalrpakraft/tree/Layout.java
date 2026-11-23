package dev.zalrpakraft.tree;

import java.util.*;

/** Раскладка: узлы планеты — кольцами по глубине; системы галактики — спиралью. */
final class Layout {
    static final float RING0 = 78f, RING_STEP = 50f, SPACING = 40f;

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
            double r = i == 0 ? 0 : 130 * Math.sqrt(i);
            s.x = (float) (Math.cos(i * golden) * r);
            s.y = (float) (Math.sin(i * golden) * r);
        }
    }

    private Layout() {}
}
