package dev.zalrpakraft.tree;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Все результаты рецепта. Обычно один (getResultItem), но некоторые моды дают выбор из нескольких —
 * например кузня Lookin' Sharp: из одного ванильного меча 10 видов оружия (метод getResults()).
 */
public final class RecipeOutputs {
    private static final Map<Class<?>, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    public static List<ItemStack> all(Recipe<?> r, HolderLookup.Provider reg) {
        List<ItemStack> out = new ArrayList<>();
        try {
            ItemStack first = r.getResultItem(reg);
            if (first != null && !first.isEmpty()) out.add(first);
        } catch (Throwable ignored) {
        }
        for (ItemStack s : extra(r)) {
            boolean dup = false;
            for (ItemStack o : out) if (o.getItem() == s.getItem()) { dup = true; break; }
            if (!dup) out.add(s);
        }
        return out;
    }

    /** Результаты из getResults(), если у рецепта он есть и возвращает список ItemStack. */
    public static List<ItemStack> extra(Recipe<?> r) {
        Optional<Method> m = METHODS.computeIfAbsent(r.getClass(), c -> {
            try {
                Method mm = c.getMethod("getResults");
                return List.class.isAssignableFrom(mm.getReturnType()) && mm.getParameterCount() == 0 ? Optional.of(mm) : Optional.empty();
            } catch (Throwable e) {
                return Optional.empty();
            }
        });
        if (m.isEmpty()) return List.of();
        List<ItemStack> out = new ArrayList<>();
        try {
            Object v = m.get().invoke(r);
            if (v instanceof List<?> l) for (Object o : l) if (o instanceof ItemStack s && !s.isEmpty()) out.add(s);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private RecipeOutputs() {}
}
