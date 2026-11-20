package dev.zalrpakraft.net;

import dev.zalrpakraft.Zalrpakraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SyncProgressPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<SyncProgressPayload> TYPE = new Type<>(Zalrpakraft.id("sync_progress"));
    public static final StreamCodec<ByteBuf, SyncProgressPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, SyncProgressPayload::tag, SyncProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
