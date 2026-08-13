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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class DraconicExplosionScheduleAudit {
    private static final Map<BlockPos, AtomicInteger> SCHEDULED_EXPLOSIONS = new ConcurrentHashMap<>();

    private DraconicExplosionScheduleAudit() {}

    public static void record(BlockPos position) {
        SCHEDULED_EXPLOSIONS.computeIfAbsent(position.immutable(), ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    public static int scheduledWithin(AABB bounds) {
        AABB toleranceBounds = bounds.inflate(2.0D);
        return SCHEDULED_EXPLOSIONS.entrySet().stream()
                .filter(entry -> toleranceBounds.contains(entry.getKey().getCenter()))
                .mapToInt(entry -> entry.getValue().get())
                .sum();
    }

    public static void clearWithin(AABB bounds) {
        AABB toleranceBounds = bounds.inflate(2.0D);
        SCHEDULED_EXPLOSIONS.keySet()
                .removeIf(position -> toleranceBounds.contains(position.getCenter()));
    }
}
