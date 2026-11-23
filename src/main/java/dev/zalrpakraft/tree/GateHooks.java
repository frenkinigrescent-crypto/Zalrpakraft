package dev.zalrpakraft.tree;

import dev.zalrpakraft.client.ClientTree;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/** Точки входа для миксинов. Всё, что может упасть, гасится — крафт важнее замка. */
public final class GateHooks {
    private static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    private static ItemStack result(Object holderOrRecipe) {
        try {
            Object r = holderOrRecipe;
            if (r instanceof com.mojang.datafixers.util.Pair<?, ?> pair) r = pair.getSecond();
            if (r instanceof RecipeHolder<?> h) r = h.value();
            MinecraftServer s = server();
            if (r instanceof Recipe<?> rec && s != null) return rec.getResultItem(s.registryAccess());
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    /** Только серверный менеджер рецептов (у клиента свой экземпляр). */
    private static boolean isServerManager(RecipeManager rm) {
        MinecraftServer s = server();
        return s != null && s.getRecipeManager() == rm && TreeBuilder.tree() != Tree.EMPTY;
    }

    public static boolean serverAllows(RecipeManager rm, Object holder) {
        if (!isServerManager(rm)) return true;
        return StarService.machineAllowed(result(holder));
    }

    public static List<?> filter(RecipeManager rm, List<?> list) {
        if (!isServerManager(rm)) return list;
        return filterAnyServer(list);
    }

    public static List<?> filterAnyServer(List<?> list) {
        if (server() == null || TreeBuilder.tree() == Tree.EMPTY) return list;
        List<Object> out = null;
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (!StarService.machineAllowed(result(o))) {
                if (out == null) out = new ArrayList<>(list.subList(0, i));
            } else if (out != null) out.add(o);
        }
        return out == null ? list : out;
    }

    public static boolean machineAllowsRecipe(Recipe<?> recipe) {
        if (server() == null || TreeBuilder.tree() == Tree.EMPTY) return true;
        return StarService.machineAllowed(result(recipe));
    }

    public static boolean playerAllows(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || player == null) return true;
        try {
            if (player instanceof ServerPlayer sp) return TreeBuilder.tree() == Tree.EMPTY || StarService.playerAllowed(sp, stack);
            if (player.level().isClientSide && FMLEnvironment.dist.isClient()) return player.isCreative() || ClientTree.allows(stack);
        } catch (Throwable ignored) {
        }
        return true;
    }

    private GateHooks() {}
}
