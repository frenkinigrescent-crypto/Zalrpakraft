package dev.zalrpakraft.registry;

import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.block.StarTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ZBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Zalrpakraft.MODID);

    public static final DeferredBlock<Block> STAR_TABLE = BLOCKS.register("star_table",
            () -> new StarTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.WOOD)
                    .lightLevel(s -> 7)
                    .noOcclusion()));

    private ZBlocks() {}
}
