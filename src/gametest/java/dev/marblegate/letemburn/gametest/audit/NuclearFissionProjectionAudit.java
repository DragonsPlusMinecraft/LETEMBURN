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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import nuclearscience.common.tile.reactor.fission.TileFissionReactorCore;

public final class NuclearFissionProjectionAudit {
    private static final List<Event> EVENTS = new CopyOnWriteArrayList<>();
    private static final Map<TileFissionReactorCore, Event> MELTDOWNS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static volatile boolean capturing;

    private NuclearFissionProjectionAudit() {}

    public static void record(
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

    public static void recordScheduled(
            TileFissionReactorCore core,
            UUID subLevelId,
            BlockPos localPosition,
            Vec3 globalPosition,
            int overheatingTicks) {
        Event event = new Event(
                Kind.MELTDOWN_QUEUED,
                subLevelId,
                localPosition.immutable(),
                globalPosition,
                overheatingTicks);
        if (capturing) {
            EVENTS.add(event);
            MELTDOWNS.put(core, event);
        }
    }

    public static void recordFromMeltdown(TileFissionReactorCore core, Kind kind) {
        if (!capturing) {
            return;
        }
        Event scheduled = MELTDOWNS.get(core);
        if (scheduled != null) {
            EVENTS.add(scheduled.withKind(kind));
        }
    }

    public static List<Event> events() {
        return List.copyOf(EVENTS);
    }

    public static long count(Kind kind) {
        return EVENTS.stream().filter(event -> event.kind() == kind).count();
    }

    public static void beginCapture() {
        EVENTS.clear();
        MELTDOWNS.clear();
        capturing = true;
    }

    public static void endCapture() {
        capturing = false;
        EVENTS.clear();
        MELTDOWNS.clear();
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
            int overheatingTicks) {
        private Event withKind(Kind replacement) {
            return new Event(
                    replacement,
                    subLevelId,
                    localPosition,
                    globalPosition,
                    overheatingTicks);
        }
    }
}
