package dev.zalrpakraft.world;

import dev.zalrpakraft.Zalrpakraft;
import dev.zalrpakraft.ZConfig;
import dev.zalrpakraft.registry.ZBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/**
 * Домик незнакомца: небольшая заброшенная хижина 7×7 недалеко от спавна,
 * в центре — Звёздный стол. Строится кодом, без NBT-структур.
 */
public final class HouseBuilder {
    private static final int HALF = 3; // 7×7

    public static void ensureBuilt(ServerLevel level) {
        WorldState st = WorldState.get(level.getServer());
        if (st.houseBuilt) return;
        BlockPos spawn = level.getSharedSpawnPos();
        RandomSource rnd = RandomSource.create(level.getSeed() ^ 0x5A17L);
        BlockPos chosen = null;
        int min = ZConfig.HOUSE_MIN_DIST.get();
        int max = Math.max(min + 1, ZConfig.HOUSE_MAX_DIST.get());
        for (int attempt = 0; attempt < 40 && chosen == null; attempt++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            int d = min + rnd.nextInt(max - min);
            int x = spawn.getX() + (int) (Math.cos(a) * d);
            int z = spawn.getZ() + (int) (Math.sin(a) * d);
            level.getChunk(x >> 4, z >> 4); // сгенерировать чанк
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y - 1, z);
            if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()) continue;
            if (!level.getBlockState(ground).isSolid()) continue;
            if (!flatEnough(level, x, z, y)) continue;
            chosen = new BlockPos(x, y, z);
        }
        if (chosen == null) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX() + min, spawn.getZ());
            chosen = new BlockPos(spawn.getX() + min, Math.max(y, level.getSeaLevel() + 1), spawn.getZ());
        }
        build(level, chosen);
        st.housePos = chosen;
        st.tablePos = chosen.above();
        st.houseBuilt = true;
        st.setDirty();
        Zalrpakraft.LOG.info("Zalepacraft: домик незнакомца построен на {}", chosen);
    }

    private static boolean flatEnough(ServerLevel level, int cx, int cz, int y) {
        for (int dx = -HALF; dx <= HALF; dx += HALF) {
            for (int dz = -HALF; dz <= HALF; dz += HALF) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz);
                if (Math.abs(h - y) > 3) return false;
            }
        }
        return true;
    }

    /** floor — уровень пола (на этой высоте лежат доски). */
    private static void build(ServerLevel level, BlockPos floor) {
        int fx = floor.getX(), fy = floor.getY(), fz = floor.getZ();
        // 1. Расчистка и фундамент
        for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
            for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
                for (int dy = 1; dy <= 7; dy++) set(level, fx + dx, fy + dy, fz + dz, Blocks.AIR.defaultBlockState());
                boolean inside = Math.abs(dx) <= HALF && Math.abs(dz) <= HALF;
                if (inside) {
                    for (int dy = -1; dy >= -6; dy--) {
                        BlockPos p = new BlockPos(fx + dx, fy + dy, fz + dz);
                        BlockState s = level.getBlockState(p);
                        if (s.isSolid() && level.getFluidState(p).isEmpty()) break;
                        set(level, p, Blocks.COBBLESTONE.defaultBlockState());
                    }
                }
            }
        }
        RandomSource r = RandomSource.create(floor.asLong());
        // 2. Пол
        for (int dx = -HALF; dx <= HALF; dx++)
            for (int dz = -HALF; dz <= HALF; dz++) {
                boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                set(level, fx + dx, fy, fz + dz, edge ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                        : (r.nextInt(5) == 0 ? Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState()));
            }
        // 3. Стены, углы — брёвна
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = -HALF; dx <= HALF; dx++)
                for (int dz = -HALF; dz <= HALF; dz++) {
                    boolean corner = Math.abs(dx) == HALF && Math.abs(dz) == HALF;
                    boolean wall = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                    if (!wall) continue;
                    BlockState s;
                    if (corner) s = Blocks.SPRUCE_LOG.defaultBlockState();
                    else if (dy == 2 && (Math.abs(dx) == 1 || Math.abs(dz) == 1) && !(dz == HALF && dx == 0)) s = Blocks.GLASS_PANE.defaultBlockState();
                    else s = r.nextInt(6) == 0 ? Blocks.STRIPPED_OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
                    set(level, fx + dx, fy + dy, fz + dz, s);
                }
        }
        // 4. Дверь на южной стороне (+Z)
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        set(level, fx, fy + 1, fz + HALF, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, fx, fy + 2, fz + HALF, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // 5. Крыша — ступенчатая пирамида из еловых ступенек
        for (int layer = 0; layer <= HALF + 1; layer++) {
            int y = fy + 4 + layer;
            int ext = HALF + 1 - layer;
            for (int dx = -ext; dx <= ext; dx++)
                for (int dz = -ext; dz <= ext; dz++) {
                    boolean ring = Math.abs(dx) == ext || Math.abs(dz) == ext;
                    if (!ring && ext > 0) continue;
                    BlockState s;
                    if (ext == 0) s = Blocks.SPRUCE_PLANKS.defaultBlockState();
                    else if (Math.abs(dx) == ext && Math.abs(dz) == ext) s = Blocks.SPRUCE_SLAB.defaultBlockState();
                    else {
                        Direction facing = dz == -ext ? Direction.SOUTH : dz == ext ? Direction.NORTH : dx == -ext ? Direction.EAST : Direction.WEST;
                        s = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
                    }
                    set(level, fx + dx, y, fz + dz, s);
                }
            // перекрытие под крышей
            if (layer == 0) for (int dx = -HALF + 1; dx <= HALF - 1; dx++)
                for (int dz = -HALF + 1; dz <= HALF - 1; dz++)
                    set(level, fx + dx, fy + 4, fz + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
        // 6. Интерьер
        set(level, floor.above(), ZBlocks.STAR_TABLE.get().defaultBlockState());
        set(level, fx - 2, fy + 1, fz - 2, Blocks.BOOKSHELF.defaultBlockState());
        set(level, fx - 1, fy + 1, fz - 2, Blocks.BOOKSHELF.defaultBlockState());
        set(level, fx - 2, fy + 2, fz - 2, Blocks.COBWEB.defaultBlockState());
        set(level, fx + 2, fy + 1, fz - 2, Blocks.BARREL.defaultBlockState());
        set(level, fx + 2, fy + 3, fz + 2, Blocks.COBWEB.defaultBlockState());
        set(level, fx - 2, fy + 3, fz + 1, Blocks.COBWEB.defaultBlockState());
        set(level, fx, fy + 3, fz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(level, fx + 2, fy + 1, fz + 1, Blocks.CANDLE.defaultBlockState());
        BlockPos chestPos = new BlockPos(fx - 2, fy + 1, fz + 2);
        set(level, chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.BREAD, 6));
            chest.setItem(1, new ItemStack(Items.TORCH, 16));
            chest.setItem(3, new ItemStack(Items.MAP));
            chest.setItem(4, new ItemStack(Items.COMPASS));
            chest.setItem(13, new ItemStack(Items.STONE_PICKAXE));
            chest.setItem(22, new ItemStack(Items.ENDER_PEARL, 2));
        }
        // 7. Дорожка к двери
        for (int i = 1; i <= 3; i++) {
            int x = fx, z = fz + HALF + i;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (level.getFluidState(new BlockPos(x, y, z)).is(Fluids.WATER)) break;
            set(level, x, y, z, Blocks.DIRT_PATH.defaultBlockState());
        }
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        set(l, new BlockPos(x, y, z), s);
    }

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, 2);
    }

    private HouseBuilder() {}
}
