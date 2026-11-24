package dev.zalrpakraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.zalrpakraft.Zalrpakraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Логотипы модов из их mods.toml (logoFile). Берутся только почти квадратные — баннеры в круг не влезут. */
public final class ModIcons {
    public record Logo(ResourceLocation tex, int w, int h) {}

    private static final Map<String, Optional<Logo>> CACHE = new HashMap<>();

    public static Logo get(String modId) {
        if (modId == null || modId.isEmpty() || modId.startsWith("_")) return null;
        return CACHE.computeIfAbsent(modId, ModIcons::load).orElse(null);
    }

    private static Optional<Logo> load(String modId) {
        try {
            Optional<IModInfo> info = ModList.get().getModContainerById(modId).map(c -> c.getModInfo());
            if (info.isEmpty() || info.get().getLogoFile().isEmpty()) return Optional.empty();
            Path p = info.get().getOwningFile().getFile().findResource(info.get().getLogoFile().get());
            if (p == null || !Files.exists(p)) return Optional.empty();
            NativeImage img;
            try (InputStream in = Files.newInputStream(p)) {
                img = NativeImage.read(in);
            }
            float ratio = img.getWidth() / (float) img.getHeight();
            if (ratio < 0.8f || ratio > 1.25f) {
                img.close();
                return Optional.empty();
            }
            ResourceLocation rl = Zalrpakraft.id("modlogo/" + modId.replaceAll("[^a-z0-9_.-]", "_"));
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
            return Optional.of(new Logo(rl, img.getWidth(), img.getHeight()));
        } catch (Exception e) {
            Zalrpakraft.LOG.debug("Zalepacraft: логотип {} не загрузился: {}", modId, e.toString());
            return Optional.empty();
        }
    }

    private ModIcons() {}
}
