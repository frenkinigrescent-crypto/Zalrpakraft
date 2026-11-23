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

        // 1. Какие предметы кто производит и из чего
        Map<Item, Set<Item>> ingredientsOf = new HashMap<>();
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
            Set<Item> ing = ingredientsOf.computeIfAbsent(item, k -> new LinkedHashSet<>());
            try {
                for (Ingredient in : r.getIngredients()) {
                    ItemStack[] opts = in.getItems();
                    if (opts.length == 0 || opts.length > 6) continue; // слишком общий тег — не связь
                    for (ItemStack s : opts) if (!s.isEmpty() && s.getItem() != item) ing.add(s.getItem());
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Отбор узлов
        Map<Item, String> gated = new LinkedHashMap<>(); // item -> namespace
        for (Item item : ingredientsOf.keySet()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            JsonObject ov = overrides.has(id.toString()) ? overrides.getAsJsonObject(id.toString()) : null;
            if (ov != null && ov.has("gated") && !ov.get("gated").getAsBoolean()) continue;
            if (ungateDecor && ov == null && isDecor(item, id, stonecut)) continue;
            gated.put(item, id.getNamespace());
        }

        // 3. Сложность = глубина цепочки крафта по закрытым ингредиентам
        Map<Item, Integer> depth = new HashMap<>();
        for (Item it : gated.keySet()) depth(it, gated, ingredientsOf, depth, new HashSet<>());

        // 4. Системы: аддон присоединяется к самой крупной обязательной зависимости, у которой есть узлы
        Map<String, Integer> nsCount = new HashMap<>();
        for (String ns : gated.values()) nsCount.merge(ns, 1, Integer::sum);
        JsonObject merge = cfg.has("merge") ? cfg.getAsJsonObject("merge") : new JsonObject();
        Map<String, String> rootOf = new HashMap<>();
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
                    for (Item ing : ingredientsOf.getOrDefault(item, Set.of())) {
                        String iid = BuiltInRegistries.ITEM.getKey(ing).toString();
                        if (inPlanet.contains(iid) && !iid.equals(nid)) n.parents.add(iid);
                    }
                    tree.nodes.put(nid, n);
                }
                Layout.branches(pl, tree.nodes, depth, maxRoots);
                Layout.planet(pl, tree.nodes);
                for (String nid : pl.nodes) {
                    Tree.Node n = tree.nodes.get(nid);
                    JsonObject ov = overrides.has(nid) ? overrides.getAsJsonObject(nid) : null;
                    if (ov == null || !ov.has("cost")) n.cost = Math.min(maxCost, n.cost + n.depth / ringStep);
                }
                if (pi > 0) pl.requiresSun = Math.min(sunReq, sd.planets.get(0).nodes.size());
            }
            tree.systems.put(sys, sd);
        }
        Layout.galaxy(tree);
        current = tree;
        Zalrpakraft.LOG.info("Zalepacraft: звёздная карта — систем {}, узлов {} ({} мс)", tree.systems.size(), tree.nodes.size(), System.currentTimeMillis() - t0);
        return tree;
    }

    private static int depth(Item it, Map<Item, String> gated, Map<Item, Set<Item>> ing, Map<Item, Integer> memo, Set<Item> stack) {
        Integer m = memo.get(it);
        if (m != null) return m;
        if (!stack.add(it)) return 0;
        int best = 0;
        for (Item p : ing.getOrDefault(it, Set.of())) if (gated.containsKey(p)) best = Math.max(best, depth(p, gated, ing, memo, stack) + 1);
        stack.remove(it);
        best = Math.min(best, 12);
        memo.put(it, best);
        return best;
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
