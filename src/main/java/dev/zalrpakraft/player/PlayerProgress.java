package dev.zalrpakraft.player;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Прогресс игрока: пролог, открытые измерения, изученные узлы карты. */
public class PlayerProgress implements INBTSerializable<CompoundTag> {
    public boolean introSeen;
    public boolean journalRead;
    public boolean houseFound;
    public boolean tableOpened;
    public boolean mapKeyUsed;
    public final Set<String> unlockedDims = new LinkedHashSet<>();
    public final Set<String> visitedDims = new LinkedHashSet<>();
    public final Set<String> studied = new LinkedHashSet<>();
    public final Set<String> kills = new LinkedHashSet<>();
    public final Set<String> eyesSeen = new LinkedHashSet<>();
    public final Set<String> questsDone = new LinkedHashSet<>();
    public final Set<String> questsClaimed = new LinkedHashSet<>();
    public final Map<String, Integer> killCounts = new LinkedHashMap<>();
    public String tracked = "";

    public CompoundTag write() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("introSeen", introSeen);
        t.putBoolean("journalRead", journalRead);
        t.putBoolean("houseFound", houseFound);
        t.putBoolean("tableOpened", tableOpened);
        t.putBoolean("mapKeyUsed", mapKeyUsed);
        t.put("unlockedDims", list(unlockedDims));
        t.put("visitedDims", list(visitedDims));
        t.put("studied", list(studied));
        t.put("kills", list(kills));
        t.put("eyesSeen", list(eyesSeen));
        t.put("questsDone", list(questsDone));
        t.put("questsClaimed", list(questsClaimed));
        CompoundTag kc = new CompoundTag();
        killCounts.forEach(kc::putInt);
        t.put("killCounts", kc);
        t.putString("tracked", tracked);
        return t;
    }

    public void read(CompoundTag t) {
        introSeen = t.getBoolean("introSeen");
        journalRead = t.getBoolean("journalRead");
        houseFound = t.getBoolean("houseFound");
        tableOpened = t.getBoolean("tableOpened");
        mapKeyUsed = t.getBoolean("mapKeyUsed");
        fill(unlockedDims, t, "unlockedDims");
        fill(visitedDims, t, "visitedDims");
        fill(studied, t, "studied");
        fill(kills, t, "kills");
        fill(eyesSeen, t, "eyesSeen");
        fill(questsDone, t, "questsDone");
        fill(questsClaimed, t, "questsClaimed");
        killCounts.clear();
        CompoundTag kc = t.getCompound("killCounts");
        for (String k : kc.getAllKeys()) killCounts.put(k, kc.getInt(k));
        tracked = t.getString("tracked");
    }

    private static ListTag list(Set<String> s) {
        ListTag l = new ListTag();
        for (String v : s) l.add(StringTag.valueOf(v));
        return l;
    }

    private static void fill(Set<String> s, CompoundTag t, String key) {
        s.clear();
        ListTag l = t.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) s.add(l.getString(i));
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return write();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        read(nbt);
    }
}
