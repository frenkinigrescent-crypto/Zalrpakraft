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

    @EventBusSubscriber(modid = Zalrpakraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                if (ZClientConfig.WINDOW_ICON.get()) WindowIcon.apply();
            });
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

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            IntroController.clientTick();
            ClientSelfTest.tick();
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
