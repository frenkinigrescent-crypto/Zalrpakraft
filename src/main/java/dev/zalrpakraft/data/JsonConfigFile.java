package dev.zalrpakraft.data;

import dev.zalrpakraft.Zalrpakraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Копирует json по умолчанию из ресурсов мода в config/zalrpakraft/ и читает его оттуда. */
final class JsonConfigFile {
    static String readOrCreate(String name) {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(Zalrpakraft.MODID);
        Path file = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            byte[] bundled;
            try (InputStream in = JsonConfigFile.class.getResourceAsStream("/defaults/" + name)) {
                bundled = in == null ? null : in.readAllBytes();
            }
            if (bundled != null && Files.exists(file)
                    && version(Files.readString(file, StandardCharsets.UTF_8)) < version(new String(bundled, StandardCharsets.UTF_8))) {
                Files.copy(file, dir.resolve(name + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.write(file, bundled);
                Zalrpakraft.LOG.info("Zalepacraft: {} обновлён до новой версии (старый сохранён как {}.bak)", name, name);
            }
            if (!Files.exists(file)) {
                try (InputStream in = JsonConfigFile.class.getResourceAsStream("/defaults/" + name)) {
                    if (in == null) throw new IOException("нет ресурса /defaults/" + name);
                    Files.write(file, in.readAllBytes());
                }
                Zalrpakraft.LOG.info("Zalepacraft: создан {}", file);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Zalrpakraft.LOG.error("Zalepacraft: не удалось прочитать {}, беру встроенный", file, e);
            try (InputStream in = JsonConfigFile.class.getResourceAsStream("/defaults/" + name)) {
                return in == null ? "{}" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e2) {
                return "{}";
            }
        }
    }

    /** Поле "_version" в корне json; нет — 0. */
    static int version(String json) {
        try {
            var o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            return o.has("_version") ? o.get("_version").getAsInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private JsonConfigFile() {}
}
