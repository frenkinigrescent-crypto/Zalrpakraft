package dev.zalrpakraft.registry;

import dev.zalrpakraft.Zalrpakraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ZCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Zalrpakraft.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.zalrpakraft"))
            .icon(() -> ZItems.EYE_ITEMS.get(11).get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(ZItems.STRANGER_JOURNAL.get());
                out.accept(ZItems.STAR_TABLE.get());
                out.accept(ZItems.NETHER_KEY.get());
                out.accept(ZItems.PARADISE_KEY.get());
                for (DeferredItem<Item> r : ZItems.RELIC_ITEMS.values()) out.accept(r.get());
                for (DeferredItem<Item> e : ZItems.EYE_ITEMS) out.accept(e.get());
            })
            .build());

    private ZCreativeTab() {}
}
