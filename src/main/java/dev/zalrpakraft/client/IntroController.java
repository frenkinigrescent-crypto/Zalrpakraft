package dev.zalrpakraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Пробуждение: темнота → сердцебиение → веки моргают и открываются →
 * взгляд с неба опускается вперёд (будто встаёшь) → надпись → задание.
 * Время в тиках клиента (20 в секунду).
 */
public final class IntroController {
    public static final int LENGTH = 220;
    private static int tick = -1;

    public static void start() {
        tick = 0;
    }

    public static boolean active() {
        return tick >= 0 && tick < LENGTH;
    }

    public static float time(float partial) {
        return tick < 0 ? LENGTH : tick + partial;
    }

    public static void clientTick() {
        if (tick < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.isPaused()) return;
        switch (tick) {
            case 12, 34 -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.9F, 0.7F));
            case 56 -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_BREATH, 1.0F, 0.9F));
            case 120 -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.8F));
            case 165 -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F));
            default -> {}
        }
        tick++;
        if (tick >= LENGTH) tick = -1;
    }

    /** 0 = глаза закрыты, 1 = открыты полностью. */
    public static float eyeOpen(float t) {
        if (t < 40) return 0f;
        if (t < 58) return ease((t - 40) / 18f) * 0.28f;          // приоткрыл
        if (t < 70) return 0.28f * (1f - ease((t - 58) / 12f));   // моргнул
        if (t < 92) return ease((t - 70) / 22f) * 0.65f;          // снова
        if (t < 100) return 0.65f - ease((t - 92) / 8f) * 0.5f;   // моргнул
        if (t < 135) return 0.15f + ease((t - 100) / 35f) * 0.85f;
        return 1f;
    }

    /** Мягкое затемнение/размытие по краям. */
    public static float haze(float t) {
        if (t < 100) return 1f;
        if (t < 190) return 1f - ease((t - 100) / 90f);
        return 0f;
    }

    /** Наклон камеры: лежим, смотрим в небо, потом поднимаемся. */
    public static float pitch(float t) {
        if (t < 110) return -72f + Mth.sin(t * 0.05f) * 2f;
        if (t < 175) return Mth.lerp(ease((t - 110) / 65f), -72f, 6f);
        if (t < 195) return Mth.lerp(ease((t - 175) / 20f), 6f, 0f);
        return 0f;
    }

    public static float roll(float t) {
        if (t < 110) return 18f + Mth.sin(t * 0.07f) * 1.5f;
        if (t < 180) return Mth.lerp(ease((t - 110) / 70f), 18f, 0f);
        return 0f;
    }

    /** Насколько интро ещё управляет камерой (плавная передача управления игроку). */
    public static float cameraWeight(float t) {
        if (t < 185) return 1f;
        if (t < LENGTH) return 1f - ease((t - 185) / (LENGTH - 185f));
        return 0f;
    }

    public static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private IntroController() {}
}
