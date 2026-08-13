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

package dev.marblegate.letemburn.integration.nuclearscience;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NuclearFissionProjectionAudit {
    private static final List<Event> EVENTS = new CopyOnWriteArrayList<>();
    private static volatile boolean capturing;

    private NuclearFissionProjectionAudit() {}

    static void record(
            Kind kind,
            UUID subLevelId,
            BlockPos localPosition,
            Vec3 globalPosition,
            int overheatingTicks) {
        if (!capturing) {
            return;
        }
        EVENTS.add(new Event(
                kind,
                subLevelId,
                localPosition.immutable(),
                new Vec3(globalPosition.x, globalPosition.y, globalPosition.z),
                overheatingTicks));
    }

    public static List<Event> events() {
        return List.copyOf(EVENTS);
    }

    public static long count(Kind kind) {
        return EVENTS.stream().filter(event -> event.kind() == kind).count();
    }

    public static void beginCapture() {
        EVENTS.clear();
        capturing = true;
    }

    public static void endCapture() {
        capturing = false;
        EVENTS.clear();
    }

    public enum Kind {
        RADIATION,
        SOUND,
        HEAT_QUERY,
        MELTDOWN_QUEUED,
        INITIAL_PARENT_CORE_WRITE_SKIPPED,
        NATIVE_EFFECT_STARTED,
        NATIVE_EXPLOSION,
        MELTDOWN_COMPLETE
    }

    public record Event(
            Kind kind,
            UUID subLevelId,
            BlockPos localPosition,
            Vec3 globalPosition,
            int overheatingTicks) {}
}
