/*
 * Copyright (C) 2026 MarbleGate
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.marblegate.letemburn.gametest.draconic;

import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable, raw-bit snapshot of every observable ProcessExplosion field. */
public record ProcessExplosionState(
        long originXBits,
        long originYBits,
        long originZBits,
        String dimension,
        boolean serverMatchesLevel,
        int minimumDelay,
        List<Long> angularResistanceBits,
        boolean dead,
        int radius,
        int maxRadius,
        long circumferenceBits,
        long meanResistanceBits,
        boolean effectEnabled,
        boolean calculationComplete,
        boolean detonated,
        long startTime,
        long calculationWait,
        boolean lavaEnabled,
        List<Long> blocksToUpdate,
        List<List<Long>> destroyedBlocks,
        List<Long> lavaPositions,
        List<Long> destroyedCache,
        List<Long> scannedCache,
        long mutablePosition,
        boolean progressMonitorPresent,
        String lavaState) {
    public static ProcessExplosionState capture(
            ProcessExplosion explosion,
            ServerLevel level,
            MinecraftServer server,
            int minimumDelay,
            boolean calculationComplete,
            boolean detonated,
            long startTime,
            long calculationWait,
            BlockState lavaState) {
        return new ProcessExplosionState(
                Double.doubleToRawLongBits(explosion.origin.x),
                Double.doubleToRawLongBits(explosion.origin.y),
                Double.doubleToRawLongBits(explosion.origin.z),
                level.dimension().location().toString(),
                server == level.getServer(),
                minimumDelay,
                rawBits(explosion.angularResistance),
                explosion.isDead,
                explosion.radius,
                explosion.maxRadius,
                Double.doubleToRawLongBits(explosion.circumference),
                Double.doubleToRawLongBits(explosion.meanResistance),
                explosion.enableEffect,
                calculationComplete,
                detonated,
                startTime,
                calculationWait,
                explosion.lava,
                List.copyOf(explosion.blocksToUpdate),
                explosion.destroyedBlocks.stream().map(List::copyOf).toList(),
                List.copyOf(explosion.lavaPositions),
                List.copyOf(explosion.destroyedCache),
                List.copyOf(explosion.scannedCache),
                explosion.mPos.asLong(),
                explosion.progressMon != null,
                lavaState.toString());
    }

    private static List<Long> rawBits(double[] values) {
        return java.util.Arrays.stream(values)
                .mapToObj(Double::doubleToRawLongBits)
                .toList();
    }
}
