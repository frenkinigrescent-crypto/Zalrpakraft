package dev.zalrpakraft.registry;

import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.item.DimensionKeyItem;
import dev.zalrpakraft.item.EndEyeItem;
import dev.zalrpakraft.item.RelicItem;
import dev.zalrpakraft.item.StrangerJournalItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ZItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Zalrpakraft.MODID);

    public static final DeferredItem<Item> STRANGER_JOURNAL = ITEMS.register("stranger_journal",
            () -> new StrangerJournalItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<BlockItem> STAR_TABLE = ITEMS.registerSimpleBlockItem("star_table", ZBlocks.STAR_TABLE,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredItem<Item> NETHER_KEY = ITEMS.register("nether_key",
            () -> new DimensionKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "minecraft:the_nether"));
    public static final DeferredItem<Item> PARADISE_KEY = ITEMS.register("paradise_key",
            () -> new DimensionKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "paradise_lost:paradise_lost"));

    /** Трофеи с боссов (метеоров). */
    public static final String[] RELICS = {
            "rust_core", "frost_heart", "sun_feather", "sculptor_chisel",
            "cornelia_lantern", "maze_pearl", "ignis_ember", "revenant_ash",
            "forge_heart", "leviathan_scale", "remnant_sand", "harbinger_gear",
            "guardian_shard", "qliphoth_fragment", "crimson_sigil", "rise_trophy", "meteor_shard",
            "eldritch_core", "warden_ward", "taint_heart", "hierophant_eye"
    };
    public static final Map<String, DeferredItem<Item>> RELIC_ITEMS = new LinkedHashMap<>();

    /** 12 глаз для портала в Энд. */
    public static final String[] EYES = {
            "eye_rust", "eye_frost", "eye_sun", "eye_sculptor",
            "eye_abyss", "eye_flame", "eye_forge", "eye_depths",
            "eye_sands", "eye_mechanism", "eye_undying", "eye_stars"
    };
    public static final List<DeferredItem<Item>> EYE_ITEMS = new ArrayList<>();

    static {
        for (String r : RELICS) {
            RELIC_ITEMS.put(r, ITEMS.register(r, () -> new RelicItem(new Item.Properties().rarity(Rarity.RARE).fireResistant())));
        }
        for (int i = 0; i < EYES.length; i++) {
            final int idx = i;
            EYE_ITEMS.add(ITEMS.register(EYES[i], () -> new EndEyeItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), idx)));
        }
    }

    private ZItems() {}
}
