package dev.zalrpakraft.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ZClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CUSTOM_MENU;
    public static final ModConfigSpec.BooleanValue WINDOW_ICON;
    public static final ModConfigSpec.BooleanValue STAR_BACKGROUNDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        CUSTOM_MENU = b.comment("Звёздное главное меню вместо стандартного").define("customMainMenu", true);
        STAR_BACKGROUNDS = b.comment("Звёздный фон вместо панорамы на экранах меню (выбор мира, сетевая игра, настройки до входа в мир)")
                .define("starBackgrounds", true);
        WINDOW_ICON = b.comment("Своя иконка окна игры").define("windowIcon", true);
        SPEC = b.build();
    }

    private ZClientConfig() {}
}
