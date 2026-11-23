package dev.zalrpakraft.client;

import dev.zalrpakraft.Zalrpakraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;

/**
 * Клиентский самотест для CI (-Dzalrpakraft.clienttest=1): открывает главное меню, звёздную карту,
 * плашки, кадр пробуждения и дневник, делает скриншоты в screenshots/ и закрывает игру.
 */
public final class ClientSelfTest {
    public static final boolean ENABLED = !System.getProperty("zalrpakraft.clienttest", "").isEmpty();
    private static int ticks = -1;

    public static void tick() {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (ticks < 0) {
            if (mc.screen instanceof ZTitleScreen) ticks = 0;
            return;
        }
        ticks++;
        switch (ticks) {
            case 100 -> shot(mc, "zk_1_menu.png");
            case 155 -> {
                PlaqueOverlay.push("table", "zalrpakraft:star_table", "goal");
                mc.setScreen(new Showcase(0));
            }
            case 165 -> shot(mc, "zk_3_plaque_goal.png");
            case 170 -> mc.setScreen(new Showcase(1));
            case 200 -> shot(mc, "zk_4_plaque_challenge.png");
            case 265 -> {
                IntroController.freezeAt(88);
                mc.setScreen(new Showcase(2));
            }
            case 275 -> shot(mc, "zk_5_intro.png");
            case 280 -> {
                IntroController.freezeAt(-1);
                ClientState.housePos = new net.minecraft.core.BlockPos(-42, 78, 83);
                mc.setScreen(JournalScreen.create());
            }
            case 288 -> { if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen b) b.setPage(1); }
            case 291 -> shot(mc, "zk_6a_journal.png");
            case 293 -> { if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen b) b.setPage(2); }
            case 296 -> shot(mc, "zk_6b_journal.png");
            case 298 -> { if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen b) b.setPage(4); }
            case 301 -> shot(mc, "zk_6c_journal.png");
            case 303 -> { if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen b) b.setPage(5); }
            case 306 -> shot(mc, "zk_6d_journal.png");
            case 310 -> {
                CompoundTag st = new CompoundTag();
                CompoundTag qs = new CompoundTag();
                dev.zalrpakraft.data.QuestBook.Book b = dev.zalrpakraft.data.QuestBook.fromJson(dev.zalrpakraft.data.QuestBook.json());
                int i = 0;
                for (dev.zalrpakraft.data.QuestBook.Quest q : b.quests) {
                    CompoundTag t = new CompoundTag();
                    int s2 = q.chapter.equals("prologue") ? (i < 2 ? 3 : i == 2 ? 2 : 1) : (q.requires.isEmpty() ? 1 : 0);
                    t.putInt("s", s2);
                    int[] p = new int[q.tasks.size()];
                    for (int k = 0; k < p.length; k++) p[k] = s2 >= 2 ? q.tasks.get(k).count : Math.min(q.tasks.get(k).count, 7);
                    t.putIntArray("p", p);
                    qs.put(q.id, t);
                    if (q.chapter.equals("prologue")) i++;
                }
                st.put("quests", qs);
                st.putString("tracked", "pr_mapkey");
                QuestBookScreen qb = new QuestBookScreen(dev.zalrpakraft.data.QuestBook.json(), st);
                mc.setScreen(qb);
                qb.select("prologue", "pr_mapkey");
            }
            case 330 -> shot(mc, "zk_7_quests.png");
            case 340 -> mc.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(new ZTitleScreen()));
            case 400 -> shot(mc, "zk_8_worlds.png");
            case 405 -> net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc, mc.screen);
            case 450 -> shot(mc, "zk_9_create.png");
            case 455 -> {
                mc.setScreen(new ZTitleScreen());
                loadWorld(mc, "zk_test");
            }
            default -> {}
        }
        // в мире: ждём игрока, настраиваем прогресс и снимаем все три уровня карты
        if (ticks > 455 && mc.player != null && mc.screen == null && worldTick < 0) worldTick = 0;
        if (worldTick >= 0) {
            worldTick++;
            switch (worldTick) {
                case 80 -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.zalrpakraft.net.SimplePayload("selftest_setup"));
                case 140 -> shot(mc, "zk_10_galaxy.png");
                case 145 -> {
                    if (mc.screen instanceof StarMapScreen sm && sm.biggestSystem() != null) sm.requestSystem(sm.biggestSystem());
                }
                case 200 -> shot(mc, "zk_11_system.png");
                case 205 -> {
                    if (mc.screen instanceof StarMapScreen sm) sm.enterPlanet(0);
                }
                case 245 -> shot(mc, "zk_12_planet.png");
                case 250 -> {
                    if (mc.screen instanceof StarMapScreen sm) sm.enterPlanet(1);
                }
                case 290 -> shot(mc, "zk_13_planet2.png");
                case 300 -> {
                    Zalrpakraft.LOG.info("Zalepacraft clienttest: DONE");
                    mc.stop();
                }
                default -> {}
            }
        }
    }

    private static int worldTick = -1;

    private static void loadWorld(Minecraft mc, String id) {
        Object flows = mc.createWorldOpenFlows();
        Runnable fail = () -> Zalrpakraft.LOG.error("Zalepacraft clienttest: не удалось открыть мир");
        for (String name : new String[]{"openWorld", "checkForBackupAndLoad", "loadLevel"}) {
            for (java.lang.reflect.Method m : flows.getClass().getMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (m.getName().equals(name) && pt.length == 2 && pt[0] == String.class && pt[1] == Runnable.class) {
                    try {
                        m.invoke(flows, id, fail);
                        Zalrpakraft.LOG.info("Zalepacraft clienttest: открываю мир через {}", name);
                        return;
                    } catch (Exception e) {
                        Zalrpakraft.LOG.error("Zalepacraft clienttest: {}", name, e);
                    }
                }
            }
        }
        Zalrpakraft.LOG.error("Zalepacraft clienttest: нет подходящего метода WorldOpenFlows");
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> Zalrpakraft.LOG.info("Zalepacraft clienttest: {}", c.getString()));
    }

    /** Экран-витрина: звёздный фон меню + плашка / задание / кадр пробуждения. */
    private static final class Showcase extends Screen {
        private final int mode;
        private final ZTitleScreen bg = new ZTitleScreen();
        private final long opened = System.currentTimeMillis();

        Showcase(int mode) {
            super(Component.empty());
            this.mode = mode;
        }

        @Override
        protected void init() {
            bg.init(minecraft, width, height);
            if (mode == 1) {
                PlaqueOverlay.clear();
                PlaqueOverlay.push("twelve_eyes", "zalrpakraft:eye_stars", "challenge");
            }
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            bg.renderBackground(g, -1, -1, pt);
            if (mode == 2) {
                IntroOverlay.render(g, minecraft.getTimer());
                return;
            }
            float since = (System.currentTimeMillis() - opened) / 50f;
            QuestHud.draw(g, Component.literal("Ferrous Wroughtnaut"),
                    Component.literal("Подземные залы. Даёт Ржавое ядро — ключ в Ад и Глаз Ржавчины"), since);
            PlaqueOverlay.render(g, minecraft.getTimer());
        }
    }

    private ClientSelfTest() {}
}
