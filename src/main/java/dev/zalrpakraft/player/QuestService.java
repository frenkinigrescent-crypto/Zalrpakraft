package dev.zalrpakraft.player;

import dev.zalrpakraft.data.ProgressionMap;
import dev.zalrpakraft.data.QuestBook;
import dev.zalrpakraft.data.QuestBook.Quest;
import dev.zalrpakraft.data.QuestBook.Reward;
import dev.zalrpakraft.data.QuestBook.Task;
import dev.zalrpakraft.net.PlaquePayload;
import dev.zalrpakraft.net.QuestBookPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

/** Серверная логика книги заданий. Статусы: 0 — закрыто, 1 — в работе, 2 — выполнено (награда ждёт), 3 — награда получена. */
public final class QuestService {

    public static int status(PlayerProgress pr, Quest q) {
        if (pr.questsClaimed.contains(q.id)) return 3;
        if (pr.questsDone.contains(q.id)) return 2;
        for (String r : q.requires) if (!pr.questsDone.contains(r)) return 0;
        return 1;
    }

    public static int progress(ServerPlayer p, PlayerProgress pr, Task t) {
        switch (t.type) {
            case "item": {
                int n = 0;
                for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && matchesItem(s, t)) n += s.getCount();
                for (ItemStack s : p.getInventory().offhand) if (!s.isEmpty() && matchesItem(s, t)) n += s.getCount();
                return n;
            }
            case "kill": {
                int n = 0;
                for (Map.Entry<String, Integer> e : pr.killCounts.entrySet())
                    for (String v : t.values) if (ProgressService.matches(v, e.getKey())) { n += e.getValue(); break; }
                return n;
            }
            case "node":
                return pr.studied.contains(t.values.get(0)) ? 1 : 0;
            case "dimension":
                return pr.visitedDims.contains(t.values.get(0)) ? 1 : 0;
            case "story":
                return switch (t.values.get(0)) {
                    case "journal" -> pr.journalRead ? 1 : 0;
                    case "house" -> pr.houseFound ? 1 : 0;
                    case "table" -> pr.tableOpened ? 1 : 0;
                    default -> 0;
                };
            default:
                return 0;
        }
    }

    private static boolean matchesItem(ItemStack s, Task t) {
        for (String v : t.values) {
            if (v.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(v.substring(1));
                if (rl != null && s.is(TagKey.create(Registries.ITEM, rl))) return true;
            } else {
                Item it = QuestBook.item(v);
                if (it != null && s.is(it)) return true;
            }
        }
        return false;
    }

    /** Проверить активные задания. true — что-то выполнено. */
    public static boolean check(ServerPlayer p) {
        PlayerProgress pr = ProgressService.of(p);
        boolean any = false;
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 16) {
            changed = false;
            for (Quest q : QuestBook.book().quests) {
                if (status(pr, q) != 1) continue;
                boolean all = true;
                for (Task t : q.tasks) if (progress(p, pr, t) < t.count) { all = false; break; }
                if (!all) continue;
                pr.questsDone.add(q.id);
                if (q.id.equals(pr.tracked)) pr.tracked = "";
                changed = any = true;
                p.displayClientMessage(Component.translatable("zalrpakraft.quest.complete", Component.literal(q.name).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GREEN), false);
                PacketDistributor.sendToPlayer(p, new PlaquePayload("#" + q.name, q.icon, "quest"));
            }
        }
        return any;
    }

    public static void claim(ServerPlayer p, String id) {
        PlayerProgress pr = ProgressService.of(p);
        Quest q = QuestBook.book().quest(id);
        if (q == null || status(pr, q) != 2) return;
        pr.questsClaimed.add(id);
        for (Reward r : q.rewards) {
            Item it = QuestBook.item(r.id);
            if (it == null) continue;
            ItemStack st = new ItemStack(it, Math.max(1, r.count));
            if (!p.getInventory().add(st)) p.drop(st, false);
        }
        if (q.xp > 0) p.giveExperiencePoints(q.xp);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.3F);
        openBook(p);
        ProgressService.sync(p);
    }

    public static void track(ServerPlayer p, String id) {
        PlayerProgress pr = ProgressService.of(p);
        pr.tracked = id.equals(pr.tracked) ? "" : id;
        openBook(p);
        ProgressService.sync(p);
    }

    /** Состояние всех заданий для клиента. */
    public static CompoundTag state(ServerPlayer p) {
        PlayerProgress pr = ProgressService.of(p);
        CompoundTag root = new CompoundTag();
        CompoundTag qs = new CompoundTag();
        for (Quest q : QuestBook.book().quests) {
            CompoundTag t = new CompoundTag();
            int st = status(pr, q);
            t.putInt("s", st);
            int[] prog = new int[q.tasks.size()];
            for (int i = 0; i < prog.length; i++) {
                Task task = q.tasks.get(i);
                prog[i] = st >= 2 ? task.count : Math.min(task.count, progress(p, pr, task));
            }
            t.putIntArray("p", prog);
            qs.put(q.id, t);
        }
        root.put("quests", qs);
        root.putString("tracked", pr.tracked);
        return root;
    }

    public static void openBook(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new QuestBookPayload(QuestBook.json(), state(p)));
    }

    /**
     * Строка задания для HUD: "$тип|значение|сейчас|нужно|подпись" — клиент сам переведёт.
     * null — если отслеживаемого задания нет.
     */
    public static String[] hudLine(ServerPlayer p, PlayerProgress pr) {
        if (pr.tracked.isEmpty()) return null;
        Quest q = QuestBook.book().quest(pr.tracked);
        if (q == null || status(pr, q) != 1) return null;
        for (Task t : q.tasks) {
            int cur = progress(p, pr, t);
            if (cur < t.count) {
                String label = t.label;
                if (label.isEmpty() && t.type.equals("node")) {
                    ProgressionMap.Node n = ProgressionMap.nodes().get(t.values.get(0));
                    if (n != null) label = n.name;
                }
                return new String[]{"#" + q.name, "$" + t.type + "|" + t.values.get(0) + "|" + Math.min(cur, t.count) + "|" + t.count + "|" + label};
            }
        }
        return null;
    }

    private QuestService() {}
}
