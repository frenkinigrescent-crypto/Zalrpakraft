package dev.zalrpakraft.client;

import dev.zalrpakraft.data.ProgressionMap;
import dev.zalrpakraft.data.ProgressionMap.Kind;
import dev.zalrpakraft.data.ProgressionMap.Node;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Звёздная карта: галактики-группы модов, планеты-моды, метеоры-боссы, врата-измерения.
 * Перетаскивание — ЛКМ, масштаб — колесо.
 */
public class ConstellationScreen extends Screen {
    private final Map<String, Node> nodes;
    private final Set<String> studied = new HashSet<>();
    private final List<float[]> stars = new ArrayList<>(); // x, y, яркость, фаза, параллакс
    private float panX, panY, zoom = 1f, targetZoom = 1f;
    private int ticks;
    private Node hovered;

    public ConstellationScreen(String json, CompoundTag progress) {
        super(Component.translatable("zalrpakraft.map.title"));
        Map<String, Node> parsed;
        try {
            parsed = ProgressionMap.parse(json);
        } catch (Exception e) {
            parsed = Map.of();
        }
        this.nodes = parsed;
        ListTag l = progress.getList("studied", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) studied.add(l.getString(i));
        RandomSource r = RandomSource.create(42);
        for (int i = 0; i < 520; i++) {
            stars.add(new float[]{(r.nextFloat() - 0.5f) * 2400, (r.nextFloat() - 0.5f) * 1800,
                    0.25f + r.nextFloat() * 0.75f, r.nextFloat() * 6.28f, 0.2f + r.nextFloat() * 0.6f});
        }
        zoom = 0.55f;
    }

    @Override
    protected void init() {
        addRenderableWidget(new StarButton(width - 128, 6, 120, 18, Component.translatable("zalrpakraft.book.title"),
                () -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.zalrpakraft.net.SimplePayload("open_quests"))));
        float minX = 0, maxX = 0, minY = 0, maxY = 0;
        for (Node n : nodes.values()) {
            if (hidden(n)) continue;
            minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
            minY = Math.min(minY, n.y); maxY = Math.max(maxY, n.y);
        }
        float fit = Math.min((width - 40) / Math.max(1f, maxX - minX + 80), (height - 70) / Math.max(1f, maxY - minY + 80));
        targetZoom = Mth.clamp(fit, 0.3f, 1.2f);
        zoom = targetZoom * 0.8f;
        panX = -(minX + maxX) / 2f;
        panY = -(minY + maxY) / 2f + 6 / targetZoom;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
    }

    /** Самотест: показывать планеты модов, которых нет в сборке. */
    public static boolean showAll = false;

    private boolean hidden(Node n) {
        return !showAll && n.kind == Kind.PLANET && !n.mod.isEmpty() && !ModList.get().isLoaded(n.mod);
    }

    private boolean isStudied(Node n) {
        return studied.contains(n.id);
    }

    private boolean isAvailable(Node n) {
        return ProgressionMap.available(n, studied::contains);
    }

    private float sx(float x) { return width / 2f + (x + panX) * zoom; }
    private float sy(float y) { return height / 2f + (y + panY) * zoom; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // не затемняем мир стандартно — рисуем своё небо
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float time = ticks + pt;
        zoom = Mth.lerp(0.18f, zoom, targetZoom);

        // Небо
        g.fillGradient(0, 0, width, height, 0xF6060A1A, 0xF6150A26);
        // туманности
        nebula(g, sx(-250), sy(-60), 220 * zoom, 0x10, 0x3A6BD1);
        nebula(g, sx(260), sy(120), 260 * zoom, 0x0E, 0x9B4DCB);
        nebula(g, sx(0), sy(-420), 180 * zoom, 0x0C, 0xD15FA0);
        // звёзды фона (с параллаксом)
        for (float[] s : stars) {
            float x = width / 2f + (s[0] + panX * s[4]) * (0.6f + zoom * 0.4f);
            float y = height / 2f + (s[1] + panY * s[4]) * (0.6f + zoom * 0.4f);
            if (x < 0 || y < 0 || x > width || y > height) continue;
            float tw = 0.55f + 0.45f * Mth.sin(time * 0.08f + s[3]);
            int a = (int) (255 * s[2] * tw);
            g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, (Mth.clamp(a, 0, 255) << 24) | 0xFFFFFF);
        }

        // Связи
        for (Node n : nodes.values()) {
            if (hidden(n)) continue;
            List<String> links = new ArrayList<>(n.requires);
            links.addAll(n.requiresAny);
            for (String rid : links) {
                Node r = nodes.get(rid);
                if (r == null || hidden(r)) continue;
                if (n.kind == Kind.PLANET && r.kind == Kind.GALAXY) continue; // планеты и так вокруг галактики
                if (r.kind == Kind.ROOT && n.kind != Kind.GALAXY) continue;      // не тянем лучи от стола ко всему подряд
                if (!isStudied(n) && !isAvailable(n) && !isStudied(r)) continue;  // показываем только освоенное и фронтир
                boolean lit = isStudied(n) && isStudied(r);
                boolean open = isStudied(r);
                int col = lit ? (0xC0000000 | (n.color & 0xFFFFFF)) : open ? 0x70B8A8E8 : 0x28FFFFFF;
                dotted(g, sx(r.x), sy(r.y), sx(n.x), sy(n.y), col, lit ? 2 : 4);
            }
        }

        // Узлы
        hovered = null;
        float pulse = 0.5f + 0.5f * Mth.sin(time * 0.15f);
        for (Node n : nodes.values()) {
            if (hidden(n)) continue;
            float x = sx(n.x), y = sy(n.y);
            if (x < -60 || y < -60 || x > width + 60 || y > height + 60) continue;
            boolean st = isStudied(n), av = isAvailable(n);
            int rgb = n.color & 0xFFFFFF;
            float hitR;
            switch (n.kind) {
                case ROOT -> {
                    disc(g, x, y, 14 * zoom, (0x40 << 24) | 0xFFFFFF);
                    flare(g, x, y, 20 * zoom, 0xFFFFFFFF);
                    disc(g, x, y, 4 * zoom + 1, 0xFFFFFFFF);
                    hitR = 12 * zoom;
                }
                case GALAXY -> {
                    float r = 30 * zoom;
                    disc(g, x, y, r, ((st ? 0x26 : 0x0E) << 24) | rgb);
                    ring(g, x, y, r, ((st ? 0x90 : 0x30) << 24) | rgb);
                    ring(g, x, y, r * 0.55f, ((st ? 0x40 : 0x18) << 24) | rgb);
                    hitR = 0; // галактику подписываем, но не выделяем
                    label(g, st || av ? Component.literal(n.name) : Component.literal("???"), x, y + r + 3, st ? (0xFF000000 | rgb) : 0xFF6E6680);
                }
                case PLANET -> {
                    float r = 5.5f * zoom + 1;
                    if (st) {
                        disc(g, x, y, r + 3 * zoom, (0x30 << 24) | rgb);
                        disc(g, x, y, r, 0xFF000000 | rgb);
                    } else if (av) {
                        ring(g, x, y, r + pulse * 2 * zoom, ((0x60 + (int) (pulse * 0x80)) << 24) | rgb);
                        disc(g, x, y, r * 0.6f, (0x90 << 24) | rgb);
                    } else {
                        disc(g, x, y, r * 0.7f, 0xFF3A3448);
                    }
                    hitR = r + 3;
                }
                case METEOR -> {
                    float r = 6 * zoom + 1;
                    int c = st ? 0xFF000000 | rgb : av ? ((0x80 + (int) (pulse * 0x7F)) << 24) | rgb : 0xFF4A3030;
                    // хвост
                    for (int i = 1; i <= 6; i++) {
                        int a = (int) ((st || av ? 0x70 : 0x30) * (1f - i / 7f));
                        disc(g, x + i * 3 * zoom, y - i * 2 * zoom, Math.max(1, r * (1f - i / 8f)), (a << 24) | (st || av ? rgb : 0x4A3030));
                    }
                    diamond(g, x, y, r, c);
                    hitR = r + 3;
                }
                case GATE -> {
                    float r = 11 * zoom + 1;
                    int a = st ? 0xFF : av ? 0x80 + (int) (pulse * 0x7F) : 0x40;
                    ring(g, x, y, r, (a << 24) | rgb);
                    ring(g, x, y, r - 3, (a / 2 << 24) | rgb);
                    if (st) disc(g, x, y, r - 5, (0x50 << 24) | rgb);
                    label(g, Component.literal(n.name), x, y + r + 3, st ? (0xFF000000 | rgb) : 0xFF8C8399);
                    hitR = r + 2;
                }
                default -> hitR = 0;
            }
            if (hitR > 0 && (mx - x) * (mx - x) + (my - y) * (my - y) <= hitR * hitR) hovered = n;
        }

        // Заголовок и счётчик
        int total = 0, done = 0;
        for (Node n : nodes.values()) {
            if (hidden(n) || n.kind == Kind.GALAXY || n.kind == Kind.ROOT) continue;
            total++;
            if (isStudied(n)) done++;
        }
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, 8, 0xFFE9DDFF);
        g.drawCenteredString(font, Component.translatable("zalrpakraft.map.progress", done, total), width / 2, 20, 0xFF9A8CC0);
        // Легенда
        int ly = height - 44;
        legend(g, 8, ly, Kind.PLANET, 0xFF7FD36B, "zalrpakraft.map.legend.planet");
        legend(g, 8, ly + 11, Kind.METEOR, 0xFFFF6A3D, "zalrpakraft.map.legend.meteor");
        legend(g, 8, ly + 22, Kind.GATE, 0xFFD7B6FF, "zalrpakraft.map.legend.gate");
        g.drawString(font, Component.translatable("zalrpakraft.map.controls"), 8, height - 10, 0xFF5E5675, false);

        if (hovered != null) g.renderComponentTooltip(font, tooltip(hovered), mx, my);
        super.render(g, mx, my, pt);
    }

    private List<Component> tooltip(Node n) {
        List<Component> t = new ArrayList<>();
        boolean st = isStudied(n), av = isAvailable(n);
        if (!st && !av) {
            t.add(Component.literal("???").withStyle(ChatFormatting.DARK_GRAY));
            t.add(Component.translatable("zalrpakraft.map.locked").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return t;
        }
        t.add(Component.literal(n.name).withColor(n.color));
        String kind = switch (n.kind) {
            case PLANET -> "zalrpakraft.map.kind.planet";
            case METEOR -> "zalrpakraft.map.kind.meteor";
            case GATE -> "zalrpakraft.map.kind.gate";
            case ROOT -> "zalrpakraft.map.kind.root";
            default -> "zalrpakraft.map.kind.galaxy";
        };
        t.add(Component.translatable(kind).withStyle(ChatFormatting.DARK_PURPLE));
        if (!n.desc.isEmpty()) t.add(Component.literal(n.desc).withStyle(ChatFormatting.GRAY));
        if (!n.hint.isEmpty()) t.add(Component.literal(n.hint).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        t.add(st ? Component.translatable("zalrpakraft.map.studied").withStyle(ChatFormatting.GREEN)
                : Component.translatable("zalrpakraft.map.available").withStyle(ChatFormatting.YELLOW));
        return t;
    }

    private void legend(GuiGraphics g, int x, int y, Kind k, int col, String key) {
        switch (k) {
            case METEOR -> diamond(g, x + 4, y + 4, 3.5f, col);
            case GATE -> ring(g, x + 4, y + 4, 4, col);
            default -> disc(g, x + 4, y + 4, 3.5f, col);
        }
        g.drawString(font, Component.translatable(key), x + 12, y, 0xFFA79CC0, false);
    }

    private void label(GuiGraphics g, Component c, float x, float y, int col) {
        if (zoom < 0.45f) return;
        g.drawCenteredString(font, c, (int) x, (int) y, col);
    }

    // ---------- примитивы ----------
    private static void disc(GuiGraphics g, float cx, float cy, float r, int col) {
        int ri = Math.max(1, (int) r);
        for (int dy = -ri; dy <= ri; dy++) {
            int w = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill((int) cx - w, (int) cy + dy, (int) cx + w + 1, (int) cy + dy + 1, col);
        }
    }

    private static void ring(GuiGraphics g, float cx, float cy, float r, int col) {
        int steps = Math.max(16, (int) (r * 4));
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = (int) (cx + Math.cos(a) * r), y = (int) (cy + Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    private static void diamond(GuiGraphics g, float cx, float cy, float r, int col) {
        int ri = Math.max(1, (int) r);
        for (int dy = -ri; dy <= ri; dy++) {
            int w = ri - Math.abs(dy);
            g.fill((int) cx - w, (int) cy + dy, (int) cx + w + 1, (int) cy + dy + 1, col);
        }
    }

    private static void flare(GuiGraphics g, float cx, float cy, float len, int col) {
        int l = (int) len;
        for (int i = -l; i <= l; i++) {
            int a = (int) (255 * (1f - Math.abs(i) / (float) (l + 1)));
            int c = (Mth.clamp(a, 0, 255) << 24) | (col & 0xFFFFFF);
            g.fill((int) cx + i, (int) cy, (int) cx + i + 1, (int) cy + 1, c);
            g.fill((int) cx, (int) cy + i, (int) cx + 1, (int) cy + i + 1, c);
        }
    }

    private static void nebula(GuiGraphics g, float cx, float cy, float r, int alpha, int rgb) {
        for (int i = 6; i >= 1; i--) disc(g, cx, cy, r * i / 6f, ((alpha / 2 + 1) << 24) | rgb);
    }

    private static void dotted(GuiGraphics g, float x1, float y1, float x2, float y2, int col, int step) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        for (float d = 0; d <= len; d += step) {
            int x = (int) (x1 + dx * d / len), y = (int) (y1 + dy * d / len);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    // ---------- управление ----------
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        panX += (float) (dx / zoom);
        panY += (float) (dy / zoom);
        panX = Mth.clamp(panX, -900, 900);
        panY = Mth.clamp(panY, -900, 900);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        targetZoom = Mth.clamp(targetZoom * (sy > 0 ? 1.15f : 1f / 1.15f), 0.3f, 2.6f);
        return true;
    }
}
