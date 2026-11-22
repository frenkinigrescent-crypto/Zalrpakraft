package dev.zalrpakraft.net;

import dev.zalrpakraft.Zalrpakraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuestBookPayload(String bookJson, CompoundTag state) implements CustomPacketPayload {
    public static final Type<QuestBookPayload> TYPE = new Type<>(Zalrpakraft.id("quest_book"));
    public static final StreamCodec<ByteBuf, QuestBookPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1 << 21), QuestBookPayload::bookJson,
            ByteBufCodecs.COMPOUND_TAG, QuestBookPayload::state,
            QuestBookPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
