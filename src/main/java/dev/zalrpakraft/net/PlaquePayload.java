package dev.zalrpakraft.net;

import dev.zalrpakraft.Zalrpakraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Показать плашку достижения: id, иконка (ID предмета), тип рамки (task/goal/challenge). */
public record PlaquePayload(String id, String icon, String frame) implements CustomPacketPayload {
    public static final Type<PlaquePayload> TYPE = new Type<>(Zalrpakraft.id("plaque"));
    public static final StreamCodec<ByteBuf, PlaquePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlaquePayload::id,
            ByteBufCodecs.STRING_UTF8, PlaquePayload::icon,
            ByteBufCodecs.STRING_UTF8, PlaquePayload::frame,
            PlaquePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
