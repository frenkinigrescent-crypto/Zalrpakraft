package dev.zalrpakraft;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Серверные настройки (лежат в мире: serverconfig/zalrpakraft-server.toml, по умолчанию — defaultconfigs/).
 * Сервер сам рассылает их игрокам, поэтому скорость копания на клиенте и сервере всегда совпадает.
 */
public final class ZServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue CAVES_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CAVES_DIMENSIONS;
    public static final ModConfigSpec.IntValue CAVES_START_Y;
    public static final ModConfigSpec.DoubleValue CAVES_MAX_SLOWDOWN;
    public static final ModConfigSpec.DoubleValue CAVES_HUNGER;
    public static final ModConfigSpec.DoubleValue CAVES_TOOL_WEAR;
    public static final ModConfigSpec.DoubleValue CAVES_MOB_HEALTH;
    public static final ModConfigSpec.DoubleValue CAVES_MOB_DAMAGE;
    public static final ModConfigSpec.DoubleValue CAVES_MOB_ARMOR;
    public static final ModConfigSpec.DoubleValue CAVES_MOB_SPEED;
    public static final ModConfigSpec.DoubleValue CAVES_MOB_XP;
    public static final ModConfigSpec.BooleanValue CAVES_MESSAGES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Глубина: чем ниже, тем тяжелее копать и тем злее монстры.",
                "Сила растёт плавно от startY (0%) до дна мира (100%).").push("caves");
        CAVES_ENABLED = b.comment("Включить усложнение пещер").define("enabled", true);
        CAVES_DIMENSIONS = b.comment("Измерения, где это работает")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld", o -> o instanceof String);
        CAVES_START_Y = b.comment("С какой высоты начинается усложнение").defineInRange("startY", 48, -2032, 2032);
        CAVES_MAX_SLOWDOWN = b.comment("Замедление копания на дне (0.5 = вдвое дольше, 0.66 = втрое)")
                .defineInRange("maxMiningSlowdown", 0.5, 0.0, 0.95);
        CAVES_HUNGER = b.comment("Доп. истощение за каждый сломанный блок на дне (обычное — 0.005)")
                .defineInRange("hungerPerBlock", 0.02, 0.0, 1.0);
        CAVES_TOOL_WEAR = b.comment("Шанс лишней единицы износа инструмента за блок на дне (0.35 = 35%)")
                .defineInRange("toolWearChance", 0.35, 0.0, 1.0);
        CAVES_MOB_HEALTH = b.comment("Монстры на дне: прибавка здоровья (1.0 = +100%)").defineInRange("mobHealthBonus", 1.0, 0.0, 20.0);
        CAVES_MOB_DAMAGE = b.comment("Монстры на дне: прибавка урона (0.6 = +60%)").defineInRange("mobDamageBonus", 0.6, 0.0, 20.0);
        CAVES_MOB_ARMOR = b.comment("Монстры на дне: доп. броня").defineInRange("mobArmorBonus", 4.0, 0.0, 30.0);
        CAVES_MOB_SPEED = b.comment("Монстры на дне: прибавка скорости (0.1 = +10%)").defineInRange("mobSpeedBonus", 0.1, 0.0, 2.0);
        CAVES_MOB_XP = b.comment("Монстры на дне: прибавка опыта (1.0 = вдвое больше)").defineInRange("mobXpBonus", 1.0, 0.0, 20.0);
        CAVES_MESSAGES = b.comment("Короткие записи путника, когда спускаешься на новую глубину").define("messages", true);
        b.pop();
        SPEC = b.build();
    }

    private ZServerConfig() {}
}
