package dev.zalrpakraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zalrpakraft.data.ProgressionMap;
import dev.zalrpakraft.net.SimplePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Звёздная карта в духе Create: Constellation.
 * Галактика (системы-моды, метеоры, врата) → система (солнце и планеты на орбитах) → планета (узлы-предметы).
 */
public class StarMapScreen extends Screen {
    enum Level { GALAXY, SYSTEM, PLANET }

    // ---- данные ----
    private record Sys(String id, String name, int color, float x, float y, int total, int done, int[] planets) {}
    private record PNode(String item, ItemStack stack, float x, float y, int cost, int exp, char cat, List<String> parents) {}
    private record Pl(String id, String name, int color, List<PNode> nodes) {}

    private final List<Sys> systems = new ArrayList<>();
    private final Map<String, ProgressionMap.Node> mapNodes = new LinkedHashMap<>();
    private final Set<String> studied = new HashSet<>();
    private String sysId, sysName;
    private int sysColor;
    private final List<Pl> planets = new ArrayList<>();

    // ---- вид ----
    private Level level = Level.GALAXY;
    private int planetIdx;
    private float camX, camY, zoom = 0.6f, tZoom = 1f;
    private float galaxyR = 300;
    private int ticks;
    private final List<float[]> stars = new ArrayList<>();
    private String pendingSystem;

    public StarMapScreen() {
        super(Component.translatable("zalrpakraft.map.title"));
        RandomSource r = RandomSource.create(42);
        for (int i = 0; i < 600; i++)
            stars.add(new float[]{(r.nextFloat() - 0.5f) * 3000, (r.nextFloat() - 0.5f) * 2200, 0.25f + r.nextFloat() * 0.75f, r.nextFloat() * 6.28f, 0.15f + r.nextFloat() * 0.5f});
    }

    // ---------- данные с сервера ----------
    public void setGalaxy(String json, String mapJson, CompoundTag progress) {
        systems.clear();
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        galaxyR = 200;
        for (JsonElement e : o.getAsJsonArray("systems")) {
            JsonObject s = e.getAsJsonObject();
            JsonArray pa = s.getAsJsonArray("planets");
            int[] pc = new int[pa.size()];
            for (int i = 0; i < pc.length; i++) pc[i] = pa.get(i).getAsInt();
            Sys sys = new Sys(s.get("id").getAsString(), s.get("name").getAsString(), s.get("color").getAsInt(),
                    s.get("x").getAsFloat(), s.get("y").getAsFloat(), s.get("total").getAsInt(), s.get("done").getAsInt(), pc);
            systems.add(sys);
            galaxyR = Math.max(galaxyR, (float) Math.hypot(sys.x, sys.y) + 60);
        }
        mapNodes.clear();
        try {
            mapNodes.putAll(ProgressionMap.parse(mapJson));
        } catch (Exception ignored) {
        }
        studied.clear();
        ListTag l = progress.getList("studied", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) studied.add(l.getString(i));
        if (level == Level.GALAXY) fitGalaxy();
    }

    public void setSystem(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        boolean same = o.get("id").getAsString().equals(sysId);
        sysId = o.get("id").getAsString();
        sysName = o.get("name").getAsString();
        sysColor = o.get("color").getAsInt();
        planets.clear();
        for (JsonElement pe : o.getAsJsonArray("planets")) {
            JsonObject p = pe.getAsJsonObject();
            List<PNode> nodes = new ArrayList<>();
            for (JsonElement ne : p.getAsJsonArray("nodes")) {
                JsonObject n = ne.getAsJsonObject();
                List<String> par = new ArrayList<>();
                if (n.has("p")) for (JsonElement x : n.getAsJsonArray("p")) par.add(x.getAsString());
                String item = n.get("i").getAsString();
                ResourceLocation rl = ResourceLocation.tryParse(item);
                ItemStack st = rl != null && BuiltInRegistries.ITEM.containsKey(rl) ? new ItemStack(BuiltInRegistries.ITEM.get(rl)) : ItemStack.EMPTY;
                nodes.add(new PNode(item, st, n.get("x").getAsFloat(), n.get("y").getAsFloat(), n.get("c").getAsInt(), n.get("e").getAsInt(),
                        n.get("k").getAsString().charAt(0), par));
            }
            planets.add(new Pl(p.get("id").getAsString(), p.get("name").getAsString(), p.get("color").getAsInt(), nodes));
        }
        if (!same || level == Level.GALAXY) {
            level = Level.SYSTEM;
            camX = camY = 0;
            zoom = 0.5f;
            tZoom = systemFit();
        }
        pendingSystem = null;
    }

    private void fitGalaxy() {
        float outer = galaxyR + 220;
        tZoom = Mth.clamp(Math.min(width, height) / 2f / outer, 0.12f, 1.2f);
        zoom = tZoom * 0.8f;
        camX = camY = 0;
    }

    private float systemFit() {
        float r = 70 + planets.size() * 52 + 40;
        return Mth.clamp(Math.min(width, height) / 2f / r, 0.3f, 1.4f);
    }

    private float planetFit() {
        float r = 60;
        for (PNode n : planets.get(planetIdx).nodes) r = Math.max(r, (float) Math.hypot(n.x, n.y) + 24);
        // большие планеты не ужимаем до нечитаемого: остальное — колесом и перетаскиванием
        return Mth.clamp((Math.min(width, height - 30) / 2f) / r, 0.8f, 1.6f);
    }

    @Override
    protected void init() {
        if (level == Level.GALAXY) fitGalaxy();
        addRenderableWidget(new StarButton(width - 124, height - 24, 116, 18, Component.translatable("zalrpakraft.book.title"),
                () -> PacketDistributor.sendToServer(new SimplePayload("open_quests"))));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
    }

    private float sx(float x) { return width / 2f + (x - camX) * zoom; }
    private float sy(float y) { return height / 2f + (y - camY) * zoom; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // небо рисуется в render(), до узлов
    }

    private void sky(GuiGraphics g, float pt) {
        float t = ticks + pt;
        g.fillGradient(0, 0, width, height, 0xFF080C1E, 0xFF0B0E22);
        for (float[] s : stars) {
            float x = width / 2f + (s[0] - camX * s[4]) * 0.5f;
            float y = height / 2f + (s[1] - camY * s[4]) * 0.5f;
            if (x < 0 || y < 0 || x > width || y > height) continue;
            int a = Mth.clamp((int) (255 * s[2] * (0.55f + 0.45f * Mth.sin(t * 0.07f + s[3]))), 0, 255);
            int sz = s[2] > 0.85f ? 2 : 1;
            g.fill((int) x, (int) y, (int) x + sz, (int) y + sz, (a << 24) | 0xDDE4FF);
        }
    }

    // ==================== РИСОВАНИЕ ====================
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        zoom = Mth.lerp(0.2f, zoom, tZoom);
        sky(g, pt);
        float t = ticks + pt;
        List<Component> tip = null;
        switch (level) {
            case GALAXY -> tip = renderGalaxy(g, mx, my, t);
            case SYSTEM -> tip = renderSystem(g, mx, my, t);
            case PLANET -> tip = renderPlanet(g, mx, my, t);
        }
        renderHud(g);
        super.render(g, mx, my, pt);
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    private void renderHud(GuiGraphics g) {
        // очки и EXP
        int x = 8, y = 8;
        Component pts = Component.translatable("zalrpakraft.star.points", ClientTree.points);
        g.drawString(font, Component.literal("✦ ").append(pts), x, y, 0xFFFFD27A, true);
        int bw = 90, by = y + 12;
        g.fill(x, by, x + bw, by + 3, 0xFF2A2440);
        g.fill(x, by, x + (int) (bw * Math.min(1f, ClientTree.exp / (float) ClientTree.expPerPoint)), by + 3, 0xFFB79CFF);
        g.drawString(font, ClientTree.exp + "/" + ClientTree.expPerPoint + " EXP", x + bw + 4, by - 2, 0xFF9A8CC0, false);
        // хлебные крошки
        String crumb = Component.translatable("zalrpakraft.map.galaxy").getString();
        if (level != Level.GALAXY && sysName != null) crumb += "  ›  " + sysName;
        if (level == Level.PLANET && planetIdx < planets.size()) crumb += "  ›  " + planets.get(planetIdx).name;
        g.drawCenteredString(font, crumb, width / 2, 8, 0xFFE9DDFF);
        Component hint = Component.translatable(level == Level.GALAXY ? "zalrpakraft.map.hint.galaxy" : "zalrpakraft.map.hint.inner");
        g.drawString(font, hint, 8, height - 11, 0xFF5E5675, false);
        if (pendingSystem != null) g.drawCenteredString(font, Component.translatable("zalrpakraft.map.loading"), width / 2, height / 2, 0xFFB79CFF);
    }

    private List<Component> renderGalaxy(GuiGraphics g, int mx, int my, float t) {
        List<Component> tip = null;
        // метеоры и врата — на внешнем кольце
        for (ProgressionMap.Node n : mapNodes.values()) {
            if (n.kind != ProgressionMap.Kind.METEOR && n.kind != ProgressionMap.Kind.GATE) continue;
            float[] p = outer(n);
            float x = sx(p[0]), y = sy(p[1]);
            boolean st = studied.contains(n.id), av = ProgressionMap.available(n, studied::contains);
            int rgb = n.color & 0xFFFFFF;
            float pulse = 0.5f + 0.5f * Mth.sin(t * 0.15f);
            float r = Math.max(3, 7 * zoom);
            if (n.kind == ProgressionMap.Kind.METEOR) {
                for (int i = 1; i <= 6; i++) {
                    int a = (int) ((st || av ? 0x70 : 0x30) * (1f - i / 7f));
                    disc(g, x + i * 3 * zoom, y - i * 2 * zoom, Math.max(1, r * (1f - i / 8f)), (a << 24) | (st || av ? rgb : 0x4A3030));
                }
                diamond(g, x, y, r, st ? 0xFF000000 | rgb : av ? ((0x80 + (int) (pulse * 0x7F)) << 24) | rgb : 0xFF4A3030);
            } else {
                float rr = Math.max(5, 12 * zoom);
                int a = st ? 0xFF : av ? 0x80 + (int) (pulse * 0x7F) : 0x50;
                ring(g, x, y, rr, (a << 24) | rgb);
                ring(g, x, y, rr - 3, ((a / 2) << 24) | rgb);
                if (zoom > 0.25f) g.drawCenteredString(font, n.name, (int) x, (int) (y + rr + 3), st ? 0xFF000000 | rgb : 0xFF8C8399);
            }
            if (dist(mx, my, x, y) < Math.max(8, r + 4)) {
                tip = new ArrayList<>();
                if (!st && !av) tip.add(Component.literal("???").withStyle(ChatFormatting.DARK_GRAY));
                else {
                    tip.add(Component.literal(n.name).withColor(rgb));
                    tip.add(Component.translatable(n.kind == ProgressionMap.Kind.METEOR ? "zalrpakraft.map.kind.meteor" : "zalrpakraft.map.kind.gate").withStyle(ChatFormatting.DARK_PURPLE));
                    if (!n.hint.isEmpty()) tip.add(Component.literal(n.hint).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    tip.add(st ? Component.translatable("zalrpakraft.map.studied").withStyle(ChatFormatting.GREEN)
                            : Component.translatable("zalrpakraft.map.available").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        // системы
        for (Sys s : systems) {
            float x = sx(s.x), y = sy(s.y);
            if (x < -80 || y < -80 || x > width + 80 || y > height + 80) continue;
            float r = (8 + (float) Math.sqrt(s.total) * 1.3f) * zoom;
            r = Math.max(3, Math.min(r, 40 * zoom + 4));
            for (int i = 4; i >= 1; i--) disc(g, x, y, r * (1 + i * 0.35f), ((int) (14 / (float) i * 1.4f) << 24) | s.color);
            sun(g, x, y, r, s.color);
            // крошечные планеты
            for (int i = 0; i < s.planets.length - 1; i++) {
                double a = t * 0.01 * (1 + i * 0.3) + i * 2.1;
                float pr = r + (6 + i * 4) * zoom;
                disc(g, x + (float) Math.cos(a) * pr, y + (float) Math.sin(a) * pr, Math.max(1, 1.6f * zoom + 0.6f), 0xFF000000 | s.planets[i + 1]);
            }
            if (zoom > 0.2f) {
                g.drawCenteredString(font, s.name, (int) x, (int) (y + r + 4), 0xFFE6DDF8);
                if (zoom > 0.35f) g.drawCenteredString(font, s.done + "/" + s.total, (int) x, (int) (y + r + 14), 0xFF8C8399);
            }
            if (dist(mx, my, x, y) < r + 4) {
                tip = List.of(Component.literal(s.name).withColor(s.color),
                        Component.translatable("zalrpakraft.map.system.progress", s.done, s.total).withStyle(ChatFormatting.GRAY),
                        Component.translatable("zalrpakraft.map.system.open").withStyle(ChatFormatting.YELLOW));
            }
        }
        return tip;
    }

    private float[] outer(ProgressionMap.Node n) {
        double len = Math.hypot(n.x, n.y);
        if (len < 1) return new float[]{0, -(galaxyR + 120)};
        double k = (galaxyR + 110 + len * 0.25) / len;
        return new float[]{(float) (n.x * k), (float) (n.y * k)};
    }

    private float orbitR(int i) { return 70 + i * 52; }

    private float[] planetPos(int i, float t) {
        double a = t * 0.004 * (1.6 / (1 + i * 0.35)) + i * 2.399;
        return new float[]{(float) Math.cos(a) * orbitR(i), (float) Math.sin(a) * orbitR(i)};
    }

    private List<Component> renderSystem(GuiGraphics g, int mx, int my, float t) {
        List<Component> tip = null;
        if (planets.isEmpty()) return null;
        float cx = sx(0), cy = sy(0);
        // орбиты
        for (int i = 1; i < planets.size(); i++) dottedCircle(g, cx, cy, orbitR(i) * zoom, 0x90000000 | (planets.get(i).color & 0xFFFFFF));
        // солнце
        float sr = 30 * zoom + 2;
        for (int i = 4; i >= 1; i--) disc(g, cx, cy, sr * (1 + i * 0.3f), ((int) (22 / (float) i) << 24) | (planets.get(0).color & 0xFFFFFF));
        sun(g, cx, cy, sr, planets.get(0).color);
        g.drawCenteredString(font, sysName, (int) cx, (int) (cy - sr - 12), 0xFFFFE6A6);
        int tw = font.width(planets.get(0).name);
        g.fill((int) cx - tw / 2 - 3, (int) cy - 5, (int) cx + tw / 2 + 3, (int) cy + 6, 0x90000000);
        g.drawCenteredString(font, planets.get(0).name, (int) cx, (int) cy - 4, 0xFFFFF4E0);
        if (dist(mx, my, cx, cy) < sr) tip = planetTip(0);
        // планеты
        for (int i = 1; i < planets.size(); i++) {
            Pl p = planets.get(i);
            float[] pos = planetPos(i, t);
            float x = sx(pos[0]), y = sy(pos[1]);
            float pr = (7 + Math.min(6, p.nodes.size() / 8f)) * zoom + 2;
            sun(g, x, y, pr, p.color);
            g.drawCenteredString(font, p.name, (int) x, (int) (y + pr + 4), 0xFFE6DDF8);
            if (dist(mx, my, x, y) < pr + 4) tip = planetTip(i);
        }
        return tip;
    }

    private List<Component> planetTip(int i) {
        Pl p = planets.get(i);
        int done = 0;
        for (PNode n : p.nodes) if (ClientTree.UNLOCKED.contains(n.item)) done++;
        return List.of(Component.literal(p.name).withColor(p.color),
                Component.translatable("zalrpakraft.map.system.progress", done, p.nodes.size()).withStyle(ChatFormatting.GRAY),
                Component.translatable("zalrpakraft.map.planet.open").withStyle(ChatFormatting.YELLOW));
    }

    private List<Component> renderPlanet(GuiGraphics g, int mx, int my, float t) {
        if (planetIdx >= planets.size()) return null;
        Pl p = planets.get(planetIdx);
        Map<String, PNode> byId = new HashMap<>();
        for (PNode n : p.nodes) byId.put(n.item, n);
        float cx = sx(0), cy = sy(0);
        // связи
        for (PNode n : p.nodes) {
            boolean un = ClientTree.UNLOCKED.contains(n.item);
            if (n.parents.isEmpty()) {
                boolean lit = un;
                line(g, cx, cy, sx(n.x), sy(n.y), lit ? 0xFFE8B84A : 0xFF2A3150, lit ? 2 : 1);
            }
            for (String par : n.parents) {
                PNode pn = byId.get(par);
                if (pn == null) continue;
                boolean lit = un && ClientTree.UNLOCKED.contains(par);
                line(g, sx(pn.x), sy(pn.y), sx(n.x), sy(n.y), lit ? 0xFFE8B84A : 0xFF2A3150, lit ? 2 : 1);
            }
        }
        // планета в центре
        float pr = 34 * zoom + 2;
        for (int i = 4; i >= 1; i--) disc(g, cx, cy, pr * (1 + i * 0.22f), ((int) (26 / (float) i) << 24) | (p.color & 0xFFFFFF));
        sun(g, cx, cy, pr, p.color);
        dottedCircle(g, cx, cy, pr + 4, 0xA0FFE08A);
        // узлы
        List<Component> tip = null;
        float nr = 15 * zoom;
        // подпись планеты — поверх, на тёмной плашке
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        int tw = font.width(p.name);
        g.fill((int) cx - tw / 2 - 3, (int) cy - 5, (int) cx + tw / 2 + 3, (int) cy + 6, 0xA0000000);
        g.drawCenteredString(font, p.name, (int) cx, (int) cy - 4, 0xFFFFE6A6);
        g.pose().popPose();
        for (PNode n : p.nodes) {
            float x = sx(n.x), y = sy(n.y);
            if (x < -30 || y < -30 || x > width + 30 || y > height + 30) continue;
            boolean un = ClientTree.UNLOCKED.contains(n.item);
            boolean av = !un && available(n);
            boolean afford = ClientTree.points >= n.cost;
            int ringCol = un ? 0xFFE8B84A : av ? (afford ? 0xFFD98A5A : 0xFFB0545E) : 0xFF3A4260;
            disc(g, x, y, nr + 2, ringCol);
            disc(g, x, y, nr, 0xFF141B2E);
            if (av && afford) ring(g, x, y, nr + 3 + Mth.sin(t * 0.2f), 0x80FFD27A);
            if (!n.stack.isEmpty()) {
                float s = Math.max(0.5f, zoom);
                g.pose().pushPose();
                g.pose().translate(x - 8 * s, y - 8 * s, 0);
                g.pose().scale(s, s, 1);
                g.renderItem(n.stack, 0, 0);
                g.pose().popPose();
                if (!un && !av) g.fill((int) (x - nr), (int) (y - nr), (int) (x + nr), (int) (y + nr), 0x00000000);
            }
            if (zoom >= 0.55f) {
                float ls = Math.min(1f, zoom);
                g.pose().pushPose();
                g.pose().translate(x + nr * 0.5f, y + nr * 0.3f, 200);
                g.pose().scale(ls, ls, 1);
                if (un || av) g.drawString(font, String.valueOf(n.cost), 0, 0, un ? 0xFFFFD27A : 0xFFE88A8A, true);
                else lock(g, 0, 0);
                g.pose().popPose();
            }
            if (dist(mx, my, x, y) < nr + 2) tip = nodeTip(n, un, av, afford);
        }
        return tip;
    }

    private boolean available(PNode n) {
        if (n.parents.isEmpty()) return true;
        for (String p : n.parents) if (ClientTree.UNLOCKED.contains(p)) return true;
        return false;
    }

    private List<Component> nodeTip(PNode n, boolean un, boolean av, boolean afford) {
        List<Component> l = new ArrayList<>();
        l.add(n.stack.isEmpty() ? Component.literal(n.item) : n.stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
        String cat = switch (n.cat) { case 'c' -> "complex"; case 'm' -> "medium"; default -> "light"; };
        ChatFormatting cc = switch (n.cat) { case 'c' -> ChatFormatting.RED; case 'm' -> ChatFormatting.YELLOW; default -> ChatFormatting.GREEN; };
        l.add(Component.translatable("zalrpakraft.star.cat." + cat, n.cost).withStyle(cc));
        l.add(Component.translatable("zalrpakraft.star.cost", n.cost, n.exp).withStyle(ChatFormatting.WHITE));
        if (un) l.add(Component.translatable("zalrpakraft.star.unlocked").withStyle(ChatFormatting.GREEN));
        else if (!av) l.add(Component.translatable("zalrpakraft.star.need_parent").withStyle(ChatFormatting.GRAY));
        else if (!afford) l.add(Component.translatable("zalrpakraft.star.no_points").withStyle(ChatFormatting.RED));
        else l.add(Component.translatable("zalrpakraft.star.click").withStyle(ChatFormatting.GOLD));
        return l;
    }

    // ==================== ВВОД ====================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 1) { back(); return true; }
        if (button != 0) return false;
        float t = ticks;
        switch (level) {
            case GALAXY -> {
                for (Sys s : systems) {
                    float r = Math.max(3, Math.min((8 + (float) Math.sqrt(s.total) * 1.3f) * zoom, 40 * zoom + 4));
                    if (dist(mx, my, sx(s.x), sy(s.y)) < r + 4) {
                        pendingSystem = s.id;
                        PacketDistributor.sendToServer(new SimplePayload("tree_system:" + s.id));
                        click();
                        return true;
                    }
                }
            }
            case SYSTEM -> {
                if (dist(mx, my, sx(0), sy(0)) < 30 * zoom + 2) { enterPlanet(0); return true; }
                for (int i = 1; i < planets.size(); i++) {
                    float[] pos = planetPos(i, t);
                    float pr = (7 + Math.min(6, planets.get(i).nodes.size() / 8f)) * zoom + 2;
                    if (dist(mx, my, sx(pos[0]), sy(pos[1])) < pr + 4) { enterPlanet(i); return true; }
                }
            }
            case PLANET -> {
                float nr = 15 * zoom;
                for (PNode n : planets.get(planetIdx).nodes) {
                    if (dist(mx, my, sx(n.x), sy(n.y)) < nr + 2) {
                        if (!ClientTree.UNLOCKED.contains(n.item) && available(n) && ClientTree.points >= n.cost) {
                            PacketDistributor.sendToServer(new SimplePayload("unlock:" + n.item));
                        } else click();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void enterPlanet(int i) {
        if (i >= planets.size()) return;
        planetIdx = i;
        level = Level.PLANET;
        camX = camY = 0;
        tZoom = planetFit();
        zoom = tZoom * 0.7f;
        click();
    }

    /** Самотест: открыть систему по id (данные придут с сервера). */
    public void requestSystem(String id) {
        pendingSystem = id;
        PacketDistributor.sendToServer(new SimplePayload("tree_system:" + id));
    }

    public String biggestSystem() {
        return systems.isEmpty() ? null : systems.stream().max(Comparator.comparingInt(Sys::total)).get().id;
    }

    private void back() {
        switch (level) {
            case PLANET -> { level = Level.SYSTEM; camX = camY = 0; tZoom = systemFit(); zoom = tZoom * 1.3f; }
            case SYSTEM -> { level = Level.GALAXY; fitGalaxy(); zoom = tZoom * 1.4f; }
            default -> onClose();
        }
        click();
    }

    private void click() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, 1.4F, 0.6F));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ZClientEvents.MAP_KEY.matches(key, scan)) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_C) {
            if (level == Level.GALAXY && key != GLFW.GLFW_KEY_ESCAPE) return true;
            back();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0) return false;
        camX -= (float) (dx / zoom);
        camY -= (float) (dy / zoom);
        float lim = level == Level.GALAXY ? galaxyR + 400 : 1500;
        camX = Mth.clamp(camX, -lim, lim);
        camY = Mth.clamp(camY, -lim, lim);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        tZoom = Mth.clamp(tZoom * (sy > 0 ? 1.15f : 1f / 1.15f), 0.08f, 3f);
        return true;
    }

    // ==================== ПРИМИТИВЫ ====================
    private static float dist(double mx, double my, float x, float y) {
        return (float) Math.hypot(mx - x, my - y);
    }

    static void disc(GuiGraphics g, float cx, float cy, float r, int col) {
        int ri = Math.max(1, (int) r);
        for (int dy = -ri; dy <= ri; dy++) {
            int w = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill((int) cx - w, (int) cy + dy, (int) cx + w + 1, (int) cy + dy + 1, col);
        }
    }

    /** Солнце/планета: тёмный край, основной цвет, светлый блик. */
    static void sun(GuiGraphics g, float cx, float cy, float r, int rgb) {
        disc(g, cx, cy, r + 1, 0xFF0A0D1C);
        disc(g, cx, cy, r, 0xFF000000 | (rgb & 0xFFFFFF));
        disc(g, cx - r * 0.25f, cy - r * 0.25f, r * 0.55f, 0x40FFFFFF);
        disc(g, cx - r * 0.35f, cy - r * 0.35f, r * 0.22f, 0x30FFFFFF);
    }

    static void ring(GuiGraphics g, float cx, float cy, float r, int col) {
        int steps = Math.max(16, (int) (r * 5));
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = (int) (cx + Math.cos(a) * r), y = (int) (cy + Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    static void dottedCircle(GuiGraphics g, float cx, float cy, float r, int col) {
        int steps = Math.max(24, (int) (r * 1.2f));
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = (int) (cx + Math.cos(a) * r), y = (int) (cy + Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    static void diamond(GuiGraphics g, float cx, float cy, float r, int col) {
        int ri = Math.max(1, (int) r);
        for (int dy = -ri; dy <= ri; dy++) {
            int w = ri - Math.abs(dy);
            g.fill((int) cx - w, (int) cy + dy, (int) cx + w + 1, (int) cy + dy + 1, col);
        }
    }

    static void line(GuiGraphics g, float x1, float y1, float x2, float y2, int col, int w) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        for (float d = 0; d <= len; d += 1f) {
            int x = (int) (x1 + dx * d / len), y = (int) (y1 + dy * d / len);
            g.fill(x, y, x + w, y + w, col);
        }
    }

    private static void lock(GuiGraphics g, int x, int y) {
        g.fill(x, y + 3, x + 7, y + 9, 0xFFE6E6EE);
        g.fill(x + 1, y, x + 6, y + 1, 0xFFE6E6EE);
        g.fill(x + 1, y, x + 2, y + 4, 0xFFE6E6EE);
        g.fill(x + 5, y, x + 6, y + 4, 0xFFE6E6EE);
        g.fill(x + 3, y + 5, x + 4, y + 7, 0xFF3A4260);
    }
}
