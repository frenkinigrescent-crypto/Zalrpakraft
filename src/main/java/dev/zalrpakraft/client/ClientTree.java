package dev.zalrpakraft.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Клиентская копия: какие предметы закрыты, где они на карте, что открыто, очки и EXP. */
public final class ClientTree {
    public static final Map<String, String> GATED = new HashMap<>();
    /** Для поиска по карте: предмет → «система|планета|подпись» (планета -1 — солнце «своего пути»). */
    public static final Map<String, String> LOC = new HashMap<>();
    public static int locVersion;
    public static final Set<String> UNLOCKED = new HashSet<>();
    public static int points, exp, expPerPoint = 100;

    public static void apply(CompoundTag t) {
        if (t.contains("gated")) {
            GATED.clear();
            CompoundTag g = t.getCompound("gated");
            for (String k : g.getAllKeys()) GATED.put(k, g.getString(k));
        }
        if (t.contains("loc")) {
            LOC.clear();
            CompoundTag g = t.getCompound("loc");
            for (String k : g.getAllKeys()) LOC.put(k, g.getString(k));
            locVersion++;
        }
        if (t.contains("points")) {
            points = t.getInt("points");
            exp = t.getInt("exp");
            expPerPoint = Math.max(1, t.getInt("expPerPoint"));
            UNLOCKED.clear();
            ListTag l = t.getList("unlocked", Tag.TAG_STRING);
            for (int i = 0; i < l.size(); i++) UNLOCKED.add(l.getString(i));
        }
    }

    public static boolean allows(ItemStack s) {
        if (s.isEmpty()) return true;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        return !GATED.containsKey(id) || UNLOCKED.contains(id);
    }

    public static String where(ItemStack s) {
        return GATED.get(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
    }

    private ClientTree() {}
}
