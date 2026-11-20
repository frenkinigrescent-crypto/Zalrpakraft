package dev.zalrpakraft.net;

import dev.zalrpakraft.Zalrpakraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenMapPayload(String mapJson, CompoundTag progress) implements CustomPacketPayload {
    public static final Type<OpenMapPayload> TYPE = new Type<>(Zalrpakraft.id("open_map"));
    public static final StreamCodec<ByteBuf, OpenMapPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1 << 20), OpenMapPayload::mapJson,
            ByteBufCodecs.COMPOUND_TAG, OpenMapPayload::progress,
            OpenMapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
