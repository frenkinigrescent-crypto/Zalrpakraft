package dev.zalrpakraft.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Общее звёздное небо для меню: градиент, туманности, мерцающие звёзды, редкие метеоры. */
public final class StarSky {
    private static final float[][] STARS = new float[700][];
    private static float[] meteor;
    private static final RandomSource RND = RandomSource.create();
    private static final long START = Util.getMillis();

    static {
        RandomSource r = RandomSource.create(1337);
        for (int i = 0; i < STARS.length; i++)
            STARS[i] = new float[]{r.nextFloat(), r.nextFloat(), 0.2f + r.nextFloat() * 0.8f, r.nextFloat() * 6.28f, 0.2f + r.nextFloat()};
    }

    public static float time() {
        return (Util.getMillis() - START) / 50f;
    }

    public static void render(GuiGraphics g, int width, int height, int mx, int my) {
        float t = time();
        g.fillGradient(0, 0, width, height, 0xFF05081A, 0xFF170A28);
        nebula(g, width * 0.25f + Mth.sin(t * 0.004f) * 30, height * 0.3f, width * 0.35f, 0x3A6BD1);
        nebula(g, width * 0.78f, height * 0.62f + Mth.cos(t * 0.003f) * 25, width * 0.4f, 0x9B4DCB);
        nebula(g, width * 0.55f, height * 0.12f, width * 0.22f, 0xD15FA0);
        float px = mx < 0 ? 0 : (mx - width / 2f) / width, py = my < 0 ? 0 : (my - height / 2f) / height;
        for (float[] s : STARS) {
            float x = ((s[0] * width + t * 0.05f * s[4] - px * 14 * s[4]) % width + width) % width;
            float y = s[1] * height - py * 10 * s[4];
            float tw = 0.55f + 0.45f * Mth.sin(t * 0.07f + s[3]);
            int a = Mth.clamp((int) (255 * s[2] * tw), 0, 255);
            int size = s[4] > 1.05f ? 2 : 1;
            g.fill((int) x, (int) y, (int) x + size, (int) y + size, (a << 24) | 0xFFFFFF);
        }
        if (meteor == null && RND.nextInt(240) == 0)
            meteor = new float[]{RND.nextFloat() * width * 0.8f + width * 0.2f, -10, -3.2f - RND.nextFloat() * 2, 1.6f + RND.nextFloat()};
        if (meteor != null) {
            meteor[0] += meteor[2];
            meteor[1] += meteor[3];
            for (int i = 0; i < 26; i++) {
                int a = (int) (220 * (1f - i / 26f));
                int x = (int) (meteor[0] - meteor[2] * i * 0.9f), y = (int) (meteor[1] - meteor[3] * i * 0.9f);
                g.fill(x, y, x + 2, y + 1, (a << 24) | (i < 3 ? 0xFFFFFF : 0xFFB070));
            }
            if (meteor[1] > height + 20 || meteor[0] < -40) meteor = null;
        }
    }

    public static void disc(GuiGraphics g, float cx, float cy, float r, int col) {
        int ri = Math.max(1, (int) r);
        for (int dy = -ri; dy <= ri; dy++) {
            int w = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill((int) cx - w, (int) cy + dy, (int) cx + w + 1, (int) cy + dy + 1, col);
        }
    }

    private static void nebula(GuiGraphics g, float cx, float cy, float r, int rgb) {
        for (int i = 7; i >= 1; i--) disc(g, cx, cy, r * i / 7f, (5 << 24) | rgb);
    }

    private StarSky() {}
}
