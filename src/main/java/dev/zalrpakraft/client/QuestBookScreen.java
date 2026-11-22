package dev.zalrpakraft.client;

import dev.zalrpakraft.data.QuestBook;
import dev.zalrpakraft.data.QuestBook.Chapter;
import dev.zalrpakraft.data.QuestBook.Quest;
import dev.zalrpakraft.data.QuestBook.Reward;
import dev.zalrpakraft.data.QuestBook.Task;
import dev.zalrpakraft.net.SimplePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Книга заданий: главы слева, задания в середине, подробности справа. Открывается клавишей J. */
public class QuestBookScreen extends Screen {
    private static String lastChapter = "";
    private static String lastQuest = "";

    private QuestBook.Book book;
    private CompoundTag state;
    private String chapter;
    private String selected;
    private int listScroll;
    private final List<float[]> stars = new ArrayList<>();
    private StarButton claimBtn, trackBtn;
    private int ticks;

    public QuestBookScreen(String json, CompoundTag state) {
        super(Component.translatable("zalrpakraft.book.title"));
        this.book = QuestBook.fromJson(json);
        this.state = state;
        RandomSource r = RandomSource.create(77);
        for (int i = 0; i < 260; i++) stars.add(new float[]{r.nextFloat(), r.nextFloat(), 0.2f + r.nextFloat() * 0.8f, r.nextFloat() * 6.28f});
        chapter = book.chapters.stream().anyMatch(c -> c.id.equals(lastChapter)) ? lastChapter : firstChapter();
        selected = lastQuest;
    }

    /** Обновить данные без закрытия (после получения награды). */
    public void update(String json, CompoundTag state) {
        this.book = QuestBook.fromJson(json);
        this.state = state;
        refreshButtons();
    }

    private String firstChapter() {
        for (Chapter c : book.chapters) if (!quests(c.id).isEmpty()) return c.id;
        return book.chapters.isEmpty() ? "" : book.chapters.get(0).id;
    }

    private List<Quest> quests(String ch) {
        List<Quest> l = new ArrayList<>();
        for (Quest q : book.quests) if (q.chapter.equals(ch)) l.add(q);
        return l;
    }

    private int status(Quest q) {
        CompoundTag qs = state.getCompound("quests");
        return qs.contains(q.id) ? qs.getCompound(q.id).getInt("s") : 0;
    }

    private int[] prog(Quest q) {
        return state.getCompound("quests").getCompound(q.id).getIntArray("p");
    }

    // ---------- раскладка ----------
    private int chapW() { return Math.min(118, width / 4); }
    private int listW() { return Math.min(150, width / 3); }
    private int top() { return 30; }
    private int bottom() { return height - 12; }
    private int chapRow() {
        int n = 0;
        for (Chapter c : book.chapters) if (!quests(c.id).isEmpty()) n++;
        return Mth.clamp((bottom() - top()) / Math.max(1, n), 12, 21);
    }

    private int detailX() { return 10 + chapW() + 6 + listW() + 6; }

    @Override
    protected void init() {
        int dx = detailX(), dw = width - dx - 10;
        int bw = Math.min(110, (dw - 6) / 2);
        claimBtn = addRenderableWidget(new StarButton(dx, bottom() - 24, bw, 20, Component.translatable("zalrpakraft.book.claim"), () -> {
            if (selected != null && !selected.isEmpty()) PacketDistributor.sendToServer(new SimplePayload("claim:" + selected));
        }));
        trackBtn = addRenderableWidget(new StarButton(dx + bw + 6, bottom() - 24, bw, 20, Component.translatable("zalrpakraft.book.track"), () -> {
            if (selected != null && !selected.isEmpty()) PacketDistributor.sendToServer(new SimplePayload("track:" + selected));
        }));
        refreshButtons();
    }

    private void refreshButtons() {
        if (claimBtn == null) return;
        Quest q = selected == null ? null : book.quest(selected);
        claimBtn.visible = trackBtn.visible = q != null;
        if (q == null) return;
        int st = status(q);
        claimBtn.active = st == 2;
        claimBtn.setMessage(Component.translatable(st == 3 ? "zalrpakraft.book.claimed" : "zalrpakraft.book.claim"));
        trackBtn.active = st == 1;
        boolean tracked = q.id.equals(state.getString("tracked"));
        trackBtn.setMessage(Component.translatable(tracked ? "zalrpakraft.book.untrack" : "zalrpakraft.book.track"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // фон рисуется в render() до панелей, чтобы кнопки оказались поверх всего
    }

    private void sky(GuiGraphics g, float pt) {
        float t = ticks + pt;
        g.fillGradient(0, 0, width, height, 0xF0060A1A, 0xF0150A26);
        for (float[] s : stars) {
            int a = Mth.clamp((int) (255 * s[2] * (0.55f + 0.45f * Mth.sin(t * 0.08f + s[3]))), 0, 255);
            int x = (int) (s[0] * width), y = (int) (s[1] * height);
            g.fill(x, y, x + 1, y + 1, (a << 24) | 0xFFFFFF);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        sky(g, pt);
        content(g, mx, my);
        super.render(g, mx, my, pt); // кнопки поверх панелей
    }

    private void content(GuiGraphics g, int mx, int my) {
        // заголовок
        int done = 0, total = book.quests.size();
        for (Quest q : book.quests) if (status(q) >= 2) done++;
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, 8, 0xFFE9DDFF);
        g.drawCenteredString(font, Component.translatable("zalrpakraft.book.progress", done, total), width / 2, 18, 0xFF9A8CC0);

        // главы
        int x = 10, y = top(), w = chapW();
        panel(g, x - 4, y - 4, x + w + 2, bottom(), 0xFF6A54A8);
        Component hovered = null;
        for (Chapter c : book.chapters) {
            List<Quest> qs = quests(c.id);
            if (qs.isEmpty()) continue;
            int cd = 0, claimable = 0;
            for (Quest q : qs) { int s = status(q); if (s >= 2) cd++; if (s == 2) claimable++; }
            boolean sel = c.id.equals(chapter);
            int rh = chapRow();
            boolean hov = mx >= x - 2 && mx < x + w && my >= y - 1 && my < y + rh - 2;
            if (sel) g.fill(x - 2, y - 1, x + w, y + rh - 2, 0x60000000 | (c.color & 0xFFFFFF));
            else if (hov) g.fill(x - 2, y - 1, x + w, y + rh - 2, 0x30FFFFFF);
            if (rh >= 19) {
                g.renderItem(TaskText.icon(c.icon), x, y);
                g.drawString(font, font.plainSubstrByWidth(c.name, w - 34), x + 20, y + 1, 0xFF000000 | c.color, false);
                g.drawString(font, cd + "/" + qs.size(), x + 20, y + 10, 0xFF8C8399, false);
            } else {
                String cnt = cd + "/" + qs.size();
                g.drawString(font, font.plainSubstrByWidth(c.name, w - 14 - font.width(cnt)), x + 2, y + 1, 0xFF000000 | c.color, false);
                g.drawString(font, cnt, x + w - 12 - font.width(cnt), y + 1, 0xFF8C8399, false);
            }
            if (claimable > 0) g.drawString(font, "★", x + w - 10, y + (rh >= 19 ? 5 : 1), 0xFFFFD27A, false);
            y += rh;
        }

        // список заданий главы
        int lx = x + w + 10, lw = listW();
        panel(g, lx - 4, top() - 4, lx + lw + 2, bottom(), 0xFF6A54A8);
        List<Quest> list = quests(chapter);
        int rowH = 20;
        int visible = (bottom() - top()) / rowH;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, list.size() - visible));
        y = top();
        for (int i = listScroll; i < list.size() && y + rowH <= bottom(); i++) {
            Quest q = list.get(i);
            int st = status(q);
            boolean sel = q.id.equals(selected);
            boolean hov = mx >= lx - 2 && mx < lx + lw && my >= y - 1 && my < y + rowH - 1;
            if (sel) g.fill(lx - 2, y - 1, lx + lw, y + rowH - 1, 0x507A5BA8);
            else if (hov) g.fill(lx - 2, y - 1, lx + lw, y + rowH - 1, 0x25FFFFFF);
            ItemStack icon = TaskText.icon(q.icon);
            if (st == 0) {
                g.fill(lx, y + 1, lx + 16, y + 17, 0xFF2A2438);
                g.drawString(font, "?", lx + 6, y + 5, 0xFF6E6680, false);
            } else g.renderItem(icon, lx, y);
            int col = switch (st) { case 0 -> 0xFF6E6680; case 2 -> 0xFFFFE6A6; case 3 -> 0xFF8FD18A; default -> 0xFFE6DDF8; };
            String name = st == 0 ? "???" : q.name;
            g.drawString(font, font.plainSubstrByWidth(name, lw - 34), lx + 20, y + 5, col, false);
            String mark = switch (st) { case 2 -> "★"; case 3 -> "✔"; case 1 -> q.id.equals(state.getString("tracked")) ? "◉" : "•"; default -> ""; };
            g.drawString(font, mark, lx + lw - 10, y + 5, st == 2 ? 0xFFFFD27A : st == 3 ? 0xFF8FD18A : 0xFFB79CFF, false);
            y += rowH;
        }

        // подробности
        int dx = detailX(), dw = width - dx - 10;
        panel(g, dx - 4, top() - 4, width - 6, bottom(), 0xFF6A54A8);
        Quest q = selected == null ? null : book.quest(selected);
        if (q == null || !q.chapter.equals(chapter)) {
            int cy = (top() + bottom()) / 2 - 10;
            for (FormattedCharSequence l : font.split(Component.translatable("zalrpakraft.book.pick"), dw - 8))
                { g.drawCenteredString(font, l, dx + dw / 2, cy, 0xFF8C8399); cy += 10; }
            if (claimBtn != null) claimBtn.visible = trackBtn.visible = false;
            return;
        }
        int st = status(q);
        y = top();
        g.renderItem(TaskText.icon(q.icon), dx, y);
        g.drawString(font, st == 0 ? "???" : q.name, dx + 20, y + 4, 0xFFFFE6A6, true);
        y += 22;
        if (st == 0) {
            g.drawString(font, Component.translatable("zalrpakraft.book.locked"), dx, y, 0xFF8C8399, false);
            y += 12;
            for (String r : q.requires) {
                Quest rq = book.quest(r);
                if (rq != null) { g.drawString(font, "• " + rq.name, dx + 4, y, 0xFFB0A6C8, false); y += 10; }
            }
            return;
        }
        for (FormattedCharSequence l : font.split(Component.literal(q.desc).withStyle(ChatFormatting.ITALIC), dw - 4)) {
            g.drawString(font, l, dx, y, 0xFFB0A6C8, false);
            y += 10;
        }
        y += 6;
        g.drawString(font, Component.translatable("zalrpakraft.book.tasks"), dx, y, 0xFFB79CFF, false);
        y += 12;
        int[] p = prog(q);
        for (int i = 0; i < q.tasks.size(); i++) {
            Task t = q.tasks.get(i);
            int cur = i < p.length ? p[i] : 0;
            boolean ok = cur >= t.count;
            ItemStack ic = t.type.equals("item") ? TaskText.icon(t.values.get(0)) : ItemStack.EMPTY;
            int tx = dx;
            if (!ic.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(dx, y - 2, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.renderItem(ic, 0, 0);
                g.pose().popPose();
                tx += 14;
            }
            Component line = TaskText.describe(t.type, t.values.get(0), cur, t.count, t.label);
            List<FormattedCharSequence> ls = font.split(line, dw - (tx - dx) - 14);
            for (FormattedCharSequence l : ls) {
                g.drawString(font, l, tx, y, ok ? 0xFF8FD18A : 0xFFE6DDF8, false);
                y += 10;
            }
            if (ok) g.drawString(font, "✔", dx + dw - 10, y - 10, 0xFF8FD18A, false);
            // полоса прогресса
            if (t.count > 1) {
                int bw = dw - (tx - dx) - 14;
                g.fill(tx, y, tx + bw, y + 2, 0xFF2A2438);
                g.fill(tx, y, tx + (int) (bw * Math.min(1f, cur / (float) t.count)), y + 2, ok ? 0xFF8FD18A : 0xFFB79CFF);
                y += 5;
            }
            y += 2;
        }
        y += 4;
        if (!q.rewards.isEmpty() || q.xp > 0) {
            g.drawString(font, Component.translatable("zalrpakraft.book.rewards"), dx, y, 0xFFFFD27A, false);
            y += 11;
            int rx = dx;
            for (Reward r : q.rewards) {
                ItemStack st2 = TaskText.icon(r.id);
                if (st2.isEmpty()) continue;
                st2.setCount(Math.max(1, r.count));
                if (rx + 18 > dx + dw) { rx = dx; y += 18; }
                g.fill(rx - 1, y - 1, rx + 17, y + 17, 0x40000000);
                g.renderItem(st2, rx, y);
                g.renderItemDecorations(font, st2, rx, y);
                if (mx >= rx && mx < rx + 16 && my >= y && my < y + 16) hovered = st2.getHoverName();
                rx += 20;
            }
            if (q.xp > 0) g.drawString(font, Component.translatable("zalrpakraft.book.xp", q.xp), rx + 2, y + 4, 0xFFB5F07A, false);
        }
        if (hovered != null) g.renderTooltip(font, hovered, mx, my);
    }

    private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int border) {
        g.fill(x0, y0, x1, y1, 0x9A0C0818);
        g.fill(x0, y0, x1, y0 + 1, border);
        g.fill(x0, y1 - 1, x1, y1, 0xFF3C2C5A);
        g.fill(x0, y0, x0 + 1, y1, 0xFF3C2C5A);
        g.fill(x1 - 1, y0, x1, y1, 0xFF3C2C5A);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int x = 10, y = top(), w = chapW();
        for (Chapter c : book.chapters) {
            if (quests(c.id).isEmpty()) continue;
            if (mx >= x - 2 && mx < x + w && my >= y - 1 && my < y + chapRow() - 2) {
                chapter = lastChapter = c.id;
                listScroll = 0;
                playClick();
                return true;
            }
            y += chapRow();
        }
        int lx = x + w + 10, lw = listW();
        List<Quest> list = quests(chapter);
        y = top();
        for (int i = listScroll; i < list.size() && y + 20 <= bottom(); i++) {
            if (mx >= lx - 2 && mx < lx + lw && my >= y - 1 && my < y + 19) {
                selected = lastQuest = list.get(i).id;
                refreshButtons();
                playClick();
                return true;
            }
            y += 20;
        }
        return false;
    }

    private void playClick() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.7F));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        listScroll -= (int) Math.signum(sy);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ZClientEvents.QUEST_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Самотест: выбрать главу и задание. */
    public void select(String ch, String quest) {
        chapter = ch;
        selected = quest;
        refreshButtons();
    }
}
