package dev.zalrpakraft;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ZConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_MULT;
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE_MULT;
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_PER_PLAYER;
    public static final ModConfigSpec.IntValue BOSS_PLAYER_RADIUS;
    public static final ModConfigSpec.BooleanValue SCALE_TAGGED_BOSSES;

    public static final ModConfigSpec.BooleanValue LOCK_NETHER;
    public static final ModConfigSpec.BooleanValue LOCK_PARADISE;
    public static final ModConfigSpec.BooleanValue CUSTOM_END_EYES;

    public static final ModConfigSpec.BooleanValue INTRO_ENABLED;
    public static final ModConfigSpec.IntValue HOUSE_MIN_DIST;
    public static final ModConfigSpec.IntValue HOUSE_MAX_DIST;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Усиление боссов (под группу ~4 человек)").push("bosses");
        BOSS_HEALTH_MULT = b.comment("Множитель здоровья боссов").defineInRange("healthMultiplier", 3.0, 0.1, 100.0);
        BOSS_DAMAGE_MULT = b.comment("Множитель урона боссов").defineInRange("damageMultiplier", 1.5, 0.1, 100.0);
        BOSS_HEALTH_PER_PLAYER = b.comment("Доп. здоровье за каждого игрока рядом сверх первого (0.25 = +25%)")
                .defineInRange("healthPerExtraPlayer", 0.25, 0.0, 10.0);
        BOSS_PLAYER_RADIUS = b.comment("Радиус, в котором считаются игроки при появлении босса")
                .defineInRange("playerRadius", 96, 8, 512);
        SCALE_TAGGED_BOSSES = b.comment("Усиливать также всех мобов с тегом #c:bosses, даже если их нет в bosses.json")
                .define("scaleTaggedBosses", true);
        b.pop();

        b.comment("Измерения").push("dimensions");
        LOCK_NETHER = b.comment("Ад закрыт, пока игрок не использует Ключ Преисподней").define("lockNether", true);
        LOCK_PARADISE = b.comment("Рай (Paradise Lost) закрыт, пока игрок не использует Ключ Рая").define("lockParadise", true);
        CUSTOM_END_EYES = b.comment("В рамку портала Энда вставляются только 12 особых глаз, каждый по одному разу")
                .define("customEndEyes", true);
        b.pop();

        b.comment("Начало игры").push("intro");
        INTRO_ENABLED = b.comment("Анимация пробуждения и дневник при первом входе").define("enabled", true);
        HOUSE_MIN_DIST = b.comment("Мин. расстояние домика от точки спавна").defineInRange("houseMinDistance", 90, 16, 2000);
        HOUSE_MAX_DIST = b.comment("Макс. расстояние домика от точки спавна").defineInRange("houseMaxDistance", 160, 16, 4000);
        b.pop();

        SPEC = b.build();
    }

    private ZConfig() {}
}
