package dev.zalrpakraft.client;

import dev.zalrpakraft.data.ProgressionMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Человеческое описание задачи задания: «Добыть: Андезитовый сплав 3/16». */
public final class TaskText {
    public static Component describe(String type, String value, int cur, int count, String label) {
        Component what = switch (type) {
            case "item" -> label.isEmpty() ? itemName(value) : Component.literal(label);
            case "kill" -> label.isEmpty() ? entityName(value) : Component.literal(label);
            case "node" -> Component.literal(label.isEmpty() ? nodeName(value) : label);
            case "dimension" -> Component.literal(label.isEmpty() ? value : label);
            case "story" -> Component.translatable("zalrpakraft.task.story." + value);
            default -> Component.literal(value);
        };
        String key = switch (type) {
            case "item" -> "zalrpakraft.task.item";
            case "kill" -> "zalrpakraft.task.kill";
            case "node" -> "zalrpakraft.task.node";
            case "dimension" -> "zalrpakraft.task.dimension";
            default -> "zalrpakraft.task.story";
        };
        Component base = Component.translatable(key, what);
        if (count > 1) return base.copy().append(Component.literal("  " + cur + "/" + count));
        return base;
    }

    /** Декодер строки HUD "$тип|значение|сейчас|нужно|подпись". */
    public static Component fromHud(String s) {
        String[] p = s.substring(1).split("\\|", -1);
        if (p.length < 5) return Component.literal(s);
        int cur = parse(p[2]), count = parse(p[3]);
        return describe(p[0], p[1], cur, count, p[4]);
    }

    public static ItemStack icon(String id) {
        if (id.startsWith("#")) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return ItemStack.EMPTY;
        Item it = BuiltInRegistries.ITEM.get(rl);
        return new ItemStack(it);
    }

    private static Component itemName(String id) {
        ItemStack st = icon(id);
        if (!st.isEmpty()) return st.getHoverName();
        return Component.literal(id.startsWith("#") ? id.substring(1) : id);
    }

    private static Component entityName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && !id.contains("*") && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(rl);
            return t.getDescription();
        }
        return Component.literal(id);
    }

    private static String nodeName(String id) {
        ProgressionMap.Node n = ProgressionMap.nodes().get(id);
        return n == null ? id : n.name;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private TaskText() {}
}
