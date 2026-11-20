package dev.zalrpakraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Данные мира: где стоит домик незнакомца и какие глаза вставлены в какие рамки. */
public class WorldState extends SavedData {
    public BlockPos housePos;      // центр пола
    public BlockPos tablePos;
    public boolean houseBuilt;
    public final Map<BlockPos, Integer> frameEyes = new HashMap<>();

    public static WorldState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldState::new, WorldState::load, null), "zalrpakraft");
    }

    public static WorldState load(CompoundTag t, HolderLookup.Provider p) {
        WorldState s = new WorldState();
        s.houseBuilt = t.getBoolean("houseBuilt");
        NbtUtils.readBlockPos(t, "house").ifPresent(b -> s.housePos = b);
        NbtUtils.readBlockPos(t, "table").ifPresent(b -> s.tablePos = b);
        ListTag eyes = t.getList("eyes", Tag.TAG_COMPOUND);
        for (int i = 0; i < eyes.size(); i++) {
            CompoundTag e = eyes.getCompound(i);
            NbtUtils.readBlockPos(e, "pos").ifPresent(b -> s.frameEyes.put(b, e.getInt("eye")));
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
        t.putBoolean("houseBuilt", houseBuilt);
        if (housePos != null) t.put("house", NbtUtils.writeBlockPos(housePos));
        if (tablePos != null) t.put("table", NbtUtils.writeBlockPos(tablePos));
        ListTag eyes = new ListTag();
        frameEyes.forEach((pos, eye) -> {
            CompoundTag e = new CompoundTag();
            e.put("pos", NbtUtils.writeBlockPos(pos));
            e.putInt("eye", eye);
            eyes.add(e);
        });
        t.put("eyes", eyes);
        return t;
    }
}
