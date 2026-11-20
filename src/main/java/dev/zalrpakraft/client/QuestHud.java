package dev.zalrpakraft.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/** Текущее задание в правом верхнем углу. */
public final class QuestHud {
    private static final int WIDTH = 168;

    public static Component decode(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        if (s.startsWith("#")) return Component.literal(s.substring(1));
        if (s.startsWith("@")) return Component.translatable("zalrpakraft.quest.house.text", s.substring(1));
        return Component.translatable(s);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (ClientState.questTitle.isEmpty()) return;
        if (IntroController.active() && IntroController.time(0) < 190) return;
        if (mc.getDebugOverlay().showDebugScreen()) return;

        float since = (mc.level.getGameTime() - ClientState.questChangedAt) + delta.getGameTimeDeltaPartialTick(true);
        float slide = IntroController.ease(Mth.clamp(since / 14f, 0f, 1f));
        float flash = Mth.clamp(1f - since / 40f, 0f, 1f);

        Component title = decode(ClientState.questTitle);
        Component text = decode(ClientState.questText);
        List<FormattedCharSequence> lines = mc.font.split(text, WIDTH - 16);
        int h = 22 + lines.size() * 10 + 4;
        int x = (int) (g.guiWidth() - 6 - WIDTH * slide);
        int y = 6;

        g.fill(x, y, x + WIDTH, y + h, 0xB0100A1C);
        int border = flash > 0 ? lerpColor(0xFF7A5BA8, 0xFFFFE08A, flash) : 0xFF7A5BA8;
        g.fill(x, y, x + WIDTH, y + 1, border);
        g.fill(x, y + h - 1, x + WIDTH, y + h, 0xFF3C2C5A);
        g.fill(x, y, x + 2, y + h, border);

        g.drawString(mc.font, Component.literal("✦ ").append(Component.translatable("zalrpakraft.quest.header")), x + 7, y + 4, 0xFFB79CFF, false);
        g.drawString(mc.font, title, x + 7, y + 13, 0xFFFFE6A6, false);
        int ly = y + 24;
        for (FormattedCharSequence l : lines) {
            g.drawString(mc.font, l, x + 9, ly, 0xFFD8D2E6, false);
            ly += 10;
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) Mth.lerp(t, ar, br) << 16) | ((int) Mth.lerp(t, ag, bg) << 8) | (int) Mth.lerp(t, ab, bb);
    }

    private QuestHud() {}
}
