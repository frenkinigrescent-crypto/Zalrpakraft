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
    private record Sys(String id, String name, int color, float x, float y, int total, int done, int[] planets, boolean own, ItemStack book, ItemStack icon) {}
    private record Emblem(float x, float y, float size, String mod, ItemStack stack) {}
    private record PNode(String item, ItemStack stack, float x, float y, int cost, int exp, char cat, List<String> parents,
                         List<String> soft, List<String> external) {}
    private record Step(ItemStack stack, String text) {}
    private record Pl(String id, String name, int color, List<PNode> nodes, int req) {}

    private final List<Sys> systems = new ArrayList<>();
    private final Map<String, ProgressionMap.Node> mapNodes = new LinkedHashMap<>();
    private final Set<String> studied = new HashSet<>();
    private String sysId, sysName;
    private int sysColor;
    private final List<Pl> planets = new ArrayList<>();
    // система со своей прогрессией (Thaumaturge, Unbound…)
    private boolean own;
    private ItemStack ownBook = ItemStack.EMPTY;
    private String ownNote = "";
    private final List<Step> ownSteps = new ArrayList<>();
    private final List<String> ownMods = new ArrayList<>();
    // что нарисовать поверх всего
    private final List<Emblem> emblems = new ArrayList<>();
    private ItemStack sysIcon = ItemStack.EMPTY;
    private ItemStack recipeFor = ItemStack.EMPTY;

    // ---- вид ----
    private Level level = Level.GALAXY;
    private int planetIdx;
    private float camX, camY, zoom = 0.6f, tZoom = 1f;
    private float galaxyR = 300;
    private int ticks;
    private final List<float[]> stars = new ArrayList<>();
    private String pendingSystem;
    // ---------- поиск ----------
    private record Hit(String item, ItemStack stack, Component name, String sys, int planet, String label) {}
    private record Entry(String item, ItemStack stack, String nameLow, String sys, int planet, String label) {}
    private static final List<Entry> INDEX = new ArrayList<>();
    private static int indexVersion = -1;
    private static String searchText = "";
    private net.minecraft.client.gui.components.EditBox search;
    private final List<Hit> hits = new ArrayList<>();
    private final Set<String> matchItems = new HashSet<>();
    private final Set<String> matchSystems = new HashSet<>();
    private String focusItem;
    private int focusUntil;
    private String pendingFocusSys, pendingFocusItem;
    private int pendingFocusPlanet = -2;

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
            boolean ow = s.has("own") && s.get("own").getAsBoolean();
            Sys sys = new Sys(s.get("id").getAsString(), s.get("name").getAsString(), s.get("color").getAsInt(),
                    s.get("x").getAsFloat(), s.get("y").getAsFloat(), s.get("total").getAsInt(), s.get("done").getAsInt(), pc,
                    ow, ow && s.has("book") ? stackOf(s.get("book").getAsString()) : ItemStack.EMPTY,
                    s.has("icon") ? stackOf(s.get("icon").getAsString()) : ItemStack.EMPTY);
            systems.add(sys);
            galaxyR = Math.max(galaxyR, (float) Math.hypot(sys.x, sys.y) + 60);
        }
        mapNodes.clear();
        try {
            mapNodes.putAll(ProgressionMap.parse(mapJson));
        } catch (Exception ignored) {
        }
        RecipeView.clearCache();
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
        sysIcon = o.has("icon") ? stackOf(o.get("icon").getAsString()) : ItemStack.EMPTY;
        planets.clear();
        for (JsonElement pe : o.getAsJsonArray("planets")) {
            JsonObject p = pe.getAsJsonObject();
            List<PNode> nodes = new ArrayList<>();
            for (JsonElement ne : p.getAsJsonArray("nodes")) {
                JsonObject n = ne.getAsJsonObject();
                String item = n.get("i").getAsString();
                nodes.add(new PNode(item, stackOf(item), n.get("x").getAsFloat(), n.get("y").getAsFloat(), n.get("c").getAsInt(), n.get("e").getAsInt(),
                        n.get("k").getAsString().charAt(0), strings(n, "p"), strings(n, "s"), strings(n, "ex")));
            }
            planets.add(new Pl(p.get("id").getAsString(), p.get("name").getAsString(), p.get("color").getAsInt(), nodes,
                    p.has("req") ? p.get("req").getAsInt() : 0));
        }
        own = o.has("own");
        ownSteps.clear();
        ownMods.clear();
        ownBook = ItemStack.EMPTY;
        ownNote = "";
        if (own) {
            JsonObject ow = o.getAsJsonObject("own");
            ownBook = stackOf(ow.get("book").getAsString());
            ownNote = ow.has("note") ? ow.get("note").getAsString() : "";
            if (ow.has("steps")) for (JsonElement e : ow.getAsJsonArray("steps")) {
                JsonObject st = e.getAsJsonObject();
                ownSteps.add(new Step(stackOf(st.get("item").getAsString()), st.get("text").getAsString()));
            }
            if (ow.has("mods")) for (JsonElement e : ow.getAsJsonArray("mods")) ownMods.add(modName(e.getAsString()));
        }
        if (!same || level == Level.GALAXY) {
            level = Level.SYSTEM;
            camX = camY = 0;
            zoom = 0.5f;
            tZoom = systemFit();
        }
        pendingSystem = null;
        if (pendingFocusSys != null && pendingFocusSys.equals(sysId)) {
            if (pendingFocusPlanet >= 0) {
                enterPlanet(pendingFocusPlanet);
                focusNode(pendingFocusItem);
            }
            pendingFocusSys = null;
        }
    }

    // ==================== ПОИСК ====================
    private static void rebuildIndex() {
        if (indexVersion == ClientTree.locVersion) return;
        INDEX.clear();
        for (Map.Entry<String, String> e : ClientTree.LOC.entrySet()) {
            ItemStack st = stackOf(e.getKey());
            if (st.isEmpty()) continue;
            String[] p = e.getValue().split("\\|", 3);
            if (p.length < 3) continue;
            int pl;
            try { pl = Integer.parseInt(p[1]); } catch (NumberFormatException ex) { continue; }
            INDEX.add(new Entry(e.getKey(), st, st.getHoverName().getString().toLowerCase(Locale.ROOT), p[0], pl, p[2]));
        }
        indexVersion = ClientTree.locVersion;
    }

    private void updateSearch(String q) {
        searchText = q;
        hits.clear();
        matchItems.clear();
        matchSystems.clear();
        String s = q.trim().toLowerCase(Locale.ROOT);
        if (s.length() < 2) return;
        rebuildIndex();
        List<Entry> found = new ArrayList<>();
        for (Entry e : INDEX) {
            if (e.nameLow().contains(s) || e.item().contains(s)) {
                found.add(e);
                matchItems.add(e.item());
                matchSystems.add(e.sys());
            }
        }
        found.sort(Comparator.<Entry>comparingInt(e -> e.nameLow().startsWith(s) ? 0 : 1)
                .thenComparingInt(e -> e.nameLow().length()).thenComparing(Entry::nameLow));
        for (int i = 0; i < Math.min(8, found.size()); i++) {
            Entry e = found.get(i);
            hits.add(new Hit(e.item(), e.stack(), e.stack().getHoverName(), e.sys(), e.planet(), e.label()));
        }
    }

    /** Самотест поиска. */
    public void testSearch(String q) {
        setFocused(search);
        search.setFocused(true);
        search.setValue(q);
    }

    public void testGoFirst() {
        if (!hits.isEmpty()) goTo(hits.get(0));
    }

    private boolean resultsShown() {
        return search != null && search.isFocused() && searchText.trim().length() >= 2;
    }

    private int[] resultsBox() {
        int w = Math.max(search.getWidth() + 60, 230);
        int x = Math.min(search.getX(), width - w - 4);
        int rows = Math.max(1, hits.size());
        return new int[]{x, search.getY() + 19, w, rows * 20 + 4};
    }

    private void goTo(Hit h) {
        search.setFocused(false);
        setFocused(null);
        if (h.planet() < 0) { // солнце своего пути
            requestSystem(h.sys());
            return;
        }
        if (level != Level.GALAXY && h.sys().equals(sysId) && h.planet() < planets.size()) {
            enterPlanet(h.planet());
            focusNode(h.item());
            return;
        }
        pendingFocusSys = h.sys();
        pendingFocusPlanet = h.planet();
        pendingFocusItem = h.item();
        requestSystem(h.sys());
    }

    private void focusNode(String item) {
        if (planetIdx >= planets.size()) return;
        for (PNode n : planets.get(planetIdx).nodes) {
            if (n.item.equals(item)) {
                camX = n.x;
                camY = n.y;
                tZoom = Math.max(tZoom, 1.3f);
                focusItem = item;
                focusUntil = ticks + 120;
                return;
            }
        }
    }

    private void renderSearchMarks(GuiGraphics g, float t) {
        if (level == Level.PLANET && planetIdx < planets.size()) {
            g.drawManaged(() -> {
                for (PNode n : planets.get(planetIdx).nodes) {
                    float x = sx(n.x), y = sy(n.y);
                    if (x < -20 || y < -20 || x > width + 20 || y > height + 20) continue;
                    if (n.item.equals(focusItem) && ticks < focusUntil) {
                        float pulse = 13 * zoom + 4 + Mth.sin(t * 0.3f) * 2.5f;
                        ring(g, x, y, pulse, 0xFFFFE07A);
                        ring(g, x, y, pulse + 1.5f, 0x90FFE07A);
                    } else if (matchItems.contains(n.item)) {
                        ring(g, x, y, 12 * zoom + 3, 0xC0FFD27A);
                    }
                }
            });
        } else if (level == Level.GALAXY && !matchSystems.isEmpty()) {
            g.drawManaged(() -> {
                for (Sys s : systems) {
                    if (!matchSystems.contains(s.id)) continue;
                    dottedCircle(g, sx(s.x), sy(s.y), sysRadius(s) + 12 + Mth.sin(t * 0.15f) * 2, 0xE0FFD27A);
                }
            });
        }
    }

    private void renderResults(GuiGraphics g, int mx, int my) {
        if (!resultsShown()) return;
        int[] b = resultsBox();
        g.pose().pushPose();
        g.pose().translate(0, 0, 480);
        g.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, 0xFF6A5AA8);
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0xF20C1022);
        if (hits.isEmpty()) {
            g.drawString(font, Component.translatable("zalrpakraft.map.search.none"), b[0] + 6, b[1] + 8, 0xFF8C8399, false);
        }
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            int y = b[1] + 2 + i * 20;
            boolean hover = mx >= b[0] && mx < b[0] + b[2] && my >= y && my < y + 20;
            if (hover) g.fill(b[0] + 1, y, b[0] + b[2] - 1, y + 20, 0x40B79CFF);
            g.renderItem(h.stack(), b[0] + 3, y + 2);
            boolean open = ClientTree.UNLOCKED.contains(h.item()) || !ClientTree.GATED.containsKey(h.item());
            String nm = font.plainSubstrByWidth(h.name().getString(), b[2] - 40);
            g.drawString(font, nm, b[0] + 23, y + 2, open ? 0xFF9BE07A : 0xFFE9DDFF, false);
            String lb = font.plainSubstrByWidth((h.planet() < 0 ? "☀ " : "") + h.label(), b[2] - 40);
            g.drawString(font, lb, b[0] + 23, y + 11, 0xFF7A7094, false);
        }
        g.pose().popPose();
    }

    private static ItemStack stackOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl) ? new ItemStack(BuiltInRegistries.ITEM.get(rl)) : ItemStack.EMPTY;
    }

    private static String modName(String ns) {
        return net.neoforged.fml.ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
    }

    private static List<String> strings(JsonObject o, String key) {
        if (!o.has(key)) return List.of();
        List<String> l = new ArrayList<>();
        for (JsonElement x : o.getAsJsonArray(key)) l.add(x.getAsString());
        return l;
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
        int sw = Mth.clamp(width / 4, 120, 180);
        search = new net.minecraft.client.gui.components.EditBox(font, width - sw - 8, 6, sw, 16, Component.translatable("zalrpakraft.map.search"));
        search.setHint(Component.translatable("zalrpakraft.map.search.hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(48);
        search.setValue(searchText);
        search.setResponder(this::updateSearch);
        addRenderableWidget(search);
        updateSearch(searchText);
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
        g.drawManaged(() -> skyInner(g, pt));
    }

    private void skyInner(GuiGraphics g, float pt) {
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
    /** Самотест: среднее время отрисовки карты, мс. */
    public static double avgRenderMs;
    private static long frames, nanos;

    public static void resetTiming() {
        frames = 0;
        nanos = 0;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long t0 = System.nanoTime();
        renderMap(g, mx, my, pt);
        nanos += System.nanoTime() - t0;
        frames++;
        avgRenderMs = nanos / 1e6 / frames;
    }

    /** Самотест: «навести мышь» на узел и «зажать Shift» без настоящего ввода. */
    public static String testHover;
    public static boolean testShift;

    public String pickTestNode() {
        if (level != Level.PLANET || planetIdx >= planets.size()) return null;
        PNode best = null;
        for (PNode n : planets.get(planetIdx).nodes) {
            int score = n.parents.size() * 10 + n.external.size() * 5 + n.soft.size();
            int bs = best == null ? -1 : best.parents.size() * 10 + best.external.size() * 5 + best.soft.size();
            if (score > bs && Math.abs(n.x) < 300 && Math.abs(n.y) < 200) best = n;
        }
        return best == null ? null : best.item;
    }

    private static boolean shift() {
        return testShift || Screen.hasShiftDown();
    }

    private void renderMap(GuiGraphics g, int rawMx, int rawMy, float pt) {
        zoom = Mth.lerp(0.2f, zoom, tZoom);
        int hx = rawMx, hy = rawMy;
        if (testHover != null && level == Level.PLANET && planetIdx < planets.size()) {
            for (PNode n : planets.get(planetIdx).nodes) if (n.item.equals(testHover)) { hx = (int) sx(n.x); hy = (int) sy(n.y); }
        }
        final int mx = hx, my = hy;
        sky(g, pt);
        float t = ticks + pt;
        @SuppressWarnings("unchecked")
        List<Component>[] box = new List[1];
        recipeFor = ItemStack.EMPTY;
        switch (level) {
            case GALAXY -> {
                emblems.clear();
                g.drawManaged(() -> box[0] = renderGalaxy(g, mx, my, t));
                for (Emblem e : emblems) drawEmblem(g, e);
            }
            case SYSTEM -> {
                if (own) box[0] = renderOwn(g, mx, my, t);
                else {
                    emblems.clear();
                    g.drawManaged(() -> box[0] = renderSystem(g, mx, my, t));
                    for (Emblem e : emblems) drawEmblem(g, e);
                    if (!planets.isEmpty()) { // подпись солнца — над ним, под названием системы, чтобы не закрывать значок
                        float sr = 30 * zoom + 2;
                        g.drawCenteredString(font, planets.get(0).name, (int) sx(0), (int) (sy(0) - sr - 11), 0xFFB9A9D9);
                    }
                }
            }
            case PLANET -> box[0] = renderPlanet(g, mx, my, t);
        }
        List<Component> tip = box[0];
        renderSearchMarks(g, t);
        renderHud(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        super.render(g, mx, my, pt);
        g.pose().popPose();
        renderResults(g, mx, my);
        if (resultsShown()) tip = null;
        if (!recipeFor.isEmpty()) {
            RecipeView.Shown rv = RecipeView.find(recipeFor);
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            if (rv == null) {
                g.renderComponentTooltip(font, List.of(recipeFor.getHoverName(),
                        Component.translatable("zalrpakraft.recipe.none").withStyle(ChatFormatting.GRAY)), mx, my);
            } else {
                int[] sz = RecipeView.size(font, rv);
                int x = Math.min(mx + 12, width - sz[0] - 4), y = Math.min(my + 8, height - sz[1] - 4);
                RecipeView.draw(g, font, rv, x, y, -1, -1, ticks, 0xB79CFF);
            }
            g.pose().popPose();
        } else if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    private void renderHud(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.drawManaged(() -> {
            g.fillGradient(0, 0, width, 30, 0xE0080C1E, 0x00080C1E);
            g.fillGradient(0, height - 30, width, height, 0x00080C1E, 0xE0080C1E);
            hudContent(g);
        });
        g.pose().popPose();
    }

    private void hudContent(GuiGraphics g) {
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
            float r = sysRadius(s);
            for (int i = 4; i >= 1; i--) disc(g, x, y, r * (1 + i * 0.35f), ((int) (14 / (float) i * 1.4f) << 24) | s.color);
            glassSun(g, x, y, r, s.color);
            if (s.own) {
                // своё солнце: двойной венец
                ring(g, x, y, r + 3 + Mth.sin(t * 0.08f), 0xC0000000 | s.color);
                dottedCircle(g, x, y, r + 8, 0x90000000 | s.color);
            }
            if (r > 5) emblems.add(new Emblem(x, y, r * 1.25f, s.own ? null : s.id, s.own ? s.book : s.icon));
            // крошечные планеты
            for (int i = 0; i < s.planets.length - 1; i++) {
                double a = t * 0.01 * (1 + i * 0.3) + i * 2.1;
                float pr = r + (6 + i * 4) * zoom;
                disc(g, x + (float) Math.cos(a) * pr, y + (float) Math.sin(a) * pr, Math.max(1, 1.6f * zoom + 0.6f), 0xFF000000 | s.planets[i + 1]);
            }
            if (zoom > 0.2f) {
                g.drawCenteredString(font, s.name, (int) x, (int) (y + r + (s.own ? 10 : 4)), 0xFFE6DDF8);
                if (zoom > 0.35f) {
                    if (s.own) g.drawCenteredString(font, Component.translatable("zalrpakraft.map.own.tag"), (int) x, (int) (y + r + 20), 0xFF9A8CC0);
                    else g.drawCenteredString(font, s.done + "/" + s.total, (int) x, (int) (y + r + 14), 0xFF8C8399);
                }
            }
            if (dist(mx, my, x, y) < r + 4) {
                tip = s.own ? List.of(Component.literal(s.name).withColor(s.color),
                        Component.translatable("zalrpakraft.map.own.tip").withStyle(ChatFormatting.GRAY),
                        Component.translatable("zalrpakraft.map.own.free").withStyle(ChatFormatting.GREEN),
                        Component.translatable("zalrpakraft.map.own.open").withStyle(ChatFormatting.YELLOW))
                        : List.of(Component.literal(s.name).withColor(s.color),
                        Component.translatable("zalrpakraft.map.system.progress", s.done, s.total).withStyle(ChatFormatting.GRAY),
                        Component.translatable("zalrpakraft.map.system.open").withStyle(ChatFormatting.YELLOW));
            }
        }
        return tip;
    }

    private float sysRadius(Sys s) {
        float r = (s.own ? 16 : 8 + (float) Math.sqrt(s.total) * 1.3f) * zoom;
        return Math.max(3, Math.min(r, 40 * zoom + 4));
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
        glassSun(g, cx, cy, sr, planets.get(0).color);
        emblems.add(new Emblem(cx, cy, sr * 1.3f, sysId, sysIcon));
        g.drawCenteredString(font, sysName, (int) cx, (int) (cy - sr - 22), 0xFFFFE6A6);
        if (dist(mx, my, cx, cy) < sr) tip = planetTip(0);
        // планеты
        for (int i = 1; i < planets.size(); i++) {
            Pl p = planets.get(i);
            float[] pos = planetPos(i, t);
            float x = sx(pos[0]), y = sy(pos[1]);
            float pr = (7 + Math.min(6, p.nodes.size() / 8f)) * zoom + 2;
            boolean lockedP = planetLocked(i);
            sun(g, x, y, pr, lockedP ? 0x3A4260 : p.color);
            if (lockedP) lock(g, (int) (x - 3), (int) (y - 4));
            g.drawCenteredString(font, p.name, (int) x, (int) (y + pr + 4), lockedP ? 0xFF6E6680 : 0xFFE6DDF8);
            if (dist(mx, my, x, y) < pr + 4) tip = planetTip(i);
        }
        return tip;
    }

    // ==================== СВОЙ ПУТЬ ====================
    /** Система мода со своей книгой: солнце с книгой и карточка «как начать» с рецептом. */
    private List<Component> renderOwn(GuiGraphics g, int mx, int my, float t) {
        List<Component> tip = null;
        int cardW = Mth.clamp(width / 2 - 24, 180, 300);
        float cx = Math.max(70, (width - cardW - 24) / 2f), cy = height / 2f + 4;
        float sr = 36;
        int col = sysColor & 0xFFFFFF;
        g.drawManaged(() -> {
            for (int i = 5; i >= 1; i--) disc(g, cx, cy, sr * (1 + i * 0.28f), ((int) (26 / (float) i) << 24) | col);
            glassSun(g, cx, cy, sr, col);
            ring(g, cx, cy, sr + 6 + Mth.sin(t * 0.08f) * 1.5f, 0xC0000000 | col);
            dottedCircle(g, cx, cy, sr + 16, 0x80000000 | col);
            dottedCircle(g, cx, cy, sr + 30, 0x50000000 | col);
        });
        if (!ownBook.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(cx - 16, cy - 16, 0);
            g.pose().scale(2, 2, 1);
            g.renderItem(ownBook, 0, 0);
            g.pose().popPose();
        }
        g.drawCenteredString(font, sysName, (int) cx, (int) (cy - sr - 22), 0xFFFFE6A6);
        g.drawCenteredString(font, Component.translatable("zalrpakraft.map.own.tag"), (int) cx, (int) (cy + sr + 36), 0xFF9A8CC0);
        g.drawCenteredString(font, Component.translatable("zalrpakraft.map.own.free"), (int) cx, (int) (cy + sr + 48), 0xFF7FD36B);
        if (dist(mx, my, cx, cy) < sr && !ownBook.isEmpty()) {
            tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, ownBook));
            tip.add(Component.translatable("zalrpakraft.map.own.tip").withStyle(ChatFormatting.GRAY));
        }
        // карточка
        int x0 = width - cardW - 16, y0 = 34;
        int h = ownCard(g, x0, y0, cardW, mx, my, false, null);
        g.fill(x0 - 7, y0 - 7, x0 + cardW + 7, y0 + h + 5, 0xFF000000 | col);
        g.fill(x0 - 6, y0 - 6, x0 + cardW + 6, y0 + h + 4, 0xF00C1022);
        List<List<Component>> hov = new ArrayList<>();
        ownCard(g, x0, y0, cardW, mx, my, true, hov);
        if (!hov.isEmpty()) tip = hov.get(0);
        return tip;
    }

    /** Раскладка карточки; draw=false — только измерить высоту. */
    private int ownCard(GuiGraphics g, int x0, int y0, int w, int mx, int my, boolean draw, List<List<Component>> hov) {
        int y = y0;
        if (draw) g.drawString(font, Component.translatable("zalrpakraft.map.own.book",
                ownBook.isEmpty() ? Component.literal("?") : ownBook.getHoverName()), x0, y, 0xFFFFD27A, false);
        y += 13;
        if (!ownNote.isEmpty()) {
            for (var line : font.split(Component.literal(ownNote), w)) {
                if (draw) g.drawString(font, line, x0, y, 0xFFCFC6E6, false);
                y += 10;
            }
            y += 4;
        }
        if (draw) g.drawString(font, Component.translatable("zalrpakraft.map.own.how"), x0, y, 0xFFB79CFF, false);
        y += 12;
        int num = 1;
        for (Step st : ownSteps) {
            int top = y;
            var lines = font.split(Component.literal(st.text()), w - 34);
            if (draw) {
                g.drawString(font, num + ".", x0, y + 4, 0xFF8C8399, false);
                if (!st.stack().isEmpty()) {
                    g.fill(x0 + 10, y, x0 + 28, y + 18, 0xFF262B45);
                    g.renderItem(st.stack(), x0 + 11, y + 1);
                    if (mx >= x0 + 10 && my >= y && mx < x0 + 28 && my < y + 18) hov.add(Screen.getTooltipFromItem(minecraft, st.stack()));
                }
                int ty = y + (lines.size() == 1 ? 5 : 0);
                for (var line : lines) {
                    g.drawString(font, line, x0 + 32, ty, 0xFFE6DDF8, false);
                    ty += 10;
                }
            }
            y = Math.max(top + 20, top + lines.size() * 10) + 4;
            num++;
        }
        y += 2;
        if (draw) g.drawString(font, Component.translatable("zalrpakraft.map.own.recipe"), x0, y, 0xFFB79CFF, false);
        y += 12;
        RecipeView.Shown rv = RecipeView.find(ownBook);
        if (rv != null) {
            int[] sz = RecipeView.size(font, rv);
            if (draw) {
                ItemStack hs = RecipeView.draw(g, font, rv, x0, y, mx, my, ticks, sysColor);
                if (!hs.isEmpty()) hov.add(Screen.getTooltipFromItem(minecraft, hs));
            }
            y += sz[1] + 6;
        } else {
            for (var line : font.split(Component.translatable("zalrpakraft.map.own.no_recipe"), w)) {
                if (draw) g.drawString(font, line, x0, y, 0xFF8C8399, false);
                y += 10;
            }
            y += 4;
        }
        if (!ownMods.isEmpty()) {
            for (var line : font.split(Component.translatable("zalrpakraft.map.own.mods", String.join(", ", ownMods)), w)) {
                if (draw) g.drawString(font, line, x0, y, 0xFF6E6680, false);
                y += 10;
            }
        }
        return y - y0;
    }

    private List<Component> planetTip(int i) {
        Pl p = planets.get(i);
        int done = 0;
        for (PNode n : p.nodes) if (ClientTree.UNLOCKED.contains(n.item)) done++;
        List<Component> l = new ArrayList<>();
        l.add(Component.literal(p.name).withColor(p.color));
        l.add(Component.translatable("zalrpakraft.map.system.progress", done, p.nodes.size()).withStyle(ChatFormatting.GRAY));
        if (planetLocked(i)) l.add(Component.translatable("zalrpakraft.star.planet_locked_tip", sunUnlocked(), p.req).withStyle(ChatFormatting.RED));
        l.add(Component.translatable("zalrpakraft.map.planet.open").withStyle(ChatFormatting.YELLOW));
        return l;
    }

    private List<Component> renderPlanet(GuiGraphics g, int mx, int my, float t) {
        if (planetIdx >= planets.size()) return null;
        Pl p = planets.get(planetIdx);
        Map<String, PNode> byId = new HashMap<>();
        for (PNode n : p.nodes) byId.put(n.item, n);
        float cx = sx(0), cy = sy(0);
        float nr = 15 * zoom;
        boolean locked = planetLocked(planetIdx);
        // подсветка пути к звезде под курсором
        Set<String> path = new HashSet<>();
        for (PNode n : p.nodes) {
            if (dist(mx, my, sx(n.x), sy(n.y)) < nr + 2) {
                Deque<String> q = new ArrayDeque<>(List.of(n.item));
                while (!q.isEmpty() && path.size() < 200) {
                    String id = q.poll();
                    if (!path.add(id)) continue;
                    PNode pn = byId.get(id);
                    if (pn != null) { q.addAll(pn.parents); q.addAll(pn.soft); }
                }
                break;
            }
        }
        // 1. связи, планета и кружки узлов — одним пакетом
        g.drawManaged(() -> {
            for (PNode n : p.nodes) {
                boolean un = ClientTree.UNLOCKED.contains(n.item);
                boolean front = un || (!locked && available(n));
                float x = sx(n.x), y = sy(n.y);
                if (n.parents.isEmpty() && n.soft.isEmpty()) {
                    if (visible(cx, cy, x, y)) quadLine(g, cx, cy, x, y, un ? 2f : 1f,
                            un ? 0xFFE8B84A : path.contains(n.item) ? 0xFFB79CFF : front ? 0xC0505A80 : 0x502A3150);
                }
                // пунктир — путь по карте (не часть рецепта)
                for (String par : n.soft) {
                    PNode pn = byId.get(par);
                    if (pn == null) continue;
                    float px = sx(pn.x), py = sy(pn.y);
                    if (!visible(px, py, x, y)) continue;
                    boolean lit = un && ClientTree.UNLOCKED.contains(par);
                    boolean onPath = path.contains(n.item) && path.contains(par);
                    int col = lit ? 0xC0E8B84A : onPath ? 0xE0B79CFF : front ? 0x90505A80 : 0x352A3150;
                    dashLine(g, px, py, x, y, lit || onPath ? 1.5f : 1f, col);
                }
                for (String par : n.parents) {
                    PNode pn = byId.get(par);
                    if (pn == null) continue;
                    float px = sx(pn.x), py = sy(pn.y);
                    if (!visible(px, py, x, y)) continue;
                    boolean lit = un && ClientTree.UNLOCKED.contains(par);
                    boolean onPath = path.contains(n.item) && path.contains(par);
                    int col = lit ? 0xFFE8B84A : onPath ? 0xFFB79CFF : front ? 0xC0505A80 : 0x452A3150;
                    quadLine(g, px, py, x, y, lit || onPath ? 2f : 1f, col);
                }
            }
            float pr = 34 * zoom + 2;
            for (int i = 4; i >= 1; i--) disc(g, cx, cy, pr * (1 + i * 0.22f), ((int) (26 / (float) i) << 24) | (p.color & 0xFFFFFF));
            sun(g, cx, cy, pr, p.color);
            dottedCircle(g, cx, cy, pr + 4, 0xA0FFE08A);
            for (PNode n : p.nodes) {
                float x = sx(n.x), y = sy(n.y);
                if (offscreen(x, y, nr)) continue;
                boolean un = ClientTree.UNLOCKED.contains(n.item);
                boolean av = !un && !locked && available(n);
                boolean afford = ClientTree.points >= n.cost;
                int ringCol = un ? 0xFFE8B84A : av ? (afford ? 0xFFD98A5A : 0xFFB0545E) : 0xFF3A4260;
                disc(g, x, y, nr + 2, ringCol);
                disc(g, x, y, nr, 0xFF141B2E);
                if (av && afford) ring(g, x, y, nr + 3 + Mth.sin(t * 0.2f), 0x80FFD27A);
            }
        });
        // 2. иконки предметов
        float s = Math.max(0.5f, zoom);
        for (PNode n : p.nodes) {
            float x = sx(n.x), y = sy(n.y);
            if (n.stack.isEmpty() || offscreen(x, y, nr)) continue;
            g.pose().pushPose();
            g.pose().translate(x - 8 * s, y - 8 * s, 0);
            g.pose().scale(s, s, 1);
            g.renderItem(n.stack, 0, 0);
            g.pose().popPose();
        }
        // 3. цены, замки, подпись планеты, подсказка
        List<Component> tip = null;
        for (PNode n : p.nodes) {
            float x = sx(n.x), y = sy(n.y);
            if (offscreen(x, y, nr)) continue;
            if (dist(mx, my, x, y) < nr + 2) {
                boolean un = ClientTree.UNLOCKED.contains(n.item);
                boolean av = !un && !locked && available(n);
                if (shift() && !n.stack.isEmpty()) recipeFor = n.stack;
                else tip = nodeTip(n, un, av, ClientTree.points >= n.cost);
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.drawManaged(() -> {
            if (zoom >= 0.55f) {
                float ls = Math.min(1f, zoom);
                for (PNode n : p.nodes) {
                    float x = sx(n.x), y = sy(n.y);
                    if (offscreen(x, y, nr)) continue;
                    boolean un = ClientTree.UNLOCKED.contains(n.item);
                    boolean av = !un && !locked && available(n);
                    g.pose().pushPose();
                    g.pose().translate(x + nr * 0.5f, y + nr * 0.3f, 0);
                    g.pose().scale(ls, ls, 1);
                    if (un || av) g.drawString(font, String.valueOf(n.cost), 0, 0, un ? 0xFFFFD27A : 0xFFE88A8A, true);
                    else lock(g, 0, 0);
                    g.pose().popPose();
                }
            }
            int tw = font.width(p.name);
            g.fill((int) cx - tw / 2 - 3, (int) cy - 5, (int) cx + tw / 2 + 3, (int) cy + 6, 0xA0000000);
            g.drawCenteredString(font, p.name, (int) cx, (int) cy - 4, 0xFFFFE6A6);
            if (locked) {
                Component lc = Component.translatable("zalrpakraft.star.planet_locked", p.req);
                g.drawCenteredString(font, lc, (int) cx, (int) (cy + 34 * zoom + 12), 0xFFE88A8A);
            }
        });
        g.pose().popPose();
        return tip;
    }

    private boolean offscreen(float x, float y, float r) {
        return x < -r - 20 || y < -r - 20 || x > width + r + 20 || y > height + r + 20;
    }

    private boolean visible(float x1, float y1, float x2, float y2) {
        return !((x1 < 0 && x2 < 0) || (y1 < 0 && y2 < 0) || (x1 > width && x2 > width) || (y1 > height && y2 > height));
    }

    /** Пунктир из коротких четырёхугольников. */
    static void dashLine(GuiGraphics g, float x1, float y1, float x2, float y2, float w, int col) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        float dash = 5f, gap = 4f;
        int n = Math.min(80, (int) (len / (dash + gap)) + 1);
        for (int i = 0; i < n; i++) {
            float a = i * (dash + gap) / len, b = Math.min(1f, (i * (dash + gap) + dash) / len);
            if (a >= 1f) break;
            quadLine(g, x1 + dx * a, y1 + dy * a, x1 + dx * b, y1 + dy * b, w, col);
        }
    }

    /** Линия одним четырёхугольником (раньше — по пикселю на каждую точку). */
    static void quadLine(GuiGraphics g, float x1, float y1, float x2, float y2, float w, int col) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        float nx = -dy / len * w / 2f, ny = dx / len * w / 2f;
        org.joml.Matrix4f m = g.pose().last().pose();
        com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        vc.addVertex(m, x1 + nx, y1 + ny, 0).setColor(col);
        vc.addVertex(m, x1 - nx, y1 - ny, 0).setColor(col);
        vc.addVertex(m, x2 - nx, y2 - ny, 0).setColor(col);
        vc.addVertex(m, x2 + nx, y2 + ny, 0).setColor(col);
        vc.addVertex(m, x2 + nx, y2 + ny, 0).setColor(col);
        vc.addVertex(m, x2 - nx, y2 - ny, 0).setColor(col);
        vc.addVertex(m, x1 - nx, y1 - ny, 0).setColor(col);
        vc.addVertex(m, x1 + nx, y1 + ny, 0).setColor(col);
    }

    private int sunUnlocked() {
        int k = 0;
        if (!planets.isEmpty()) for (PNode n : planets.get(0).nodes) if (ClientTree.UNLOCKED.contains(n.item)) k++;
        return k;
    }

    private boolean planetLocked(int i) {
        return i > 0 && i < planets.size() && sunUnlocked() < planets.get(i).req;
    }

    /** Как на сервере: все части крафта открыты и хоть одна звезда по пунктиру, если он есть. */
    private boolean available(PNode n) {
        for (String p : n.parents) if (!ClientTree.UNLOCKED.contains(p)) return false;
        if (n.soft.isEmpty()) return true;
        for (String p : n.soft) if (ClientTree.UNLOCKED.contains(p)) return true;
        return false;
    }

    private List<Component> nodeTip(PNode n, boolean un, boolean av, boolean afford) {
        List<Component> l = new ArrayList<>();
        if (n.stack.isEmpty()) l.add(Component.literal(n.item));
        else {
            // настоящая подсказка предмета: через неё Create (Ponder) видит предмет и даёт «Зажми [W]»
            l.addAll(Screen.getTooltipFromItem(minecraft, n.stack));
        }
        String cat = switch (n.cat) { case 'c' -> "complex"; case 'm' -> "medium"; default -> "light"; };
        ChatFormatting cc = switch (n.cat) { case 'c' -> ChatFormatting.RED; case 'm' -> ChatFormatting.YELLOW; default -> ChatFormatting.GREEN; };
        l.add(Component.translatable("zalrpakraft.star.cat." + cat, n.cost).withStyle(cc));
        l.add(Component.translatable("zalrpakraft.star.cost", n.cost, n.exp).withStyle(ChatFormatting.WHITE));
        if (!n.external.isEmpty()) {
            l.add(Component.translatable("zalrpakraft.star.needs").withStyle(ChatFormatting.DARK_AQUA));
            for (String ex : n.external) {
                ItemStack es = stackOf(ex);
                boolean open = ClientTree.UNLOCKED.contains(ex) || !ClientTree.GATED.containsKey(ex);
                String where = ClientTree.GATED.get(ex);
                Component name = es.isEmpty() ? Component.literal(ex) : es.getHoverName();
                l.add(Component.literal(open ? " ✔ " : " ✖ ").append(name)
                        .append(where == null || open ? Component.empty() : Component.literal("  — " + where).withStyle(ChatFormatting.DARK_GRAY))
                        .withStyle(open ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
        boolean hardOk = true;
        for (String p : n.parents) if (!ClientTree.UNLOCKED.contains(p)) hardOk = false;
        if (un) l.add(Component.translatable("zalrpakraft.star.unlocked").withStyle(ChatFormatting.GREEN));
        else if (!av && !hardOk) l.add(Component.translatable("zalrpakraft.star.need_parent").withStyle(ChatFormatting.GRAY));
        else if (!av) l.add(Component.translatable("zalrpakraft.star.need_soft").withStyle(ChatFormatting.GRAY));
        else if (!afford) l.add(Component.translatable("zalrpakraft.star.no_points").withStyle(ChatFormatting.RED));
        else l.add(Component.translatable("zalrpakraft.star.click").withStyle(ChatFormatting.GOLD));
        l.add(Component.translatable("zalrpakraft.star.shift_recipe").withStyle(ChatFormatting.DARK_GRAY));
        return l;
    }

    // ==================== ВВОД ====================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (resultsShown()) {
            int[] b = resultsBox();
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                int i = (int) ((my - b[1] - 2) / 20);
                if (button == 0 && i >= 0 && i < hits.size()) { goTo(hits.get(i)); click(); }
                return true;
            }
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (search != null && search.isFocused()) { search.setFocused(false); setFocused(null); }
        if (button == 1) { back(); return true; }
        if (button != 0) return false;
        float t = ticks;
        switch (level) {
            case GALAXY -> {
                for (Sys s : systems) {
                    float r = sysRadius(s);
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
                        if (!ClientTree.UNLOCKED.contains(n.item) && !planetLocked(planetIdx) && available(n) && ClientTree.points >= n.cost) {
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

    public void enterBiggestPlanet() {
        int best = 0;
        for (int i = 1; i < planets.size(); i++) if (planets.get(i).nodes.size() > planets.get(best).nodes.size()) best = i;
        enterPlanet(best);
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
        if (search != null && search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { search.setFocused(false); setFocused(null); return true; }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !hits.isEmpty()) { goTo(hits.get(0)); click(); return true; }
            search.keyPressed(key, scan, mods);
            return true; // пока пишешь — карта не реагирует на буквы
        }
        if (search != null && (key == GLFW.GLFW_KEY_F && (mods & GLFW.GLFW_MOD_CONTROL) != 0 || key == GLFW.GLFW_KEY_SLASH)) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
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

    /** Полупрозрачное «стеклянное» солнце: сквозь него виден значок мода. */
    static void glassSun(GuiGraphics g, float cx, float cy, float r, int rgb) {
        int c = rgb & 0xFFFFFF;
        disc(g, cx, cy, r + 1, 0x700A0D1C);
        disc(g, cx, cy, r, 0x5A000000 | c);
        disc(g, cx, cy, r * 0.78f, 0x38000000 | c);
        disc(g, cx - r * 0.3f, cy - r * 0.3f, r * 0.42f, 0x22FFFFFF);
        disc(g, cx - r * 0.38f, cy - r * 0.38f, r * 0.16f, 0x30FFFFFF);
        ring(g, cx, cy, r, 0xD0000000 | c);
    }

    /** Значок внутри солнца: квадратный логотип мода, иначе значимый предмет. */
    private void drawEmblem(GuiGraphics g, Emblem e) {
        ModIcons.Logo logo = e.mod() == null ? null : ModIcons.get(e.mod());
        float size = e.size();
        if (logo != null) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.pose().pushPose();
            g.pose().translate(e.x() - size / 2f, e.y() - size / 2f, 0);
            g.pose().scale(size / logo.w(), size / logo.h(), 1);
            g.blit(logo.tex(), 0, 0, 0, 0, logo.w(), logo.h(), logo.w(), logo.h());
            g.pose().popPose();
            return;
        }
        if (e.stack().isEmpty()) return;
        float sc = size / 16f;
        g.pose().pushPose();
        g.pose().translate(e.x() - 8 * sc, e.y() - 8 * sc, 0);
        g.pose().scale(sc, sc, 1);
        g.renderItem(e.stack(), 0, 0);
        g.pose().popPose();
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

    private static void lock(GuiGraphics g, int x, int y) {
        g.fill(x, y + 3, x + 7, y + 9, 0xFFE6E6EE);
        g.fill(x + 1, y, x + 6, y + 1, 0xFFE6E6EE);
        g.fill(x + 1, y, x + 2, y + 4, 0xFFE6E6EE);
        g.fill(x + 5, y, x + 6, y + 4, 0xFFE6E6EE);
        g.fill(x + 3, y + 5, x + 4, y + 7, 0xFF3A4260);
    }
}
