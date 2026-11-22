package dev.zalrpakraft.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ModListScreen;

import java.util.ArrayList;
import java.util.List;

/** Главное меню сборки: живое звёздное небо, кольцо из 12 глаз, логотип и стилизованные кнопки. */
public class ZTitleScreen extends Screen {
    private static final int[] EYE_COLORS = {0xE3A15F, 0xD8F4FF, 0xFFF2A0, 0xE6E0FF, 0x5FE0D0, 0xFFD24A,
            0xFF7A2E, 0x4FB8C9, 0xFFE9B0, 0xF2C55C, 0xF7E26B, 0xC9B7FF};
    private static final int SPLASHES = 8;

    private final List<float[]> stars = new ArrayList<>();
    private final long openedAt = Util.getMillis();
    private float[] meteor; // x, y, vx, vy, life
    private final RandomSource rnd = RandomSource.create();
    private final int splash = RandomSource.create().nextInt(SPLASHES);
    private String versionLine = "";

    public ZTitleScreen() {
        super(Component.translatable("zalrpakraft.menu.title"));
        RandomSource r = RandomSource.create(1337);
        for (int i = 0; i < 700; i++)
            stars.add(new float[]{r.nextFloat(), r.nextFloat(), 0.2f + r.nextFloat() * 0.8f, r.nextFloat() * 6.28f, 0.2f + r.nextFloat()});
    }

    @Override
    protected void init() {
        int bw = Math.min(204, width - 20);
        int gap = height < 260 ? 3 : 5;
        int bh = height < 260 ? 20 : 22;
        int rows = 4;
        int block = rows * bh + (rows - 1) * gap;
        int x = width / 2 - bw / 2;
        int y = Math.min(logoBottom() + 12, height - 26 - block);
        y = Math.max(y, logoBottom() + 4);
        addRenderableWidget(new StarButton(x, y, bw, bh, Component.translatable("menu.singleplayer"),
                () -> minecraft.setScreen(new SelectWorldScreen(this))));
        y += bh + gap;
        addRenderableWidget(new StarButton(x, y, bw, bh, Component.translatable("menu.multiplayer"),
                () -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
        y += bh + gap;
        addRenderableWidget(new StarButton(x, y, bw, bh, Component.translatable("fml.menu.mods"),
                () -> minecraft.setScreen(new ModListScreen(this))));
        y += bh + gap;
        int third = (bw - 2 * gap) / 3;
        addRenderableWidget(new StarButton(x, y, third, bh, Component.translatable("menu.options"),
                () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new StarButton(x + third + gap, y, third, bh, Component.translatable("options.language"),
                () -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()))));
        addRenderableWidget(new StarButton(x + 2 * (third + gap), y, bw - 2 * (third + gap), bh, Component.translatable("zalrpakraft.menu.quit"),
                () -> minecraft.stop()));

        String neo = ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        String own = ModList.get().getModContainerById("zalrpakraft").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        versionLine = "Minecraft 1.21.1 · NeoForge " + neo + " · Zalepacraft " + own + " · " + ModList.get().size() + " mods";
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        float t = (Util.getMillis() - openedAt) / 50f;
        StarSky.render(g, width, height, mx, my);
        // кольцо из 12 глаз вокруг логотипа
        float cx = width / 2f, cy = logoY() + 12;
        float rx = Math.min(width * 0.36f, 210), ry = rx * 0.32f;
        for (int i = 0; i < 12; i++) {
            double a = t * 0.004 + i * Math.PI / 6;
            float ex = cx + (float) Math.cos(a) * rx, ey = cy + (float) Math.sin(a) * ry;
            float depth = 0.5f + 0.5f * (float) Math.sin(a);
            int al = (int) (60 + 150 * depth);
            disc(g, ex, ey, 2.5f + depth * 2f, (al << 24) | EYE_COLORS[i]);
            disc(g, ex, ey, 6f + depth * 3f, ((int) (18 * depth) << 24) | EYE_COLORS[i]);
        }
        // затемнение под кнопками
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0x70000000);
    }

    private float logoScale() {
        return Math.min(4.2f, Math.min(width * 0.78f / font.width("ZALEPACRAFT"), height / 64f));
    }

    private float logoY() {
        return Math.max(14, height * 0.16f);
    }

    private int logoBottom() {
        return (int) (logoY() + 9 * logoScale() + 18);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        float t = (Util.getMillis() - openedAt) / 1000f;
        float fade = Mth.clamp(t / 1.2f, 0f, 1f);

        // логотип
        Component logo = Component.literal("ZALEPACRAFT");
        int lw = font.width(logo);
        float scale = logoScale();
        float ly = logoY();
        g.pose().pushPose();
        g.pose().translate(width / 2f, ly, 0);
        g.pose().scale(scale, scale, 1);
        int x = -lw / 2;
        int alpha = Mth.clamp((int) (fade * 255), 8, 255);
        for (int i = 3; i >= 1; i--) // свечение
            g.drawString(font, logo, x, i, ((int) (alpha * 0.22f) << 24) | 0x6A3FD0, false);
        g.drawString(font, logo, x + 1, 1, (alpha << 24) | 0x1A0E30, false);
        g.drawString(font, logo, x, 0, (alpha << 24) | 0xF1E6FF, false);
        // блик бежит по буквам
        float sweep = (t * 0.35f) % 1.6f;
        if (sweep < 1f) {
            int sx = (int) (x + sweep * lw);
            g.fill(sx, -1, sx + 2, 9, ((int) (alpha * 0.5f) << 24) | 0xFFFFFF);
        }
        g.pose().popPose();
        Component sub = Component.translatable("zalrpakraft.menu.subtitle");
        g.drawCenteredString(font, sub, width / 2, (int) (ly + 9 * scale + 6), (alpha << 24) | 0xB79CFF);

        // фраза дня: начинается у правого края логотипа и уходит вправо-вверх, сжимается, если не влезает
        Component sp = Component.translatable("zalrpakraft.menu.splash" + splash);
        float ax = width / 2f + lw * scale / 2f - 4;
        float room = width - ax - 6;
        float fit = Mth.clamp(room / Math.max(1, font.width(sp)), 0.55f, 1.0f);
        g.pose().pushPose();
        g.pose().translate(ax, ly + 4, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-16f));
        float s = fit * (1.0f + Math.abs(Mth.sin(t * 3.4f)) * 0.06f);
        g.pose().scale(s, s, 1);
        g.drawString(font, sp, 0, -4, (alpha << 24) | 0xFFE08A, true);
        g.pose().popPose();

        String copy = "Copyright Mojang AB. Do not distribute!";
        int cw = font.width(copy);
        g.drawString(font, copy, width - cw - 4, height - 10, 0x907A70A0, false);
        if (font.width(versionLine) + cw + 16 < width) g.drawString(font, versionLine, 4, height - 10, 0x907A70A0, false);
        else g.drawString(font, versionLine, width - font.width(versionLine) - 4, height - 20, 0x907A70A0, false);
    }

    private static void disc(GuiGraphics g, float cx, float cy, float r, int col) {
        int ri = Math.max(1, (int) r);
        for (int dy = -ri; dy <= ri; dy++) {
            int w = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill((int) cx - w, (int) cy + dy, (int) cx + w + 1, (int) cy + dy + 1, col);
        }
    }

    private static void nebula(GuiGraphics g, float cx, float cy, float r, int rgb) {
        for (int i = 7; i >= 1; i--) disc(g, cx, cy, r * i / 7f, (5 << 24) | rgb);
    }
}
