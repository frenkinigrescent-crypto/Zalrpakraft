package dev.zalrpakraft.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zalrpakraft.Zalrpakraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * config/zalrpakraft/bosses.json — какие мобы считаются боссами (метеорами),
 * что с них падает и какой узел карты закрывается убийством.
 * Поддерживаются шаблоны "мод:*" и "мод*:*" (тогда босс должен иметь тег #c:bosses или много здоровья).
 */
public final class BossTable {
    public record Entry(String entityPattern, String drop, int count, String meteor, boolean wildcard) {
        public boolean matches(ResourceLocation id) {
            String[] p = entityPattern.split(":", 2);
            if (p.length != 2) return false;
            return part(p[0], id.getNamespace()) && part(p[1], id.getPath());
        }

        private static boolean part(String pat, String val) {
            if (pat.equals("*")) return true;
            if (pat.endsWith("*")) return val.startsWith(pat.substring(0, pat.length() - 1));
            return pat.equals(val);
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void load() {
        ENTRIES.clear();
        try {
            JsonObject root = JsonParser.parseString(JsonConfigFile.readOrCreate("bosses.json")).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("bosses");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String ent = o.get("entity").getAsString();
                ENTRIES.add(new Entry(ent,
                        o.has("drop") ? o.get("drop").getAsString() : "",
                        o.has("count") ? o.get("count").getAsInt() : 1,
                        o.has("meteor") ? o.get("meteor").getAsString() : "",
                        ent.contains("*")));
            }
            Zalrpakraft.LOG.info("Zalepacraft: боссов в таблице: {}", ENTRIES.size());
        } catch (Exception e) {
            Zalrpakraft.LOG.error("Zalepacraft: ошибка в bosses.json", e);
        }
    }

    /** Точное совпадение важнее шаблона. */
    public static Entry find(ResourceLocation id) {
        Entry wild = null;
        for (Entry e : ENTRIES) {
            if (!e.matches(id)) continue;
            if (!e.wildcard()) return e;
            if (wild == null) wild = e;
        }
        return wild;
    }

    /** Пишет в лог, каких мобов и предметов из таблицы нет в сборке. Возвращает число найденных точных ID. */
    public static int validate() {
        int found = 0;
        for (Entry e : ENTRIES) {
            if (!e.wildcard()) {
                ResourceLocation id = ResourceLocation.tryParse(e.entityPattern());
                if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    found++;
                } else {
                    Zalrpakraft.LOG.warn("Zalepacraft: босс {} не найден в сборке (проверьте ID в config/zalrpakraft/bosses.json)", e.entityPattern());
                }
            } else {
                String ns = e.entityPattern().split(":", 2)[0];
                boolean any = BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                        .anyMatch(rl -> Entry.part(ns, rl.getNamespace()));
                if (!any) Zalrpakraft.LOG.warn("Zalepacraft: нет ни одного моба под шаблон {}", e.entityPattern());
            }
            if (!e.drop().isEmpty()) {
                ResourceLocation d = ResourceLocation.tryParse(e.drop());
                if (d == null || !BuiltInRegistries.ITEM.containsKey(d)) {
                    Zalrpakraft.LOG.warn("Zalepacraft: предмет {} из bosses.json не найден", e.drop());
                }
            }
        }
        return found;
    }

    public static Item dropItem(Entry e) {
        ResourceLocation d = ResourceLocation.tryParse(e.drop());
        if (d == null || !BuiltInRegistries.ITEM.containsKey(d)) return null;
        return BuiltInRegistries.ITEM.get(d);
    }

    public static ResourceLocation idOf(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    private BossTable() {}
}
