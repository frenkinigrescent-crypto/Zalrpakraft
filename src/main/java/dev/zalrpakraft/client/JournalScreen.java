package dev.zalrpakraft.client;

import dev.zalrpakraft.net.SimplePayload;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Дневник незнакомца. При закрытии сообщает серверу, что дневник прочитан. */
public class JournalScreen extends BookViewScreen {
    private static final int PAGES = 11;

    private JournalScreen(BookAccess access) {
        super(access);
    }

    public static JournalScreen create() {
        BlockPos h = ClientState.housePos;
        String x = h == null ? "?" : String.valueOf(h.getX());
        String y = h == null ? "?" : String.valueOf(h.getY());
        String z = h == null ? "?" : String.valueOf(h.getZ());
        List<Component> pages = new ArrayList<>();
        for (int i = 1; i <= PAGES; i++) pages.add(Component.translatable("zalrpakraft.journal.p" + i, x, y, z));
        return new JournalScreen(new BookAccess(pages));
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new SimplePayload("journal_read"));
        super.onClose();
    }
}
