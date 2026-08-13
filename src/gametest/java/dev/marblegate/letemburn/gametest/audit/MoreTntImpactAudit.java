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

package dev.marblegate.letemburn.gametest.audit;

import dev.marblegate.letemburn.integration.moretnt.MoreTntNativeFactory.NativeTntSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class MoreTntImpactAudit {
    private static final List<SpawnEvent> EVENTS = new ArrayList<>();

    private MoreTntImpactAudit() {}

    public static synchronized void record(
            UUID subLevelId,
            NativeTntSpec spec,
            Vec3 position,
            Direction projectedFacing,
            int envelopeDepth,
            int initialFuse,
            boolean parentContainedSourceBlock) {
        EVENTS.add(new SpawnEvent(
                subLevelId,
                spec.blockId(),
                spec.entityTypeId(),
                position,
                spec.localFacing(),
                projectedFacing,
                spec.size(),
                spec.fire(),
                envelopeDepth,
                initialFuse,
                parentContainedSourceBlock));
    }

    public static synchronized List<SpawnEvent> eventsForSubLevel(UUID subLevelId) {
        return EVENTS.stream()
                .filter(event -> event.subLevelId().equals(subLevelId))
                .toList();
    }

    public static synchronized void clearForSubLevel(UUID subLevelId) {
        EVENTS.removeIf(event -> event.subLevelId().equals(subLevelId));
    }

    public record SpawnEvent(
            UUID subLevelId,
            ResourceLocation blockId,
            ResourceLocation entityTypeId,
            Vec3 position,
            Direction localFacing,
            Direction projectedFacing,
            float size,
            boolean fire,
            int envelopeDepth,
            int initialFuse,
            boolean parentContainedSourceBlock) {}
}
