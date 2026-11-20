package dev.zalrpakraft.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Рисует веки, затемнение и надпись во время пробуждения. */
public final class IntroOverlay {
    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!IntroController.active()) return;
        Minecraft mc = Minecraft.getInstance();
        float t = IntroController.time(delta.getGameTimeDeltaPartialTick(true));
        int w = g.guiWidth(), h = g.guiHeight();

        float open = IntroController.eyeOpen(t);
        // Веки: сверху и снизу, с мягким краем
        int lid = (int) ((h / 2f + 8) * (1f - open));
        g.fill(0, 0, w, lid, 0xFF000000);
        g.fill(0, h - lid, w, h, 0xFF000000);
        int soft = 28;
        for (int i = 0; i < soft; i++) {
            int a = (int) (255 * (1f - i / (float) soft) * (0.4f + 0.6f * (1f - open)));
            int col = (Mth.clamp(a, 0, 255) << 24);
            g.fill(0, lid + i, w, lid + i + 1, col);
            g.fill(0, h - lid - i - 1, w, h - lid - i, col);
        }
        // Дымка по краям (виньетка)
        float haze = IntroController.haze(t);
        if (haze > 0.01f) {
            int steps = 24;
            for (int i = 0; i < steps; i++) {
                float k = 1f - i / (float) steps;
                int a = (int) (200 * haze * k * k);
                int col = (Mth.clamp(a, 0, 255) << 24) | 0x0A0612;
                int inset = i * Math.max(w, h) / (steps * 5);
                g.fill(inset, inset, w - inset, inset + 2, col);
                g.fill(inset, h - inset - 2, w - inset, h - inset, col);
                g.fill(inset, inset, inset + 2, h - inset, col);
                g.fill(w - inset - 2, inset, w - inset, h - inset, col);
            }
            int a = (int) (90 * haze);
            g.fill(0, 0, w, h, (Mth.clamp(a, 0, 255) << 24) | 0x140A1E);
        }
        // Надписи
        text(g, mc, Component.translatable("zalrpakraft.intro.line1"), t, 60, 100, h / 2 + 30, w);
        text(g, mc, Component.translatable("zalrpakraft.intro.line2"), t, 140, 205, h / 2 + 40, w);
    }

    private static void text(GuiGraphics g, Minecraft mc, Component c, float t, float from, float to, int y, int w) {
        if (t < from || t > to) return;
        float k = (t - from) / (to - from);
        float a = k < 0.2f ? k / 0.2f : k > 0.75f ? (1f - k) / 0.25f : 1f;
        int alpha = Mth.clamp((int) (a * 255), 5, 255);
        int tw = mc.font.width(c);
        g.drawString(mc.font, c, (w - tw) / 2, y, (alpha << 24) | 0xD8CFE8, false);
    }

    private IntroOverlay() {}
}
