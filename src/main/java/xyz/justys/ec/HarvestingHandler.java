package xyz.justys.ec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HarvestingHandler {
    private static final int[][] NEIGHBOR_OFFSETS = createNeighborOffsets();
    private static final Set<UUID> ACTIVE_PLAYERS = new HashSet<>();

    private HarvestingHandler() {
    }

    public static void handleBlockBreak(ServerPlayerInteractionManager manager,
                                        ServerWorld world,
                                        ServerPlayerEntity player,
                                        BlockPos origin,
                                        BlockState originalState,
                                        ModConfig config,
                                        Logger logger) {
        if (world.isClient() || originalState == null || originalState.isAir()) {
            return;
        }

        UUID playerId = player.getUuid();
        if (!ACTIVE_PLAYERS.add(playerId)) {
            return;
        }

        try {
            if (shouldTriggerTreeFeller(player, originalState, config)) {
                Set<BlockPos> treeBlocks = collectConnectedBlocks(world, origin, originalState, config.getMaxTreeBlocks());
                Box searchBox = createSearchBox(origin, treeBlocks).expand(1.5);
                Set<UUID> existingItemIds = snapshotItemIds(world, searchBox);
                breakCollectedBlocks(manager, treeBlocks, logger);
                replantSapling(world, origin, originalState, treeBlocks);
                gatherDrops(world, origin, searchBox, existingItemIds);
                return;
            }

            if (shouldTriggerVeinMiner(player, originalState, config)) {
                Set<BlockPos> veinBlocks = collectConnectedBlocks(world, origin, originalState, config.getMaxVeinBlocks());
                Box searchBox = createSearchBox(origin, veinBlocks).expand(1.5);
                Set<UUID> existingItemIds = snapshotItemIds(world, searchBox);
                breakCollectedBlocks(manager, veinBlocks, logger);
                gatherDrops(world, origin, searchBox, existingItemIds);
            }
        } finally {
            ACTIVE_PLAYERS.remove(playerId);
        }
    }

    private static void breakCollectedBlocks(ServerPlayerInteractionManager manager, Set<BlockPos> blocks, Logger logger) {
        for (BlockPos pos : blocks) {
            try {
                manager.tryBreakBlock(pos);
            } catch (Exception e) {
                logger.error("Failed to break chained harvest block at {}.", pos, e);
                break;
            }
        }
    }

    private static Set<UUID> snapshotItemIds(ServerWorld world, Box searchBox) {
        Set<UUID> ids = new HashSet<>();
        for (ItemEntity itemEntity : world.getEntitiesByClass(ItemEntity.class, searchBox, entity -> entity.isAlive())) {
            ids.add(itemEntity.getUuid());
        }
        return ids;
    }

    private static void gatherDrops(ServerWorld world, BlockPos origin, Box searchBox, Set<UUID> existingItemIds) {
        Vec3d target = Vec3d.ofCenter(origin);
        for (ItemEntity itemEntity : world.getEntitiesByClass(ItemEntity.class, searchBox, entity -> entity.isAlive())) {
            if (existingItemIds.contains(itemEntity.getUuid())) {
                continue;
            }
            itemEntity.refreshPositionAndAngles(target.x, target.y, target.z, itemEntity.getYaw(), itemEntity.getPitch());
            itemEntity.setVelocity(Vec3d.ZERO);
            itemEntity.velocityModified = true;
        }
    }

    private static Box createSearchBox(BlockPos origin, Set<BlockPos> blocks) {
        int minX = origin.getX();
        int minY = origin.getY();
        int minZ = origin.getZ();
        int maxX = origin.getX();
        int maxY = origin.getY();
        int maxZ = origin.getZ();

        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private static void replantSapling(ServerWorld world, BlockPos origin, BlockState originalState, Set<BlockPos> brokenBlocks) {
        Block saplingBlock = saplingFor(originalState);
        if (saplingBlock == null || brokenBlocks.isEmpty()) {
            return;
        }

        Set<BlockPos> plantPositions = findPlantPositions(origin, originalState, brokenBlocks);
        if (plantPositions.isEmpty()) {
            return;
        }

        for (BlockPos plantPos : plantPositions) {
            if (!world.getBlockState(plantPos).isAir()) {
                return;
            }

            BlockPos soilPos = plantPos.down();
            if (!Block.sideCoversSmallSquare(world, soilPos, net.minecraft.util.math.Direction.UP)) {
                return;
            }

            if (!saplingBlock.getDefaultState().canPlaceAt(world, plantPos)) {
                return;
            }
        }

        for (BlockPos plantPos : plantPositions) {
            world.setBlockState(plantPos, saplingBlock.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private static Set<BlockPos> findPlantPositions(BlockPos origin, BlockState originalState, Set<BlockPos> brokenBlocks) {
        BlockPos best = origin;
        for (BlockPos pos : brokenBlocks) {
            if (pos.getY() < best.getY()
                    || (pos.getY() == best.getY() && pos.getX() < best.getX())
                    || (pos.getY() == best.getY() && pos.getX() == best.getX() && pos.getZ() < best.getZ())) {
                best = pos;
            }
        }

        if (requiresTwoByTwoReplant(originalState, brokenBlocks, best)) {
            Set<BlockPos> positions = new LinkedHashSet<>();
            positions.add(best);
            positions.add(best.east());
            positions.add(best.south());
            positions.add(best.south().east());
            return positions;
        }

        return Set.of(new BlockPos(best.getX(), best.getY(), best.getZ()));
    }

    private static boolean requiresTwoByTwoReplant(BlockState originalState, Set<BlockPos> brokenBlocks, BlockPos best) {
        String path = blockPath(originalState);
        if (!path.equals("dark_oak_log") && !path.equals("jungle_log")) {
            return false;
        }

        int baseY = best.getY();
        Set<BlockPos> baseLayer = new HashSet<>();
        for (BlockPos pos : brokenBlocks) {
            if (pos.getY() == baseY) {
                baseLayer.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
            }
        }

        return baseLayer.contains(best)
                && baseLayer.contains(best.east())
                && baseLayer.contains(best.south())
                && baseLayer.contains(best.south().east());
    }

    private static Block saplingFor(BlockState state) {
        String path = blockPath(state);
        return switch (path) {
            case "oak_log" -> Blocks.OAK_SAPLING;
            case "birch_log" -> Blocks.BIRCH_SAPLING;
            case "spruce_log" -> Blocks.SPRUCE_SAPLING;
            case "jungle_log" -> Blocks.JUNGLE_SAPLING;
            case "acacia_log" -> Blocks.ACACIA_SAPLING;
            case "dark_oak_log" -> Blocks.DARK_OAK_SAPLING;
            case "cherry_log" -> Blocks.CHERRY_SAPLING;
            case "mangrove_log" -> Blocks.MANGROVE_PROPAGULE;
            case "pale_oak_log" -> Blocks.PALE_OAK_SAPLING;
            default -> null;
        };
    }

    private static Set<BlockPos> collectConnectedBlocks(ServerWorld world, BlockPos origin, BlockState originalState, int limit) {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> matches = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && matches.size() < limit) {
            BlockPos current = queue.removeFirst();
            for (int[] offset : NEIGHBOR_OFFSETS) {
                BlockPos next = current.add(offset[0], offset[1], offset[2]);
                if (!visited.add(next)) {
                    continue;
                }

                BlockState candidate = world.getBlockState(next);
                if (!matchesFamily(originalState, candidate)) {
                    continue;
                }

                BlockPos immutablePos = next.toImmutable();
                matches.add(immutablePos);
                if (matches.size() >= limit) {
                    break;
                }
                queue.addLast(immutablePos);
            }
        }

        return matches;
    }

    private static boolean shouldTriggerTreeFeller(ServerPlayerEntity player, BlockState state, ModConfig config) {
        if (!config.isTreeFellerEnabled() || !isTreeMaterial(state)) {
            return false;
        }
        if (config.isTreeFellerRequireSneak() && !player.isSneaking()) {
            return false;
        }
        return !config.isTreeFellerRequireAxe() || isHoldingAxe(player);
    }

    private static boolean shouldTriggerVeinMiner(ServerPlayerEntity player, BlockState state, ModConfig config) {
        if (!config.isVeinMinerEnabled() || !isVeinMaterial(state)) {
            return false;
        }
        if (config.isVeinMinerRequireSneak() && !player.isSneaking()) {
            return false;
        }
        if (config.isVeinMinerRequirePickaxe() && !isHoldingPickaxe(player)) {
            return false;
        }

        ItemStack stack = player.getMainHandStack();
        return !state.isToolRequired() || stack.isSuitableFor(state);
    }

    private static boolean matchesFamily(BlockState originalState, BlockState candidateState) {
        if (candidateState.isAir()) {
            return false;
        }
        return originalState.getBlock() == candidateState.getBlock();
    }

    private static boolean isTreeMaterial(BlockState state) {
        return state.isIn(BlockTags.LOGS) || isNetherStem(state) || isHugeMushroomBlock(state);
    }

    private static boolean isVeinMaterial(BlockState state) {
        String path = blockPath(state);
        return path.endsWith("_ore") || path.equals("ancient_debris");
    }

    private static boolean isNetherStem(BlockState state) {
        String path = blockPath(state);
        return path.equals("crimson_stem") || path.equals("warped_stem");
    }

    private static boolean isHugeMushroomBlock(BlockState state) {
        String path = blockPath(state);
        return path.equals("mushroom_stem") || path.endsWith("_mushroom_block");
    }

    private static String blockPath(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getPath();
    }

    private static boolean isHoldingAxe(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        return stack.getItem() instanceof AxeItem;
    }

    private static boolean isHoldingPickaxe(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        return stack.getItem() instanceof PickaxeItem;
    }

    private static int[][] createNeighborOffsets() {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    offsets.add(new int[]{x, y, z});
                }
            }
        }
        return offsets.toArray(int[][]::new);
    }
}
