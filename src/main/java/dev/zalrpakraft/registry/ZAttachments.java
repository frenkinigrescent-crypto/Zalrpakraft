package dev.zalrpakraft.registry;

import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.player.PlayerProgress;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ZAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Zalrpakraft.MODID);

    public static final Supplier<AttachmentType<PlayerProgress>> PROGRESS = ATTACHMENTS.register("progress",
            () -> AttachmentType.serializable(PlayerProgress::new).copyOnDeath().build());

    private ZAttachments() {}
}
