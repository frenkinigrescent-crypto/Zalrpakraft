package dev.zalrpakraft.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Светящаяся плашка достижения по центру сверху (в духе Advancement Plaques):
 * всплывает, по ней проходит блик, держится ~5 секунд и уходит вверх.
 * Цвет рамки зависит от типа: достижение — фиолетовый, цель — бирюзовый, испытание — золотой.
 */
public final class PlaqueOverlay {
    private record Entry(String id, ItemStack icon, String frame) {}

    private static final Deque<Entry> QUEUE = new ArrayDeque<>();
    private static Entry current;
    private static long startMs;
    private static final long IN = 380, HOLD = 4800, OUT = 520;

    public static void clear() {
        QUEUE.clear();
        current = null;
    }

    public static void push(String id, String icon, String frame) {
        ResourceLocation rl = ResourceLocation.tryParse(icon);
        ItemStack stack = rl != null && BuiltInRegistries.ITEM.containsKey(rl)
                ? new ItemStack(BuiltInRegistries.ITEM.get(rl)) : ItemStack.EMPTY;
        QUEUE.add(new Entry(id, stack, frame));
    }

    private static void startNext(Minecraft mc) {
        current = QUEUE.poll();
        startMs = System.currentTimeMillis();
        if (current == null) return;
        switch (current.frame()) {
            case "challenge" -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F));
            case "goal" -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.35F, 0.55F));
            default -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.25F, 1.0F));
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (IntroController.active()) return;
        if (current == null) {
            if (QUEUE.isEmpty()) return;
            startNext(mc);
            if (current == null) return;
        }
        long t = System.currentTimeMillis() - startMs;
        if (t > IN + HOLD + OUT) {
            current = null;
            return;
        }
        float appear = t < IN ? IntroController.ease(t / (float) IN) : 1f;
        float leave = t > IN + HOLD ? IntroController.ease((t - IN - HOLD) / (float) OUT) : 0f;
        float alpha = appear * (1f - leave);
        float scale = 0.7f + 0.3f * appear;

        int accent = switch (current.frame()) {
            case "challenge" -> 0xFFC94A;
            case "goal" -> 0x5FE0FF;
            default -> 0xA98BFF;
        };
        Component header = Component.translatable("zalrpakraft.plaque." + current.frame());
        Component title = Component.translatable("zalrpakraft.adv." + current.id() + ".title");
        int w = Math.max(190, Math.max(mc.font.width(title), mc.font.width(header)) + 64);
        int h = 36;
        int cx = g.guiWidth() / 2;
        int baseTop = 34;
        boolean hudShown = System.currentTimeMillis() - QuestHud.lastFrame < 200;
        if (hudShown && cx + w / 2 + 12 > QuestHud.lastLeft) baseTop = Math.max(baseTop, QuestHud.lastBottom + 10);
        int top = baseTop - (int) (leave * 26);

        g.pose().pushPose();
        g.pose().translate(cx, top + h / 2f, 400);
        g.pose().scale(scale, scale, 1f);
        int x0 = -w / 2, y0 = -h / 2, x1 = w / 2, y1 = h / 2;

        // внешнее свечение
        boolean challenge = "challenge".equals(current.frame());
        float pulse = challenge ? 0.6f + 0.4f * Mth.sin(t / 180f) : 0.6f;
        for (int i = 6; i >= 1; i--) {
            int a = (int) (alpha * pulse * 26 / i);
            g.fill(x0 - i * 2, y0 - i, x1 + i * 2, y1 + i, (Mth.clamp(a, 0, 255) << 24) | accent);
        }
        // тело
        g.fillGradient(x0, y0, x1, y1, a(alpha, 0xE6, 0x1A1030), a(alpha, 0xE6, 0x0C0818));
        // рамка
        int border = a(alpha, 0xFF, accent);
        g.fill(x0, y0, x1, y0 + 1, border);
        g.fill(x0, y1 - 1, x1, y1, border);
        g.fill(x0, y0, x0 + 1, y1, border);
        g.fill(x1 - 1, y0, x1, y1, border);
        g.fill(x0 + 3, y0 + 3, x1 - 3, y0 + 4, a(alpha, 0x40, accent));
        // ромбы по краям
        diamond(g, x0, 0, 4, border);
        diamond(g, x1, 0, 4, border);
        diamond(g, 0, y0, 3, border);
        // блик
        if (t < 1200) {
            float k = t / 1200f;
            int bx = (int) Mth.lerp(k, x0 - 30, x1 + 30);
            for (int i = -10; i <= 10; i++) {
                int ba = (int) (alpha * 90 * (1f - Math.abs(i) / 11f));
                int xx = bx + i;
                if (xx > x0 && xx < x1) g.fill(xx, y0 + 1, xx + 1, y1 - 1, (Mth.clamp(ba, 0, 255) << 24) | 0xFFFFFF);
            }
        }
        // иконка
        int ix = x0 + 10, iy = -8;
        g.fill(ix - 3, iy - 3, ix + 19, iy + 19, a(alpha, 0x90, 0x000000));
        g.fill(ix - 3, iy - 3, ix + 19, iy - 2, a(alpha, 0xC0, accent));
        g.fill(ix - 3, iy + 18, ix + 19, iy + 19, a(alpha, 0xC0, accent));
        if (alpha > 0.3f && !current.icon().isEmpty()) g.renderItem(current.icon(), ix, iy);
        // текст
        int tx = x0 + 38;
        int ta = Mth.clamp((int) (alpha * 255), 8, 255);
        g.drawString(mc.font, header, tx, y0 + 7, (ta << 24) | accent, false);
        g.drawString(mc.font, title, tx, y0 + 19, (ta << 24) | 0xFFF4E6, true);
        // искры для испытаний
        if (challenge) {
            for (int i = 0; i < 8; i++) {
                double ang = t / 900.0 + i * Math.PI / 4;
                int sx = (int) (Math.cos(ang) * (w / 2f + 8));
                int sy = (int) (Math.sin(ang) * (h / 2f + 6));
                g.fill(sx, sy, sx + 1, sy + 1, a(alpha, 0xD0, 0xFFF0B0));
            }
        }
        g.pose().popPose();
    }

    private static int a(float alpha, int base, int rgb) {
        return (Mth.clamp((int) (base * alpha), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, col);
        }
    }

    private PlaqueOverlay() {}
}
