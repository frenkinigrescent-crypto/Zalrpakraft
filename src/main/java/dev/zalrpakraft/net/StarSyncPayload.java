package dev.zalrpakraft.net;

import dev.zalrpakraft.Zalrpakraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Очки/EXP/открытые предметы ("points","exp","unlocked") или список закрытых ("gated"). */
public record StarSyncPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<StarSyncPayload> TYPE = new Type<>(Zalrpakraft.id("star_sync"));
    public static final StreamCodec<ByteBuf, StarSyncPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.TRUSTED_COMPOUND_TAG, StarSyncPayload::tag, StarSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
