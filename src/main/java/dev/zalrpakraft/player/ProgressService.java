package dev.zalrpakraft.player;

import dev.zalrpakraft.data.ProgressionMap;
import dev.zalrpakraft.data.ProgressionMap.Node;
import dev.zalrpakraft.net.OpenMapPayload;
import dev.zalrpakraft.net.SyncProgressPayload;
import dev.zalrpakraft.registry.ZAttachments;
import dev.zalrpakraft.world.WorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/** Серверная логика прогресса: изучение узлов карты, текущее задание, синхронизация с клиентом. */
public final class ProgressService {

    public static PlayerProgress of(ServerPlayer p) {
        return p.getData(ZAttachments.PROGRESS);
    }

    /** Отправить клиенту прогресс + текущее задание + позицию домика. */
    public static void sync(ServerPlayer p) {
        PlayerProgress pr = of(p);
        CompoundTag t = pr.write();
        WorldState ws = WorldState.get(p.server);
        if (ws.housePos != null) {
            t.putInt("houseX", ws.housePos.getX());
            t.putInt("houseY", ws.housePos.getY());
            t.putInt("houseZ", ws.housePos.getZ());
        }
        String[] q = quest(p, pr, ws);
        t.putString("questTitle", q[0]);
        t.putString("questText", q[1]);
        PacketDistributor.sendToPlayer(p, new SyncProgressPayload(t));
    }

    public static void openMap(ServerPlayer p) {
        PlayerProgress pr = of(p);
        boolean first = !pr.tableOpened;
        pr.tableOpened = true;
        pr.houseFound = true;
        if (first) {
            p.displayClientMessage(Component.translatable("zalrpakraft.table.first").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        }
        update(p);
        sync(p);
        CompoundTag t = pr.write();
        PacketDistributor.sendToPlayer(p, new OpenMapPayload(ProgressionMap.rawJson(), t));
    }

    /** {заголовок, текст} — ключи перевода или готовые строки (клиент сам решает). */
    public static String[] quest(ServerPlayer p, PlayerProgress pr, WorldState ws) {
        if (!pr.journalRead) return new String[]{"zalrpakraft.quest.read.title", "zalrpakraft.quest.read.text"};
        if (!pr.houseFound) {
            BlockPos h = ws.housePos;
            String coords = h == null ? "?" : h.getX() + " " + h.getZ();
            return new String[]{"zalrpakraft.quest.house.title", "@" + coords};
        }
        if (!pr.tableOpened) return new String[]{"zalrpakraft.quest.table.title", "zalrpakraft.quest.table.text"};
        for (Node n : ProgressionMap.nodes().values()) {
            if (n.kind == ProgressionMap.Kind.GALAXY || n.kind == ProgressionMap.Kind.ROOT) continue;
            if (pr.studied.contains(n.id)) continue;
            if (!ProgressionMap.available(n, pr)) continue;
            return new String[]{"#" + n.name, "#" + (n.hint.isEmpty() ? n.desc : n.hint)};
        }
        return new String[]{"zalrpakraft.quest.done.title", "zalrpakraft.quest.done.text"};
    }

    /** Проверить условия всех доступных узлов. Возвращает true, если что-то изучено. */
    public static boolean update(ServerPlayer p) {
        PlayerProgress pr = of(p);
        boolean any = false;
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 64) {
            changed = false;
            for (Node n : ProgressionMap.nodes().values()) {
                if (pr.studied.contains(n.id)) continue;
                if (!ProgressionMap.available(n, pr)) continue;
                if (!conditionMet(p, pr, n)) continue;
                pr.studied.add(n.id);
                changed = true;
                any = true;
                announce(p, n);
            }
        }
        return any;
    }

    private static void announce(ServerPlayer p, Node n) {
        if (n.kind == ProgressionMap.Kind.ROOT) return;
        if (n.kind == ProgressionMap.Kind.PLANET && !n.mod.isEmpty() && !ModList.get().isLoaded(n.mod)) return;
        String key = switch (n.kind) {
            case GALAXY -> "zalrpakraft.study.galaxy";
            case METEOR -> "zalrpakraft.study.meteor";
            case GATE -> "zalrpakraft.study.gate";
            default -> "zalrpakraft.study.planet";
        };
        p.displayClientMessage(Component.translatable(key, Component.literal(n.name).withColor(n.color)).withStyle(ChatFormatting.GRAY), false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F,
                n.kind == ProgressionMap.Kind.METEOR ? 0.6F : 1.4F);
    }

    private static boolean conditionMet(ServerPlayer p, PlayerProgress pr, Node n) {
        if (!pr.tableOpened) return false; // пока стол не найден — карта молчит
        switch (n.unlockType) {
            case "table":
                return pr.tableOpened;
            case "auto":
                return true;
            case "mod":
                if (!ModList.get().isLoaded(n.unlockValue)) return true; // мода нет в сборке — пропускаем
                return hasItemFrom(p.getInventory(), n.unlockValue);
            case "item": {
                ResourceLocation id = ResourceLocation.tryParse(n.unlockValue);
                if (id == null) return false;
                if (!n.mod.isEmpty() && !ModList.get().isLoaded(n.mod)) return true;
                for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(s.getItem()))) return true;
                return false;
            }
            case "kill":
                for (String k : pr.kills) if (matches(n.unlockValue, k)) return true;
                return false;
            case "dimension":
                return pr.visitedDims.contains(n.unlockValue);
            default:
                return false;
        }
    }

    private static boolean hasItemFrom(Inventory inv, String namespace) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && namespace.equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace())) return true;
        }
        return false;
    }

    /** "мод:*", "мод*:*", точный ID. */
    public static boolean matches(String pattern, String id) {
        String[] p = pattern.split(":", 2);
        String[] v = id.split(":", 2);
        if (p.length != 2 || v.length != 2) return pattern.equals(id);
        return part(p[0], v[0]) && part(p[1], v[1]);
    }

    private static boolean part(String pat, String val) {
        if (pat.equals("*")) return true;
        if (pat.endsWith("*")) return val.startsWith(pat.substring(0, pat.length() - 1));
        return pat.equals(val);
    }

    private ProgressService() {}
}
