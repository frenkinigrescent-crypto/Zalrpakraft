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
            if (!Files.exists(file)) {
                try (InputStream in = JsonConfigFile.class.getResourceAsStream("/defaults/" + name)) {
                    if (in == null) throw new IOException("нет ресурса /defaults/" + name);
                    Files.write(file, in.readAllBytes());
                }
                Zalrpakraft.LOG.info("Zalrpakraft: создан {}", file);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Zalrpakraft.LOG.error("Zalrpakraft: не удалось прочитать {}, беру встроенный", file, e);
            try (InputStream in = JsonConfigFile.class.getResourceAsStream("/defaults/" + name)) {
                return in == null ? "{}" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e2) {
                return "{}";
            }
        }
    }

    private JsonConfigFile() {}
}
