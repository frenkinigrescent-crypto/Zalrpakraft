package dev.zalrpakraft.client;

import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.data.ProgressionMap;
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
            case 105 -> {
                ConstellationScreen.showAll = true;
                CompoundTag t = new CompoundTag();
                ListTag l = new ListTag();
                for (String s : new String[]{"home", "g_cradle", "p_farmersdelight", "p_gpr", "p_naturalist", "p_starcatcher",
                        "g_gear", "p_create", "p_copycats", "g_threat", "p_creepers", "m_wroughtnaut", "m_rise", "gate_nether",
                        "g_mystery", "g_arsenal", "m_frostmaw"})
                    l.add(StringTag.valueOf(s));
                t.put("studied", l);
                mc.setScreen(new ConstellationScreen(ProgressionMap.rawJson(), t));
            }
            case 150 -> shot(mc, "zk_2_map.png");
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
            case 295 -> {
                // листаем на 2-ю страницу — там координаты
                if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen b) b.setPage(1);
            }
            case 305 -> shot(mc, "zk_6_journal.png");
            case 320 -> {
                Zalrpakraft.LOG.info("Zalepacraft clienttest: DONE");
                mc.stop();
            }
            default -> {}
        }
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
