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

package dev.marblegate.letemburn.integration.ballistix;

import dev.marblegate.letemburn.common.impulse.ExplosionImpulseBridge.ApplicationResult;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public final class BallistixImpactAudit {
    private static final ConcurrentLinkedQueue<ImpactEvent> IMPACTS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<BridgeEvent> BRIDGES = new ConcurrentLinkedQueue<>();

    private BallistixImpactAudit() {}

    static void recordImpact(ResourceLocation type, BlockPos position, int envelopeDepth) {
        IMPACTS.add(new ImpactEvent(type, position.immutable(), envelopeDepth));
    }

    static void recordBridge(ResourceLocation type, BlockPos position, ApplicationResult result) {
        BRIDGES.add(new BridgeEvent(type, position.immutable(), result));
    }

    public static int impactsWithin(AABB bounds) {
        AABB expanded = bounds.inflate(2.0D);
        return (int) IMPACTS.stream()
                .filter(event -> expanded.contains(event.position().getCenter()))
                .count();
    }

    public static int boxedImpactsWithin(AABB bounds) {
        AABB expanded = bounds.inflate(2.0D);
        return (int) IMPACTS.stream()
                .filter(event -> event.envelopeDepth() > 0
                        && expanded.contains(event.position().getCenter()))
                .count();
    }

    public static int bridgeStartsWithin(AABB bounds) {
        AABB expanded = bounds.inflate(2.0D);
        return (int) BRIDGES.stream()
                .filter(event -> expanded.contains(event.position().getCenter()))
                .count();
    }

    public static int affectedBodiesWithin(AABB bounds) {
        AABB expanded = bounds.inflate(2.0D);
        return BRIDGES.stream()
                .filter(event -> expanded.contains(event.position().getCenter()))
                .mapToInt(event -> event.result().affectedBodies())
                .sum();
    }

    public static void clearWithin(AABB bounds) {
        AABB expanded = bounds.inflate(2.0D);
        IMPACTS.removeIf(event -> expanded.contains(event.position().getCenter()));
        BRIDGES.removeIf(event -> expanded.contains(event.position().getCenter()));
    }

    public record ImpactEvent(ResourceLocation type, BlockPos position, int envelopeDepth) {}

    public record BridgeEvent(ResourceLocation type, BlockPos position, ApplicationResult result) {}
}
