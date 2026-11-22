package dev.zalrpakraft.client;

import dev.zalrpakraft.Zalrpakraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class ZClientEvents {
    public static final net.minecraft.client.KeyMapping QUEST_KEY = new net.minecraft.client.KeyMapping(
            "key.zalrpakraft.quests", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_J, "key.categories.zalrpakraft");
    public static final net.minecraft.client.KeyMapping MAP_KEY = new net.minecraft.client.KeyMapping(
            "key.zalrpakraft.map", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_K, "key.categories.zalrpakraft");

    @EventBusSubscriber(modid = Zalrpakraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                if (ZClientConfig.WINDOW_ICON.get()) WindowIcon.apply();
            });
        }

        @SubscribeEvent
        public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) {
            e.register(QUEST_KEY);
            e.register(MAP_KEY);
        }

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAboveAll(Zalrpakraft.id("quest_hud"), QuestHud::render);
            e.registerAboveAll(Zalrpakraft.id("plaque"), PlaqueOverlay::render);
            e.registerAboveAll(Zalrpakraft.id("intro"), IntroOverlay::render);
        }
    }

    @EventBusSubscriber(modid = Zalrpakraft.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        public static void screen(net.neoforged.neoforge.client.event.ScreenEvent.Opening e) {
            if (e.getNewScreen() instanceof net.minecraft.client.gui.screens.TitleScreen && ZClientConfig.CUSTOM_MENU.get())
                e.setNewScreen(new ZTitleScreen());
        }

        /** Экраны меню (вне мира): звёздное небо поверх стандартной панорамы. */
        @SubscribeEvent
        public static void background(net.neoforged.neoforge.client.event.ScreenEvent.BackgroundRendered e) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null || !ZClientConfig.STAR_BACKGROUNDS.get()) return;
            var sc = e.getScreen();
            StarSky.render(e.getGuiGraphics(), sc.width, sc.height, -1, -1);
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            IntroController.clientTick();
            ClientSelfTest.tick();
            while (MAP_KEY.consumeClick()) {
                if (net.minecraft.client.Minecraft.getInstance().player != null && net.minecraft.client.Minecraft.getInstance().screen == null)
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.zalrpakraft.net.SimplePayload("open_map"));
            }
            while (QUEST_KEY.consumeClick()) {
                if (net.minecraft.client.Minecraft.getInstance().player != null && net.minecraft.client.Minecraft.getInstance().screen == null)
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.zalrpakraft.net.SimplePayload("open_quests"));
            }
        }

        @SubscribeEvent
        public static void camera(ViewportEvent.ComputeCameraAngles e) {
            if (!IntroController.active()) return;
            float t = IntroController.time((float) e.getPartialTick());
            float w = IntroController.cameraWeight(t);
            e.setPitch(Mth.lerp(w, e.getPitch(), IntroController.pitch(t)));
            e.setRoll(Mth.lerp(w, e.getRoll(), IntroController.roll(t)));
        }

        @SubscribeEvent
        public static void freeze(MovementInputUpdateEvent e) {
            if (!IntroController.active() || IntroController.time(0) > 185) return;
            var in = e.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }
    }

    private ZClientEvents() {}
}
