package dev.zalrpakraft.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.player.ProgressService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Книга заданий: главы, задания, задачи, награды. Источник — config/zalrpakraft/quests.json. */
public final class QuestBook {
    public static final class Chapter {
        public String id, name, icon = "minecraft:book";
        public int color = 0xFFFFFF;
    }

    public static final class Task {
        public String type;
        public List<String> values = new ArrayList<>();
        public int count = 1;
        public String label = "";
    }

    public static final class Reward {
        public String id;
        public int count = 1;
    }

    public static final class Quest {
        public String id, chapter, name, icon = "minecraft:book", desc = "";
        public List<Task> tasks = new ArrayList<>();
        public List<Reward> rewards = new ArrayList<>();
        public int xp;
        public List<String> requires = new ArrayList<>();
    }

    public static final class Book {
        public List<Chapter> chapters = new ArrayList<>();
        public List<Quest> quests = new ArrayList<>();

        public Quest quest(String id) {
            for (Quest q : quests) if (q.id.equals(id)) return q;
            return null;
        }
    }

    private static final Gson GSON = new GsonBuilder().create();
    private static Book raw = new Book();
    private static Book valid = new Book();
    private static String validJson = "{}";

    public static void load() {
        try {
            raw = parse(JsonConfigFile.readOrCreate("quests.json"));
            valid = raw;
            validJson = GSON.toJson(raw);
            Zalrpakraft.LOG.info("Zalepacraft: заданий в книге: {}", raw.quests.size());
        } catch (Exception e) {
            Zalrpakraft.LOG.error("Zalepacraft: ошибка в quests.json", e);
        }
    }

    public static Book book() { return valid; }
    public static String json() { return validJson; }

    public static Book fromJson(String json) {
        try {
            return GSON.fromJson(json, Book.class);
        } catch (Exception e) {
            return new Book();
        }
    }

    public static Book parse(String json) {
        Book b = new Book();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (JsonElement ce : root.getAsJsonArray("chapters")) {
            JsonObject o = ce.getAsJsonObject();
            Chapter c = new Chapter();
            c.id = o.get("id").getAsString();
            c.name = o.has("name") ? o.get("name").getAsString() : c.id;
            if (o.has("icon")) c.icon = o.get("icon").getAsString();
            if (o.has("color")) c.color = Integer.parseInt(o.get("color").getAsString().replace("#", ""), 16);
            b.chapters.add(c);
        }
        for (JsonElement qe : root.getAsJsonArray("quests")) {
            JsonObject o = qe.getAsJsonObject();
            Quest q = new Quest();
            q.id = o.get("id").getAsString();
            q.chapter = o.get("chapter").getAsString();
            q.name = o.has("name") ? o.get("name").getAsString() : q.id;
            if (o.has("icon")) q.icon = o.get("icon").getAsString();
            if (o.has("desc")) q.desc = o.get("desc").getAsString();
            if (o.has("requires")) for (JsonElement r : o.getAsJsonArray("requires")) q.requires.add(r.getAsString());
            for (JsonElement te : o.getAsJsonArray("tasks")) {
                JsonObject t = te.getAsJsonObject();
                Task task = new Task();
                task.type = t.get("type").getAsString();
                JsonElement v = t.get("value");
                if (v.isJsonArray()) for (JsonElement x : v.getAsJsonArray()) task.values.add(x.getAsString());
                else task.values.add(v.getAsString());
                if (t.has("count")) task.count = Math.max(1, t.get("count").getAsInt());
                if (t.has("label")) task.label = t.get("label").getAsString();
                q.tasks.add(task);
            }
            if (o.has("rewards")) {
                JsonObject r = o.getAsJsonObject("rewards");
                if (r.has("xp")) q.xp = r.get("xp").getAsInt();
                if (r.has("items")) for (JsonElement ie : r.getAsJsonArray("items")) {
                    JsonObject io = ie.getAsJsonObject();
                    Reward rw = new Reward();
                    rw.id = io.get("id").getAsString();
                    if (io.has("count")) rw.count = io.get("count").getAsInt();
                    q.rewards.add(rw);
                }
            }
            b.quests.add(q);
        }
        return b;
    }

    /**
     * Убрать задания, которые нельзя выполнить в этой сборке (нет предметов/мобов/узлов карты).
     * Вызывается на сервере после загрузки реестров и тегов.
     */
    public static int validate() {
        load(); // перечитать файл — правки применяются при следующем запуске мира
        Book out = new Book();
        out.chapters.addAll(raw.chapters);
        List<String> dropped = new ArrayList<>();
        Map<String, Boolean> ok = new LinkedHashMap<>();
        for (Quest q : raw.quests) {
            boolean good = true;
            for (Task t : q.tasks) {
                List<String> keep = new ArrayList<>();
                for (String v : t.values) if (valueExists(t.type, v)) keep.add(v);
                if (keep.isEmpty()) { good = false; break; }
                t.values = keep;
            }
            q.rewards.removeIf(r -> item(r.id) == null);
            ok.put(q.id, good);
            if (good) out.quests.add(q);
            else dropped.add(q.id);
        }
        // требования к скрытым заданиям считаем выполненными
        for (Quest q : out.quests) q.requires.removeIf(r -> !Boolean.TRUE.equals(ok.get(r)));
        valid = out;
        validJson = GSON.toJson(out);
        if (!dropped.isEmpty())
            Zalrpakraft.LOG.warn("Zalepacraft: скрыто заданий (нет предметов/мобов в сборке): {} — {}", dropped.size(), dropped);
        Zalrpakraft.LOG.info("Zalepacraft: доступно заданий: {}", out.quests.size());
        return out.quests.size();
    }

    private static boolean valueExists(String type, String v) {
        switch (type) {
            case "item": {
                if (v.startsWith("#")) {
                    ResourceLocation rl = ResourceLocation.tryParse(v.substring(1));
                    if (rl == null) return false;
                    return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl)).map(s -> s.size() > 0).orElse(false);
                }
                return item(v) != null;
            }
            case "kill":
                for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet())
                    if (ProgressService.matches(v, id.toString())) return true;
                return false;
            case "node":
                return ProgressionMap.nodes().containsKey(v);
            default:
                return true;
        }
    }

    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        return BuiltInRegistries.ITEM.get(rl);
    }

    private QuestBook() {}
}
