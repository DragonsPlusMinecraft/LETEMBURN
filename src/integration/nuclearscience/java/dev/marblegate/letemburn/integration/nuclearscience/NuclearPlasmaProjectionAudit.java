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
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NuclearPlasmaProjectionAudit {
    private static final List<Event> EVENTS = new CopyOnWriteArrayList<>();
    private static final List<NativeSteamDelivery> NATIVE_STEAM_DELIVERIES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger CAPTURE_COUNT = new AtomicInteger();

    private NuclearPlasmaProjectionAudit() {}

    static void record(
            Kind kind,
            UUID subLevelId,
            BlockPos rootPosition,
            BlockPos exitPosition,
            Vec3 globalPosition,
            int remainingSpread,
            long gameTime) {
        if (CAPTURE_COUNT.get() == 0) {
            return;
        }
        EVENTS.add(new Event(
                kind,
                subLevelId,
                rootPosition.immutable(),
                exitPosition.immutable(),
                new Vec3(globalPosition.x, globalPosition.y, globalPosition.z),
                remainingSpread,
                gameTime));
    }

    static void recordNativeSteamDelivery(
            BlockPos plasmaPosition, int requestedAmount, int temperature, int acceptedAmount, long gameTime) {
        if (CAPTURE_COUNT.get() == 0) {
            return;
        }
        NATIVE_STEAM_DELIVERIES.add(new NativeSteamDelivery(
                plasmaPosition.immutable(), requestedAmount, temperature, acceptedAmount, gameTime));
    }

    public static void beginCapture() {
        if (CAPTURE_COUNT.getAndIncrement() == 0) {
            EVENTS.clear();
            NATIVE_STEAM_DELIVERIES.clear();
        }
    }

    public static void endCapture() {
        int remaining = CAPTURE_COUNT.decrementAndGet();
        if (remaining < 0) {
            CAPTURE_COUNT.set(0);
            throw new IllegalStateException("Nuclear plasma audit capture was ended without being started");
        }
        if (remaining == 0) {
            EVENTS.clear();
            NATIVE_STEAM_DELIVERIES.clear();
        }
    }

    public static List<Event> events() {
        return List.copyOf(EVENTS);
    }

    public static long count(Kind kind) {
        return EVENTS.stream().filter(event -> event.kind() == kind).count();
    }

    public static long count(UUID subLevelId, Kind kind) {
        return EVENTS.stream()
                .filter(event -> event.subLevelId().equals(subLevelId) && event.kind() == kind)
                .count();
    }

    public static List<NativeSteamDelivery> nativeSteamDeliveries() {
        return List.copyOf(NATIVE_STEAM_DELIVERIES);
    }

    public enum Kind {
        CANDIDATE_REGISTERED,
        ESCAPE_QUEUED,
        PARENT_SEED_CREATED,
        PARENT_TARGET_PROTECTED,
        DUPLICATE_SUPPRESSED
    }

    public record Event(
            Kind kind,
            UUID subLevelId,
            BlockPos rootPosition,
            BlockPos exitPosition,
            Vec3 globalPosition,
            int remainingSpread,
            long gameTime) {}

    public record NativeSteamDelivery(
            BlockPos plasmaPosition,
            int requestedAmount,
            int temperature,
            int acceptedAmount,
            long gameTime) {}
}
