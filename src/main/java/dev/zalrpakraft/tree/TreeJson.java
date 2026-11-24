package dev.zalrpakraft.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.zalrpakraft.player.PlayerProgress;

/** Сжатое представление дерева для отправки клиенту. */
public final class TreeJson {
    public static String galaxy(Tree t, PlayerProgress pr) {
        JsonArray arr = new JsonArray();
        for (Tree.SystemDef s : t.systems.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id);
            o.addProperty("name", s.name);
            o.addProperty("color", s.color);
            o.addProperty("x", s.x);
            o.addProperty("y", s.y);
            int total = 0, done = 0;
            for (Tree.Planet pl : s.planets) for (String n : pl.nodes) { total++; if (pr.unlockedItems.contains(n)) done++; }
            o.addProperty("total", total);
            o.addProperty("icon", s.own ? s.book : s.icon);
            if (s.own) {
                o.addProperty("own", true);
                o.addProperty("book", s.book);
            }
            o.addProperty("done", done);
            JsonArray pls = new JsonArray();
            for (Tree.Planet pl : s.planets) pls.add(pl.color);
            o.add("planets", pls);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("systems", arr);
        return root.toString();
    }

    public static String system(Tree t, Tree.SystemDef s) {
        JsonObject o = new JsonObject();
        o.addProperty("id", s.id);
        o.addProperty("name", s.name);
        o.addProperty("color", s.color);
        o.addProperty("icon", s.own ? s.book : s.icon);
        JsonArray pls = new JsonArray();
        for (Tree.Planet pl : s.planets) {
            JsonObject po = new JsonObject();
            po.addProperty("id", pl.id);
            po.addProperty("name", pl.name);
            po.addProperty("color", pl.color);
            po.addProperty("req", pl.requiresSun);
            JsonArray ns = new JsonArray();
            for (String id : pl.nodes) {
                Tree.Node n = t.nodes.get(id);
                JsonObject no = new JsonObject();
                no.addProperty("i", n.item);
                no.addProperty("x", Math.round(n.x));
                no.addProperty("y", Math.round(n.y));
                no.addProperty("c", n.cost);
                no.addProperty("e", n.exp);
                no.addProperty("k", n.category.charAt(0) + "");
                if (!n.parents.isEmpty()) no.add("p", arr(n.parents));
                if (!n.soft.isEmpty()) no.add("s", arr(n.soft));
                if (!n.external.isEmpty()) no.add("ex", arr(n.external));
                ns.add(no);
            }
            po.add("nodes", ns);
            pls.add(po);
        }
        o.add("planets", pls);
        if (s.own) {
            JsonObject ow = new JsonObject();
            ow.addProperty("book", s.book);
            ow.addProperty("note", s.note);
            JsonArray st = new JsonArray();
            for (String[] step : s.steps) {
                JsonObject so = new JsonObject();
                so.addProperty("item", step[0]);
                so.addProperty("text", step[1]);
                st.add(so);
            }
            ow.add("steps", st);
            ow.add("mods", arr(s.mods));
            o.add("own", ow);
        }
        return o.toString();
    }

    private static JsonArray arr(java.util.List<String> l) {
        JsonArray a = new JsonArray();
        for (String x : l) a.add(x);
        return a;
    }

    private TreeJson() {}
}
