package dev.zalrpakraft.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.data.JsonConfigFile;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.*;

/** Строит дерево из рецептов сборки. Вызывается на сервере при запуске и после /reload. */
public final class TreeBuilder {
    private static volatile Tree current = Tree.EMPTY;

    public static Tree tree() {
        return current;
    }

    private static final List<TagKey<Item>> DECOR_TAGS = List.of(ItemTags.STAIRS, ItemTags.SLABS, ItemTags.WALLS, ItemTags.FENCES,
            ItemTags.FENCE_GATES, ItemTags.DOORS, ItemTags.TRAPDOORS, ItemTags.BUTTONS, ItemTags.WOOL, ItemTags.WOOL_CARPETS,
            ItemTags.PLANKS, ItemTags.LOGS, ItemTags.LEAVES, ItemTags.SAPLINGS, ItemTags.SIGNS, ItemTags.HANGING_SIGNS,
            ItemTags.TERRACOTTA, ItemTags.CANDLES, ItemTags.BANNERS, ItemTags.BEDS, ItemTags.FLOWERS);
    private static final String[] DECOR_WORDS = {"_slab", "_stairs", "_wall", "_pillar", "bricks", "tiles", "polished_", "cut_",
            "_window", "_pane", "layered_", "_fence", "_door", "_trapdoor", "_carpet", "_tile", "smooth_", "chiseled_", "mossy_", "cracked_"};

    public static Tree build(MinecraftServer server) {
        long t0 = System.currentTimeMillis();
        JsonObject cfg = JsonParser.parseString(JsonConfigFile.readOrCreate("constellation.json")).getAsJsonObject();
        boolean testVanilla = !System.getProperty("zalrpakraft.selftest", "").isEmpty() || !System.getProperty("zalrpakraft.clienttest", "").isEmpty();
        Set<String> excluded = new HashSet<>();
        for (JsonElement e : cfg.getAsJsonArray("excluded_namespaces")) excluded.add(e.getAsString());
        if (testVanilla) excluded.remove("minecraft");
        boolean ungateDecor = !cfg.has("ungate_decor") || cfg.get("ungate_decor").getAsBoolean();
        JsonObject overrides = cfg.has("overrides") ? cfg.getAsJsonObject("overrides") : new JsonObject();
        JsonObject cats = cfg.getAsJsonObject("categories");
        JsonObject cx = cfg.getAsJsonObject("complexity");
        int medDepth = cx.get("medium_from_depth").getAsInt(), complexDepth = cx.get("complex_from_depth").getAsInt();

        HolderLookup.Provider reg = server.registryAccess();
        Collection<RecipeHolder<?>> recipes = server.getRecipeManager().getRecipes();

        // 1. Рецепты: что получается и из чего. Каждый слот рецепта — набор вариантов (тег = несколько предметов).
        Map<Item, List<Rec>> recipesOf = new HashMap<>();
        Set<Item> stonecut = new HashSet<>();
        for (RecipeHolder<?> h : recipes) {
            Recipe<?> r = h.value();
            ItemStack out;
            try {
                out = r.getResultItem(reg);
            } catch (Exception e) {
                continue;
            }
            if (out == null || out.isEmpty()) continue;
            Item item = out.getItem();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (excluded.contains(id.getNamespace())) continue;
            if (r.getType() == RecipeType.STONECUTTING) stonecut.add(item);
            List<Item[]> slots = new ArrayList<>();
            try {
                for (Ingredient in : r.getIngredients()) {
                    ItemStack[] opts = in.getItems();
                    if (opts.length == 0) continue;
                    LinkedHashSet<Item> set = new LinkedHashSet<>();
                    for (ItemStack st : opts) if (!st.isEmpty()) set.add(st.getItem());
                    if (set.isEmpty() || set.contains(item)) continue; // перекраска/улучшение «из самого себя» — не связь
                    slots.add(set.toArray(new Item[0]));
                }
            } catch (Exception ignored) {
            }
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
            recipesOf.computeIfAbsent(item, k -> new ArrayList<>())
                    .add(new Rec(h.id().toString(), typeId == null ? "" : typeId.toString(), out.getCount(), slots));
        }
        // переработка (инструмент → самородки) и распаковка (блок → 9 слитков) — не путь крафта
        for (Map.Entry<Item, List<Rec>> e : recipesOf.entrySet()) {
            for (Rec rc : e.getValue()) rc.reverse = isReverse(e.getKey(), rc, recipesOf);
        }

        // 2. Моды со своей прогрессией (и их аддоны) не закрываются
        JsonObject own = cfg.has("own_progression") ? cfg.getAsJsonObject("own_progression") : new JsonObject();
        Map<String, Integer> candCount = new HashMap<>();
        for (Item it : recipesOf.keySet()) candCount.merge(BuiltInRegistries.ITEM.getKey(it).getNamespace(), 1, Integer::sum);
        JsonObject merge = cfg.has("merge") ? cfg.getAsJsonObject("merge") : new JsonObject();
        Map<String, String> rootOf = new HashMap<>();
        for (String ns : candCount.keySet()) rootOf.put(ns, root(ns, candCount, merge, new HashSet<>()));
        Set<String> ownNs = new HashSet<>();
        for (String ns : candCount.keySet()) if (isOwn(ns, own) || isOwn(rootOf.get(ns), own) || dependsOnOwn(ns, own)) ownNs.add(ns);

        // 3. Отбор узлов
        Map<Item, String> gated = new LinkedHashMap<>(); // item -> namespace
        for (Item item : recipesOf.keySet()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (ownNs.contains(id.getNamespace())) continue;
            JsonObject ov = overrides.has(id.toString()) ? overrides.getAsJsonObject(id.toString()) : null;
            if (ov != null && ov.has("gated") && !ov.get("gated").getAsBoolean()) continue;
            if (ungateDecor && ov == null && isDecor(item, id, stonecut)) continue;
            gated.put(item, id.getNamespace());
        }

        // 4. Сложность = длина САМОЙ ПРОСТОЙ цепочки крафта (минимум по рецептам, тег — самый доступный вариант)
        Map<Item, Integer> depth = new HashMap<>();
        Map<Item, Rec> primary = new HashMap<>();
        computeDepth(gated, recipesOf, depth, primary);
        // родители — выбранные варианты основного рецепта
        Map<Item, List<Item>> parentsOf = new HashMap<>();
        for (Item it : gated.keySet()) parentsOf.put(it, chooseParents(it, primary.get(it), gated, depth));

        // 5. Системы: аддон присоединяется к самой крупной обязательной зависимости, у которой есть узлы
        Map<String, Integer> nsCount = new HashMap<>();
        for (String ns : gated.values()) nsCount.merge(ns, 1, Integer::sum);
        rootOf.clear();
        for (String ns : nsCount.keySet()) rootOf.put(ns, root(ns, nsCount, merge, new HashSet<>()));

        Tree tree = new Tree();
        tree.expPerPoint = cfg.get("exp_per_point").getAsInt();
        tree.startingPoints = cfg.get("starting_points").getAsInt();
        tree.pointsPerMeteor = cfg.has("points_per_meteor") ? cfg.get("points_per_meteor").getAsInt() : 3;
        tree.planetCompleteBonus = cfg.has("planet_complete_bonus") ? cfg.get("planet_complete_bonus").getAsInt() : 3;
        int ringStep = cfg.has("ring_cost_step") ? Math.max(1, cfg.get("ring_cost_step").getAsInt()) : 2;
        int maxCost = cfg.has("max_cost") ? cfg.get("max_cost").getAsInt() : 12;
        int maxRoots = cfg.has("max_entry_points") ? Math.max(1, cfg.get("max_entry_points").getAsInt()) : 3;
        int sunReq = cfg.has("planet_unlock_sun_nodes") ? cfg.get("planet_unlock_sun_nodes").getAsInt() : 5;
        JsonObject names = cfg.has("names") ? cfg.getAsJsonObject("names") : new JsonObject();
        JsonObject planetRules = cfg.has("planets") ? cfg.getAsJsonObject("planets") : new JsonObject();

        // группировка
        Map<String, List<Item>> bySystem = new LinkedHashMap<>();
        for (Map.Entry<Item, String> e : gated.entrySet()) bySystem.computeIfAbsent(rootOf.get(e.getValue()), k -> new ArrayList<>()).add(e.getKey());

        for (Map.Entry<String, List<Item>> se : bySystem.entrySet()) {
            String sys = se.getKey();
            Tree.SystemDef sd = new Tree.SystemDef();
            sd.id = sys;
            sd.name = names.has(sys) ? names.get(sys).getAsString() : displayName(sys);
            sd.color = hueColor(sys);
            JsonObject rules = planetRules.has(sys) ? planetRules.getAsJsonObject(sys) : null;
            Map<String, Tree.Planet> planets = new LinkedHashMap<>();
            Tree.Planet sun = new Tree.Planet();
            sun.id = "sun";
            sun.name = rules != null && rules.has("sun") ? rules.getAsJsonObject("sun").get("name").getAsString() : "Основы";
            sun.color = rules != null && rules.has("sun") ? parseColor(rules.getAsJsonObject("sun").get("color").getAsString()) : sd.color;
            planets.put("sun", sun);

            for (Item item : se.getValue()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                String ns = id.getNamespace();
                if (!sd.mods.contains(ns)) sd.mods.add(ns);
                JsonObject ov = overrides.has(id.toString()) ? overrides.getAsJsonObject(id.toString()) : null;
                String pid; String pname; int pcolor;
                if (ov != null && ov.has("planet")) {
                    pid = ov.get("planet").getAsString(); pname = pid; pcolor = sd.color;
                } else if (!ns.equals(sys)) {                       // аддон — своя планета
                    pid = "mod:" + ns; pname = displayName(ns); pcolor = hueColor(ns);
                } else if (rules != null && rules.has("rules")) {    // правила по ключевым словам
                    pid = "sun"; pname = sun.name; pcolor = sun.color;
                    for (JsonElement re : rules.getAsJsonArray("rules")) {
                        JsonObject ro = re.getAsJsonObject();
                        boolean hit = false;
                        for (JsonElement kw : ro.getAsJsonArray("keywords")) if (id.getPath().contains(kw.getAsString())) { hit = true; break; }
                        if (hit) { pid = ro.get("id").getAsString(); pname = ro.get("name").getAsString(); pcolor = parseColor(ro.get("color").getAsString()); break; }
                    }
                } else {                                             // общий случай — по типу предмета
                    String[] c = genericCategory(item);
                    pid = c[0]; pname = c[1]; pcolor = parseColor(c[2]);
                }
                Tree.Planet pl = planets.get(pid);
                if (pl == null) {
                    pl = new Tree.Planet();
                    pl.id = pid; pl.name = pname; pl.color = pcolor;
                    planets.put(pid, pl);
                }
                pl.nodes.add(id.toString());
            }
            // мелкие системы — всё на солнце
            if (se.getValue().size() < 14 && planets.size() > 1) {
                for (Tree.Planet pl : planets.values()) if (pl != sun) sun.nodes.addAll(pl.nodes);
                planets.keySet().retainAll(Set.of("sun"));
            }
            if (sun.nodes.isEmpty() && planets.size() > 1) {
                // солнце пустое — первая планета становится солнцем
                Iterator<Map.Entry<String, Tree.Planet>> it = planets.entrySet().iterator();
                it.next();
                it.remove();
                Tree.Planet first = planets.values().iterator().next();
                sun.nodes.addAll(first.nodes);
                sun.name = first.name;
                planets.remove(first.id);
                Map<String, Tree.Planet> re = new LinkedHashMap<>();
                re.put("sun", sun);
                re.putAll(planets);
                planets = re;
            }
            sd.planets.addAll(planets.values());

            // узлы: категория, стоимость, родители внутри планеты
            for (int pi = 0; pi < sd.planets.size(); pi++) {
                Tree.Planet pl = sd.planets.get(pi);
                Set<String> inPlanet = new HashSet<>(pl.nodes);
                for (String nid : pl.nodes) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(nid));
                    Tree.Node n = new Tree.Node();
                    n.item = nid;
                    n.system = sys;
                    n.planet = pi;
                    int d = depth.getOrDefault(item, 0);
                    JsonObject ov = overrides.has(nid) ? overrides.getAsJsonObject(nid) : null;
                    n.category = ov != null && ov.has("category") ? ov.get("category").getAsString()
                            : d >= complexDepth ? "complex" : d >= medDepth ? "medium" : "light";
                    JsonObject cat = cats.has(n.category) ? cats.getAsJsonObject(n.category) : cats.getAsJsonObject("light");
                    n.cost = ov != null && ov.has("cost") ? ov.get("cost").getAsInt() : cat.get("cost").getAsInt();
                    n.exp = ov != null && ov.has("exp") ? ov.get("exp").getAsInt() : cat.get("exp").getAsInt();
                    List<Item> pars = parentsOf.getOrDefault(item, List.of());
                    for (Item ing : pars) {
                        String iid = BuiltInRegistries.ITEM.getKey(ing).toString();
                        if (iid.equals(nid)) continue;
                        if (inPlanet.contains(iid)) n.parents.add(iid);
                        else if (n.external.size() < 4) n.external.add(iid);
                    }
                    // рецепт из многих закрытых частей — на ступень сложнее
                    if (ov == null || !ov.has("category")) {
                        if (pars.size() >= 3 && n.category.equals("light")) n.category = "medium";
                        else if (pars.size() >= 4 && n.category.equals("medium")) n.category = "complex";
                        JsonObject c2 = cats.has(n.category) ? cats.getAsJsonObject(n.category) : cats.getAsJsonObject("light");
                        if (ov == null || !ov.has("cost")) n.cost = c2.get("cost").getAsInt();
                        if (ov == null || !ov.has("exp")) n.exp = c2.get("exp").getAsInt();
                    }
                    tree.nodes.put(nid, n);
                }
                // ветки → раскладка; раскладка рвёт циклы и может дать новые входы — повторяем
                for (int pass = 0; pass < 3; pass++) {
                    Layout.branches(pl, tree.nodes, depth, maxRoots);
                    Layout.planet(pl, tree.nodes);
                    int roots = 0;
                    for (String nid : pl.nodes) if (tree.nodes.get(nid).parents.isEmpty() && tree.nodes.get(nid).soft.isEmpty()) roots++;
                    if (roots <= maxRoots) break;
                }
                for (String nid : pl.nodes) {
                    Tree.Node n = tree.nodes.get(nid);
                    JsonObject ov = overrides.has(nid) ? overrides.getAsJsonObject(nid) : null;
                    if (ov == null || !ov.has("cost")) n.cost = Math.min(maxCost, n.cost + n.depth / ringStep);
                }
                if (pi > 0) pl.requiresSun = Math.min(sunReq, sd.planets.get(0).nodes.size());
            }
            tree.systems.put(sys, sd);
        }
        // 6. Солнца модов со своей прогрессией: книга, как её получить, рецепт
        for (String key : own.keySet()) {
            if (key.startsWith("_") || !own.get(key).isJsonObject()) continue;
            if (!ModList.get().isLoaded(key)) continue;
            tree.systems.put(key, ownSystem(key, own.getAsJsonObject(key), ownNs));
        }
        if (testVanilla) { // самотест: в CI этих модов нет — проверяем вид на ванильной книге
            JsonObject t = JsonParser.parseString("{\"name\":\"Свой путь (тест)\",\"color\":\"#B36BFF\",\"book\":\"minecraft:writable_book\","
                    + "\"note\":\"Тестовая система со своей прогрессией.\",\"steps\":[{\"item\":\"minecraft:feather\",\"text\":\"Перо, чернила и книга на верстаке.\"},"
                    + "{\"item\":\"minecraft:bookshelf\",\"text\":\"Шаг без рецепта: просто текст-подсказка.\"}]}").getAsJsonObject();
            tree.systems.put("_test", ownSystem("_test", t, ownNs));
        }
        Layout.galaxy(tree);
        current = tree;
        Zalrpakraft.LOG.info("Zalepacraft: звёздная карта — систем {}, узлов {} ({} мс); свои пути: {}", tree.systems.size(), tree.nodes.size(),
                System.currentTimeMillis() - t0, ownNs);
        return tree;
    }

    // ==================== рецепты ====================
    static final class Rec {
        final String id, type;
        final int outCount;
        final List<Item[]> slots;
        boolean reverse;

        Rec(String id, String type, int outCount, List<Item[]> slots) {
            this.id = id;
            this.type = type;
            this.outCount = outCount;
            this.slots = slots;
        }
    }

    private static final int INF = 1000;
    private static final int MAX_DEPTH = 12;

    /** Переработка или распаковка: не показывает, как предмет на самом деле получают. */
    private static boolean isReverse(Item out, Rec r, Map<Item, List<Rec>> all) {
        if (r.slots.size() != 1) return false;
        Item[] only = r.slots.get(0);
        // переплавка инструментов и брони в самородки
        if (r.type.endsWith("smelting") || r.type.endsWith("blasting")) {
            boolean allDamageable = true;
            for (Item i : only) if (!new ItemStack(i).isDamageableItem()) { allDamageable = false; break; }
            if (allDamageable) return true;
        }
        // 1 блок → 9 слитков, если слитки сами собираются в этот блок
        if (only.length == 1 && r.outCount >= 4) {
            for (Rec back : all.getOrDefault(only[0], List.of())) {
                for (Item[] slot : back.slots) for (Item i : slot) if (i == out) return true;
            }
        }
        return false;
    }

    private static List<Rec> linkRecipes(Item it, Map<Item, List<Rec>> all) {
        List<Rec> list = all.getOrDefault(it, List.of());
        List<Rec> fwd = new ArrayList<>();
        for (Rec r : list) if (!r.reverse) fwd.add(r);
        return fwd.isEmpty() ? list : fwd;
    }

    private static int slotCost(Item[] slot, Map<Item, String> gated, Map<Item, Integer> d) {
        int m = INF;
        for (Item o : slot) {
            int v = !gated.containsKey(o) ? 0 : d.getOrDefault(o, INF) >= INF ? INF : d.get(o) + 1;
            if (v < m) m = v;
            if (m == 0) break;
        }
        return m;
    }

    private static int recCost(Rec r, Map<Item, String> gated, Map<Item, Integer> d) {
        int worst = 0;
        for (Item[] slot : r.slots) worst = Math.max(worst, slotCost(slot, gated, d));
        return worst;
    }

    private static boolean better(Rec a, Rec b) {
        boolean ac = a.type.equals("minecraft:crafting"), bc = b.type.equals("minecraft:crafting");
        if (ac != bc) return ac;
        if (a.slots.size() != b.slots.size()) return a.slots.size() < b.slots.size();
        return a.id.compareTo(b.id) < 0;
    }

    /** Глубина = минимум по рецептам (самый простой путь), основной рецепт — тот, что даёт минимум. */
    static void computeDepth(Map<Item, String> gated, Map<Item, List<Rec>> all, Map<Item, Integer> d, Map<Item, Rec> primary) {
        for (Item it : gated.keySet()) d.put(it, INF);
        for (int round = 0; round < 2; round++) {
            for (int iter = 0; iter < 64; iter++) {
                boolean changed = false;
                for (Item it : gated.keySet()) {
                    List<Rec> recs = linkRecipes(it, all);
                    int best = recs.isEmpty() ? 0 : INF;
                    for (Rec r : recs) best = Math.min(best, recCost(r, gated, d));
                    if (best < INF) best = Math.min(best, MAX_DEPTH);
                    if (best < d.get(it)) { d.put(it, best); changed = true; }
                }
                if (!changed) break;
            }
            // остались только замкнутые циклы — считаем их началом цепочки и досчитываем остальное
            boolean any = false;
            for (Item it : gated.keySet()) if (d.get(it) >= INF) { d.put(it, 0); any = true; }
            if (!any) break;
        }
        // основной рецепт — самый дешёвый при итоговых глубинах; при равенстве — верстак, меньше слотов, по имени
        for (Item it : gated.keySet()) {
            Rec bestR = null;
            int best = INF + 1;
            for (Rec r : linkRecipes(it, all)) {
                int c = recCost(r, gated, d);
                if (c < best || (c == best && (bestR == null || better(r, bestR)))) { best = c; bestR = r; }
            }
            if (bestR != null) primary.put(it, bestR);
        }
    }

    /** Из каждого слота основного рецепта — самый доступный вариант; если есть открытый (ванильный/декор) — связи нет. */
    static List<Item> chooseParents(Item it, Rec r, Map<Item, String> gated, Map<Item, Integer> d) {
        List<Item> out = new ArrayList<>();
        if (r == null) return out;
        String ns = BuiltInRegistries.ITEM.getKey(it).getNamespace();
        int myDepth = d.getOrDefault(it, 0);
        for (Item[] slot : r.slots) {
            Item best = null;
            boolean free = false;
            for (Item o : slot) {
                if (!gated.containsKey(o)) { free = true; break; }
                if (o == it) continue;
                if (best == null) { best = o; continue; }
                int dv = d.getOrDefault(o, 0), db = d.getOrDefault(best, 0);
                boolean sameO = BuiltInRegistries.ITEM.getKey(o).getNamespace().equals(ns);
                boolean sameB = BuiltInRegistries.ITEM.getKey(best).getNamespace().equals(ns);
                if (dv < db || (dv == db && sameO && !sameB)
                        || (dv == db && sameO == sameB && BuiltInRegistries.ITEM.getKey(o).compareTo(BuiltInRegistries.ITEM.getKey(best)) < 0)) best = o;
            }
            if (free || best == null || out.contains(best)) continue;
            if (d.getOrDefault(best, 0) >= myDepth && myDepth < MAX_DEPTH) continue; // защита от петель
            out.add(best);
        }
        return out;
    }

    // ==================== моды со своей прогрессией ====================
    private static boolean isOwn(String ns, JsonObject own) {
        return ns != null && !ns.startsWith("_") && own.has(ns);
    }

    /** Аддон, которому обязателен мод со своей прогрессией (Thaumaturge → его аддоны). */
    private static boolean dependsOnOwn(String ns, JsonObject own) {
        Optional<IModInfo> info = ModList.get().getModContainerById(ns).map(c -> c.getModInfo());
        if (info.isEmpty()) return false;
        for (IModInfo.ModVersion dep : info.get().getDependencies())
            if (dep.getType() == IModInfo.DependencyType.REQUIRED && isOwn(dep.getModId(), own)) return true;
        return false;
    }

    private static Tree.SystemDef ownSystem(String key, JsonObject o, Set<String> ownNs) {
        Tree.SystemDef sd = new Tree.SystemDef();
        sd.id = key;
        sd.own = true;
        sd.name = o.has("name") ? o.get("name").getAsString() : displayName(key);
        sd.color = o.has("color") ? parseColor(o.get("color").getAsString()) : hueColor(key);
        sd.book = o.has("book") ? o.get("book").getAsString() : "";
        sd.note = o.has("note") ? o.get("note").getAsString() : "";
        if (o.has("steps")) for (JsonElement e : o.getAsJsonArray("steps")) {
            JsonObject st = e.getAsJsonObject();
            sd.steps.add(new String[]{st.has("item") ? st.get("item").getAsString() : "", st.has("text") ? st.get("text").getAsString() : ""});
        }
        sd.mods.add(key);
        for (String ns : ownNs) if (!ns.equals(key) && !sd.mods.contains(ns)) {
            Optional<IModInfo> info = ModList.get().getModContainerById(ns).map(c -> c.getModInfo());
            if (info.isPresent() && info.get().getDependencies().stream().anyMatch(dp -> dp.getModId().equals(key))) sd.mods.add(ns);
        }
        return sd;
    }

    private static String root(String ns, Map<String, Integer> count, JsonObject merge, Set<String> seen) {
        if (merge.has(ns)) return merge.get(ns).getAsString();
        if (!seen.add(ns)) return ns;
        Optional<IModInfo> info = ModList.get().getModContainerById(ns).map(c -> c.getModInfo());
        if (info.isEmpty()) return ns;
        String best = null;
        int bestCount = -1;
        for (IModInfo.ModVersion dep : info.get().getDependencies()) {
            if (dep.getType() != IModInfo.DependencyType.REQUIRED) continue;
            String d = dep.getModId();
            if (d.equals(ns) || !count.containsKey(d)) continue;
            String r = root(d, count, merge, seen);
            int c = count.getOrDefault(r, 0);
            if (c > bestCount) { best = r; bestCount = c; }
        }
        return best == null ? ns : best;
    }

    private static boolean isDecor(Item item, ResourceLocation id, Set<Item> stonecut) {
        if (!(item instanceof BlockItem bi)) return false;
        if (bi.getBlock() instanceof EntityBlock) return false;
        ItemStack st = item.getDefaultInstance();
        for (TagKey<Item> t : DECOR_TAGS) if (st.is(t)) return true;
        if (stonecut.contains(item)) return true;
        for (String w : DECOR_WORDS) if (id.getPath().contains(w)) return true;
        return false;
    }

    private static String[] genericCategory(Item item) {
        if (item instanceof BlockItem bi) {
            if (bi.getBlock() instanceof EntityBlock) return new String[]{"machines", "Механизмы", "#E0A64B"};
            return new String[]{"blocks", "Строительство", "#9C8C7A"};
        }
        if (item instanceof TieredItem || item instanceof ArmorItem || item instanceof ProjectileWeaponItem || item instanceof ShieldItem)
            return new String[]{"gear", "Снаряжение", "#E85D5D"};
        if (item.getDefaultInstance().has(DataComponents.FOOD)) return new String[]{"food", "Кухня", "#7FD36B"};
        return new String[]{"sun", "Основы", "#FFFFFF"};
    }

    public static String displayName(String ns) {
        if (ns.equals("minecraft")) return "Minecraft";
        return ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
    }

    static int hueColor(String s) {
        float h = (s.hashCode() & 0xFFFF) / 65535f * 6f;
        float sat = 0.55f, v = 0.95f;
        int i = (int) h;
        float f = h - i, p = v * (1 - sat), q = v * (1 - sat * f), t = v * (1 - sat * (1 - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    static int parseColor(String c) {
        return Integer.parseInt(c.replace("#", ""), 16);
    }

    private TreeBuilder() {}
}
