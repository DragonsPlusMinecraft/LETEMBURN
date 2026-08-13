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

package dev.marblegate.letemburn.integration.vanilla;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class VanillaTntImpactAudit {
    private static final ConcurrentLinkedQueue<SpawnEvent> SPAWNS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<BelowThresholdEvent> BELOW_THRESHOLD = new ConcurrentLinkedQueue<>();
    private static volatile @Nullable Vec3 lastSpawnPosition;

    private VanillaTntImpactAudit() {}

    static void recordSpawn(
            Vec3 position, Vec3 sourcePosition, int initialFuse, int envelopeDepth) {
        lastSpawnPosition = position;
        SPAWNS.add(new SpawnEvent(position, sourcePosition, initialFuse, envelopeDepth));
    }

    static void recordBelowThreshold(Vec3 position, double impactVelocity, int envelopeDepth) {
        BELOW_THRESHOLD.add(new BelowThresholdEvent(position, impactVelocity, envelopeDepth));
    }

    public static int spawnsWithin(AABB bounds) {
        AABB toleranceBounds = bounds.inflate(2.0D);
        return (int) SPAWNS.stream()
                .filter(event -> toleranceBounds.contains(event.position()))
                .count();
    }

    public static List<SpawnEvent> spawnEventsWithin(AABB bounds) {
        AABB toleranceBounds = bounds.inflate(2.0D);
        return SPAWNS.stream()
                .filter(event -> toleranceBounds.contains(event.position()))
                .toList();
    }

    public static List<BelowThresholdEvent> belowThresholdEventsWithin(AABB bounds) {
        AABB toleranceBounds = bounds.inflate(2.0D);
        return BELOW_THRESHOLD.stream()
                .filter(event -> toleranceBounds.contains(event.position()))
                .toList();
    }

    public static void clearWithin(AABB bounds) {
        AABB toleranceBounds = bounds.inflate(2.0D);
        SPAWNS.removeIf(event -> toleranceBounds.contains(event.position()));
        BELOW_THRESHOLD.removeIf(event -> toleranceBounds.contains(event.position()));
    }

    public static @Nullable Vec3 lastSpawnPosition() {
        return lastSpawnPosition;
    }

    public record SpawnEvent(
            Vec3 position, Vec3 sourcePosition, int initialFuse, int envelopeDepth) {}

    public record BelowThresholdEvent(Vec3 position, double impactVelocity, int envelopeDepth) {}
}
