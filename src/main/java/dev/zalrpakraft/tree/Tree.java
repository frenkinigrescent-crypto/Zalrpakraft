package dev.zalrpakraft.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Построенное дерево: системы (моды) → планеты (направления, 0 — солнце) → узлы (предметы). */
public final class Tree {
    public static final class Node {
        public String item;
        public String system;
        public int planet;          // индекс в system.planets
        public int ring;
        public float x, y;          // координаты на виде планеты
        public String category;     // light / medium / complex
        public int cost, exp;
        public List<String> parents = new ArrayList<>();
    }

    public static final class Planet {
        public String id, name;
        public int color;
        public List<String> nodes = new ArrayList<>();
    }

    public static final class SystemDef {
        public String id, name;
        public int color;
        public float x, y;          // на виде галактики
        public List<String> mods = new ArrayList<>();
        public List<Planet> planets = new ArrayList<>();
        public int size() {
            int n = 0;
            for (Planet p : planets) n += p.nodes.size();
            return n;
        }
    }

    public final Map<String, SystemDef> systems = new LinkedHashMap<>();
    public final Map<String, Node> nodes = new LinkedHashMap<>();
    public int expPerPoint = 100;
    public int startingPoints = 3;
    public int pointsPerMeteor = 3;

    public static final Tree EMPTY = new Tree();
}
