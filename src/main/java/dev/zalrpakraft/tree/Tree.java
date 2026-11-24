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
        public int ring;            // кольцо на экране
        public int depth;           // глубина в ветке (для цены)
        public float x, y;          // координаты на виде планеты
        public String category;     // light / medium / complex
        public int cost, exp;
        /** Настоящие связи крафта: все должны быть открыты. */
        public List<String> parents = new ArrayList<>();
        /** Связи пути (цепочки по материалу, подвес к входам): хватает любой одной. */
        public List<String> soft = new ArrayList<>();
        /** Нужное для крафта с других планет и из других систем — только для подсказки. */
        public List<String> external = new ArrayList<>();
    }

    public static final class Planet {
        public String id, name;
        public int color;
        public List<String> nodes = new ArrayList<>();
        public int requiresSun;     // сколько звёзд солнца открыть, чтобы планета стала доступна
    }

    public static final class SystemDef {
        public String id, name;
        public int color;
        public float x, y;          // на виде галактики
        public List<String> mods = new ArrayList<>();
        public List<Planet> planets = new ArrayList<>();
        /** Мод со своей прогрессией: ничего не закрыто, на солнце — книга мода и как её получить. */
        public boolean own;
        public String book = "", note = "";
        /** Значимый предмет системы — лежит внутри полупрозрачного солнца (если у мода нет квадратного логотипа). */
        public String icon = "";
        public List<String[]> steps = new ArrayList<>(); // {item, text}
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
    public int planetCompleteBonus = 3;

    public static final Tree EMPTY = new Tree();
}
