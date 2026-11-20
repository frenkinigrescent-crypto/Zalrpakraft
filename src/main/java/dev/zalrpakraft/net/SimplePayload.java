package dev.zalrpakraft.net;

import dev.zalrpakraft.Zalrpakraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Простой сигнал: "intro" (сервер→клиент: проиграть пробуждение), "journal_read" (клиент→сервер). */
public record SimplePayload(String action) implements CustomPacketPayload {
    public static final Type<SimplePayload> TYPE = new Type<>(Zalrpakraft.id("signal"));
    public static final StreamCodec<ByteBuf, SimplePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SimplePayload::action, SimplePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
