package dev.zalrpakraft.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.player.PlayerProgress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Карта прогрессии: галактики (группы модов), планеты (моды), метеоры (боссы), врата (измерения).
 * Источник — config/zalrpakraft/progression.json. Тот же класс разбирает карту на клиенте.
 */
public final class ProgressionMap {
    public enum Kind { ROOT, GALAXY, PLANET, METEOR, GATE }

    public static final class Node {
        public String id;
        public Kind kind;
        public String name;
        public String desc = "";
        public String hint = "";
        public String galaxy = "";
        public String mod = "";
        public int color = 0xFFFFFF;
        public float x, y;
        public String unlockType = "auto";
        public String unlockValue = "";
        public List<String> requires = new ArrayList<>();
        public List<String> requiresAny = new ArrayList<>();
    }

    private static String rawJson = "{}";
    private static Map<String, Node> nodes = Collections.emptyMap();

    public static void load() {
        rawJson = JsonConfigFile.readOrCreate("progression.json");
        try {
            nodes = parse(rawJson);
            Zalrpakraft.LOG.info("Zalepacraft: узлов на звёздной карте: {}", nodes.size());
        } catch (Exception e) {
            Zalrpakraft.LOG.error("Zalepacraft: ошибка в progression.json", e);
            nodes = Collections.emptyMap();
        }
    }

    public static String rawJson() { return rawJson; }
    public static Map<String, Node> nodes() { return nodes; }

    public static Map<String, Node> parse(String json) {
        Map<String, Node> out = new LinkedHashMap<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        if (root.has("root")) {
            Node r = node(root.getAsJsonObject("root"), Kind.ROOT);
            out.put(r.id, r);
        }
        if (root.has("galaxies")) {
            for (JsonElement ge : root.getAsJsonArray("galaxies")) {
                JsonObject g = ge.getAsJsonObject();
                Node gal = node(g, Kind.GALAXY);
                out.put(gal.id, gal);
                JsonArray planets = g.has("planets") ? g.getAsJsonArray("planets") : new JsonArray();
                int n = planets.size();
                float radius = g.has("radius") ? g.get("radius").getAsFloat() : 62f;
                for (int i = 0; i < n; i++) {
                    JsonObject po = planets.get(i).getAsJsonObject();
                    Node p = node(po, Kind.PLANET);
                    p.galaxy = gal.id;
                    if (!po.has("color")) p.color = gal.color;
                    if (!po.has("x")) {
                        double a = -Math.PI / 2 + (Math.PI * 2 * i) / Math.max(1, n);
                        p.x = gal.x + (float) (Math.cos(a) * radius);
                        p.y = gal.y + (float) (Math.sin(a) * radius);
                    }
                    if (p.requires.isEmpty() && p.requiresAny.isEmpty()) p.requires.add(gal.id);
                    out.put(p.id, p);
                }
            }
        }
        if (root.has("meteors")) {
            for (JsonElement me : root.getAsJsonArray("meteors")) out.put(node(me.getAsJsonObject(), Kind.METEOR).id, node(me.getAsJsonObject(), Kind.METEOR));
        }
        if (root.has("gates")) {
            for (JsonElement ge : root.getAsJsonArray("gates")) out.put(node(ge.getAsJsonObject(), Kind.GATE).id, node(ge.getAsJsonObject(), Kind.GATE));
        }
        return out;
    }

    private static Node node(JsonObject o, Kind kind) {
        Node n = new Node();
        n.kind = kind;
        n.id = o.get("id").getAsString();
        n.name = o.has("name") ? o.get("name").getAsString() : n.id;
        if (o.has("desc")) n.desc = o.get("desc").getAsString();
        if (o.has("hint")) n.hint = o.get("hint").getAsString();
        if (o.has("mod")) n.mod = o.get("mod").getAsString();
        if (o.has("color")) n.color = Integer.parseInt(o.get("color").getAsString().replace("#", ""), 16);
        if (o.has("x")) n.x = o.get("x").getAsFloat();
        if (o.has("y")) n.y = o.get("y").getAsFloat();
        if (o.has("unlock")) {
            JsonObject u = o.getAsJsonObject("unlock");
            n.unlockType = u.get("type").getAsString();
            if (u.has("value")) n.unlockValue = u.get("value").getAsString();
        } else if (kind == Kind.PLANET && !n.mod.isEmpty()) {
            n.unlockType = "mod";
            n.unlockValue = n.mod;
        }
        if (o.has("requires")) for (JsonElement e : o.getAsJsonArray("requires")) n.requires.add(e.getAsString());
        if (o.has("requiresAny")) for (JsonElement e : o.getAsJsonArray("requiresAny")) n.requiresAny.add(e.getAsString());
        return n;
    }

    /** Все условия-предки выполнены. */
    public static boolean available(Node n, Predicate<String> studied) {
        for (String r : n.requires) if (!studied.test(r)) return false;
        if (!n.requiresAny.isEmpty()) {
            boolean any = false;
            for (String r : n.requiresAny) if (studied.test(r)) { any = true; break; }
            if (!any) return false;
        }
        return true;
    }

    public static boolean available(Node n, PlayerProgress p) {
        return available(n, p.studied::contains);
    }

    private ProgressionMap() {}
}
