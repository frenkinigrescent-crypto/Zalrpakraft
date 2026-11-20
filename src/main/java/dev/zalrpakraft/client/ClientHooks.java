package dev.zalrpakraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Точки входа с общей стороны на клиентскую. Вызываются только на клиенте. */
public final class ClientHooks {
    public static void onSync(CompoundTag t) {
        ClientState.progress = t;
        if (t.contains("houseX")) ClientState.housePos = new BlockPos(t.getInt("houseX"), t.getInt("houseY"), t.getInt("houseZ"));
        String title = t.getString("questTitle");
        String text = t.getString("questText");
        boolean changed = !title.equals(ClientState.questTitle) || !text.equals(ClientState.questText);
        ClientState.questTitle = title;
        ClientState.questText = text;
        if (changed) {
            Minecraft mc = Minecraft.getInstance();
            ClientState.questChangedAt = mc.level == null ? 0 : mc.level.getGameTime();
            if (!IntroController.active() && mc.player != null)
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.8F));
        }
    }

    public static void openMap(String json, CompoundTag progress) {
        ClientState.progress = progress;
        Minecraft.getInstance().setScreen(new ConstellationScreen(json, progress));
    }

    public static void openJournal() {
        Minecraft.getInstance().setScreen(JournalScreen.create());
    }

    public static void onSignal(String action) {
        if ("intro".equals(action)) IntroController.start();
    }

    private ClientHooks() {}
}
