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

import dev.marblegate.letemburn.common.effect.EffectKey;
import dev.marblegate.letemburn.common.impact.ImpactStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class NuclearPlasmaProjectionAudit {
    private static final List<Event> EVENTS = new CopyOnWriteArrayList<>();
    private static final List<NativeSteamDelivery> NATIVE_STEAM_DELIVERIES = new CopyOnWriteArrayList<>();
    private static final Map<TargetKey, ConcurrentLinkedQueue<Event>> PENDING = new ConcurrentHashMap<>();
    private static final AtomicInteger CAPTURE_COUNT = new AtomicInteger();

    private NuclearPlasmaProjectionAudit() {}

    public static void recordCandidate(
            ServerLevel level,
            EffectKey key,
            Vec3 globalPosition,
            int remainingSpread,
            ImpactStatus status) {
        if (CAPTURE_COUNT.get() == 0) {
            return;
        }
        Event candidate = new Event(
                Kind.CANDIDATE_REGISTERED,
                key.subLevelId(),
                rootPosition(key.payloadFingerprint()),
                key.localPosition(),
                globalPosition,
                remainingSpread,
                key.gameTime());
        EVENTS.add(candidate);
        EVENTS.add(candidate.withKind(
                status == ImpactStatus.QUEUED ? Kind.ESCAPE_QUEUED : Kind.DUPLICATE_SUPPRESSED));
        if (status == ImpactStatus.QUEUED) {
            PENDING.computeIfAbsent(
                    new TargetKey(level, BlockPos.containing(globalPosition)),
                    ignored -> new ConcurrentLinkedQueue<>())
                    .add(candidate);
        }
    }

    public static void recordOutcome(ServerLevel level, BlockPos position, Kind kind) {
        if (CAPTURE_COUNT.get() == 0) {
            return;
        }
        TargetKey key = new TargetKey(level, position);
        ConcurrentLinkedQueue<Event> candidates = PENDING.get(key);
        Event candidate = candidates == null ? null : candidates.poll();
        if (candidate == null) {
            return;
        }
        if (candidates.isEmpty()) {
            PENDING.remove(key, candidates);
        }
        EVENTS.add(candidate.withKind(kind));
    }

    public static void recordNativeSteamDelivery(
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
            PENDING.clear();
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
            PENDING.clear();
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

    private static BlockPos rootPosition(String fingerprint) {
        int registeredTimeSeparator = fingerprint.lastIndexOf(':');
        int rootSeparator = fingerprint.lastIndexOf(':', registeredTimeSeparator - 1);
        if (rootSeparator < 0 || registeredTimeSeparator <= rootSeparator + 1) {
            throw new IllegalArgumentException("Unexpected projected plasma fingerprint: " + fingerprint);
        }
        return BlockPos.of(Long.parseLong(fingerprint.substring(rootSeparator + 1, registeredTimeSeparator)));
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
            long gameTime) {
        private Event withKind(Kind replacement) {
            return new Event(
                    replacement,
                    subLevelId,
                    rootPosition,
                    exitPosition,
                    globalPosition,
                    remainingSpread,
                    gameTime);
        }
    }

    public record NativeSteamDelivery(
            BlockPos plasmaPosition,
            int requestedAmount,
            int temperature,
            int acceptedAmount,
            long gameTime) {}

    private record TargetKey(ServerLevel level, BlockPos position) {
        private TargetKey {
            position = position.immutable();
        }
    }
}
