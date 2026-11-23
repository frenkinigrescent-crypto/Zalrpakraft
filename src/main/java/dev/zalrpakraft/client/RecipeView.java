package dev.zalrpakraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.*;

/** Рецепт предмета прямо на звёздной карте: ищется в рецептах, которые сервер прислал клиенту. */
public final class RecipeView {
    public record Shown(Component label, int w, int h, List<List<ItemStack>> cells, ItemStack result, boolean grid) {}

    private static final Map<Item, Optional<Shown>> CACHE = new HashMap<>();
    public static final int CELL = 18;

    public static void clearCache() {
        CACHE.clear();
    }

    public static Shown find(ItemStack target) {
        if (target == null || target.isEmpty()) return null;
        return CACHE.computeIfAbsent(target.getItem(), RecipeView::compute).orElse(null);
    }

    private static Optional<Shown> compute(Item target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Optional.empty();
        RegistryAccess ra = mc.level.registryAccess();
        RecipeHolder<?> best = null;
        int bestScore = Integer.MAX_VALUE;
        for (RecipeHolder<?> h : mc.level.getRecipeManager().getRecipes()) {
            Recipe<?> r = h.value();
            ItemStack out;
            try {
                out = r.getResultItem(ra);
            } catch (Exception e) {
                continue;
            }
            if (out == null || out.isEmpty() || out.getItem() != target) continue;
            int filled = 0;
            try {
                for (Ingredient in : r.getIngredients()) if (!in.isEmpty()) filled++;
            } catch (Exception e) {
                continue;
            }
            if (filled == 0) continue; // особые рецепты без списка ингредиентов
            int score = (r.getType() == RecipeType.CRAFTING ? 0 : 100) + filled;
            if (score < bestScore) { bestScore = score; best = h; }
        }
        if (best == null) return Optional.empty();
        Recipe<?> r = best.value();
        ItemStack result = r.getResultItem(ra).copy();
        List<List<ItemStack>> cells = new ArrayList<>();
        if (r instanceof ShapedRecipe sr) {
            for (Ingredient in : sr.getIngredients()) cells.add(opts(in));
            return Optional.of(new Shown(label(r), sr.getWidth(), sr.getHeight(), cells, result, true));
        }
        for (Ingredient in : r.getIngredients()) if (!in.isEmpty()) cells.add(opts(in));
        int n = cells.size();
        int w = r.getType() == RecipeType.CRAFTING ? Math.min(3, n) : Math.min(5, n);
        int h = (n + w - 1) / Math.max(1, w);
        return Optional.of(new Shown(label(r), Math.max(1, w), Math.max(1, h), cells, result, r.getType() == RecipeType.CRAFTING));
    }

    private static List<ItemStack> opts(Ingredient in) {
        List<ItemStack> l = new ArrayList<>();
        try {
            for (ItemStack s : in.getItems()) if (!s.isEmpty()) l.add(s);
        } catch (Exception ignored) {
        }
        return l;
    }

    private static Component label(Recipe<?> r) {
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
        if (id == null) return Component.translatable("zalrpakraft.recipe.other");
        String key = "zalrpakraft.recipe." + id.getNamespace() + "." + id.getPath();
        if (net.minecraft.client.resources.language.I18n.exists(key)) return Component.translatable(key);
        String mod = net.neoforged.fml.ModList.get().getModContainerById(id.getNamespace()).map(c -> c.getModInfo().getDisplayName()).orElse(id.getNamespace());
        String path = id.getPath().replace('_', ' ');
        return Component.literal(mod + ": " + path);
    }

    /** Ширина и высота карточки рецепта. */
    public static int[] size(Font font, Shown s) {
        int gw = s.w() * CELL, gh = s.h() * CELL;
        int w = Math.max(font.width(s.label()) + 8, gw + 30 + CELL + 8);
        return new int[]{w, gh + 18};
    }

    /** Рисует карточку; возвращает предмет под курсором (для подсказки) или пустой. */
    public static ItemStack draw(GuiGraphics g, Font font, Shown s, int x, int y, int mx, int my, int ticks, int accent) {
        int[] sz = size(font, s);
        g.fill(x - 1, y - 1, x + sz[0] + 1, y + sz[1] + 1, 0xFF000000 | (accent & 0xFFFFFF));
        g.fill(x, y, x + sz[0], y + sz[1], 0xF0101428);
        g.drawString(font, s.label(), x + 4, y + 4, 0xFFFFE6A6, false);
        int gx = x + 4, gy = y + 14;
        ItemStack hover = ItemStack.EMPTY;
        for (int i = 0; i < s.w() * s.h(); i++) {
            int cx = gx + (i % s.w()) * CELL, cy = gy + (i / s.w()) * CELL;
            g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF262B45);
            if (i >= s.cells().size()) continue;
            List<ItemStack> o = s.cells().get(i);
            if (o.isEmpty()) continue;
            ItemStack st = o.get((ticks / 20) % o.size());
            g.renderItem(st, cx, cy);
            if (mx >= cx && my >= cy && mx < cx + CELL && my < cy + CELL) hover = st;
        }
        int ax = gx + s.w() * CELL + 6, ay = gy + (s.h() * CELL) / 2 - 4;
        g.drawString(font, "→", ax, ay, 0xFFB79CFF, false);
        int rx = ax + 14, ry = gy + (s.h() * CELL) / 2 - 9;
        g.fill(rx - 1, ry - 1, rx + CELL, ry + CELL, 0xFF3A2E60);
        g.renderItem(s.result(), rx, ry);
        g.renderItemDecorations(font, s.result(), rx, ry);
        if (mx >= rx && my >= ry && mx < rx + CELL && my < ry + CELL) hover = s.result();
        return hover;
    }

    private RecipeView() {}
}
