package dev.zalrpakraft.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Последний прогресс, присланный сервером. */
public final class ClientState {
    public static CompoundTag progress = new CompoundTag();
    public static String questTitle = "";
    public static String questText = "";
    public static BlockPos housePos;
    public static long questChangedAt = -1000;

    private ClientState() {}
}
