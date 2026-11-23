package dev.zalrpakraft;

import com.mojang.logging.LogUtils;
import dev.zalrpakraft.data.BossTable;
import dev.zalrpakraft.data.ProgressionMap;
import dev.zalrpakraft.event.CommonEvents;
import dev.zalrpakraft.net.ZNetwork;
import dev.zalrpakraft.registry.ZAttachments;
import dev.zalrpakraft.registry.ZBlocks;
import dev.zalrpakraft.registry.ZCreativeTab;
import dev.zalrpakraft.registry.ZItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Zalrpakraft.MODID)
public class Zalrpakraft {
    public static final String MODID = "zalrpakraft";
    public static final Logger LOG = LogUtils.getLogger();

    public Zalrpakraft(IEventBus modBus, ModContainer container) {
        ZBlocks.BLOCKS.register(modBus);
        ZItems.ITEMS.register(modBus);
        ZCreativeTab.TABS.register(modBus);
        ZAttachments.ATTACHMENTS.register(modBus);
        modBus.addListener(ZNetwork::register);

        container.registerConfig(ModConfig.Type.COMMON, ZConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, dev.zalrpakraft.client.ZClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, ZServerConfig.SPEC);

        BossTable.load();
        ProgressionMap.load();
        dev.zalrpakraft.data.QuestBook.load();

        NeoForge.EVENT_BUS.register(CommonEvents.class);
        NeoForge.EVENT_BUS.register(dev.zalrpakraft.world.DepthDifficulty.class);
        LOG.info("Zalepacraft: загружен");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
