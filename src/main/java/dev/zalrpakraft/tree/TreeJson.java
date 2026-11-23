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
        JsonArray pls = new JsonArray();
        for (Tree.Planet pl : s.planets) {
            JsonObject po = new JsonObject();
            po.addProperty("id", pl.id);
            po.addProperty("name", pl.name);
            po.addProperty("color", pl.color);
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
                if (!n.parents.isEmpty()) {
                    JsonArray par = new JsonArray();
                    for (String p : n.parents) par.add(p);
                    no.add("p", par);
                }
                ns.add(no);
            }
            po.add("nodes", ns);
            pls.add(po);
        }
        o.add("planets", pls);
        return o.toString();
    }

    private TreeJson() {}
}
