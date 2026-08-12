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

package dev.marblegate.letemburn.compat.vanilla;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class VanillaTntImpactAudit {
    private static final Map<BlockPos, AtomicInteger> SPAWNS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, AtomicInteger> SOURCES = new ConcurrentHashMap<>();
    private static volatile @Nullable Vec3 lastSpawnPosition;

    private VanillaTntImpactAudit() {}

    static void record(Vec3 position, Vec3 sourcePosition) {
        lastSpawnPosition = position;
        SPAWNS.computeIfAbsent(BlockPos.containing(position), ignored -> new AtomicInteger())
                .incrementAndGet();
        SOURCES.computeIfAbsent(BlockPos.containing(sourcePosition), ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    public static int spawnsWithin(AABB bounds) {
        AABB toleranceBounds = bounds.inflate(2.0D);
        return SPAWNS.entrySet().stream()
                .filter(entry -> toleranceBounds.contains(entry.getKey().getCenter()))
                .mapToInt(entry -> entry.getValue().get())
                .sum();
    }

    public static void clearWithin(AABB bounds) {
        AABB toleranceBounds = bounds.inflate(2.0D);
        SPAWNS.keySet().removeIf(position -> toleranceBounds.contains(position.getCenter()));
        SOURCES.keySet().removeIf(position -> toleranceBounds.contains(position.getCenter()));
    }

    public static @Nullable Vec3 lastSpawnPosition() {
        return lastSpawnPosition;
    }
}
