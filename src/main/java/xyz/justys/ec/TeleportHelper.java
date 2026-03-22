package xyz.justys.ec;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public final class TeleportHelper {
    private TeleportHelper() {
    }

    public static Optional<Vec3d> findSafeTeleportTarget(ServerWorld world, BlockPos targetPos) {
        for (int radius = 0; radius <= 3; radius++) {
            for (int yOffset = -3; yOffset <= 4; yOffset++) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (radius > 0 && Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
                            continue;
                        }

                        BlockPos candidate = targetPos.add(xOffset, yOffset, zOffset);
                        if (isSafeTeleportPos(world, candidate)) {
                            return Optional.of(Vec3d.ofBottomCenter(candidate));
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isSafeTeleportPos(ServerWorld world, BlockPos feetPos) {
        BlockPos headPos = feetPos.up();
        BlockPos groundPos = feetPos.down();

        var feetState = world.getBlockState(feetPos);
        var headState = world.getBlockState(headPos);
        var groundState = world.getBlockState(groundPos);

        if (!feetState.isAir() || !headState.isAir()) {
            return false;
        }
        if (!groundState.isSideSolidFullSquare(world, groundPos, Direction.UP)) {
            return false;
        }
        if (groundState.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
                || groundState.isOf(net.minecraft.block.Blocks.CAMPFIRE)
                || groundState.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE)
                || groundState.isOf(net.minecraft.block.Blocks.CACTUS)) {
            return false;
        }

        return true;
    }
}
