package dev.zalrpakraft.net;

import dev.zalrpakraft.Zalrpakraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Открыть/обновить звёздную карту: view = galaxy | system. */
public record TreeViewPayload(String view, String json, String mapJson, CompoundTag progress) implements CustomPacketPayload {
    public static final Type<TreeViewPayload> TYPE = new Type<>(Zalrpakraft.id("tree_view"));
    public static final StreamCodec<ByteBuf, TreeViewPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TreeViewPayload::view,
            ByteBufCodecs.stringUtf8(1 << 22), TreeViewPayload::json,
            ByteBufCodecs.stringUtf8(1 << 20), TreeViewPayload::mapJson,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, TreeViewPayload::progress,
            TreeViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
