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
import com.brandon3055.draconicevolution.lib.ExplosionHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class DraconicProcessExplosionOracle {
    private static final Map<ProcessExplosion, MutableCapture> CAPTURES = new IdentityHashMap<>();
    private static final Map<ExplosionHelper, ProcessExplosion> HELPERS = new IdentityHashMap<>();

    private DraconicProcessExplosionOracle() {}

    public static synchronized void begin(ProcessExplosion explosion) {
        if (CAPTURES.putIfAbsent(explosion, new MutableCapture()) != null) {
            throw new IllegalStateException("ProcessExplosion is already being captured");
        }
    }

    public static synchronized CaptureSnapshot finish(ProcessExplosion explosion) {
        MutableCapture capture = CAPTURES.remove(explosion);
        if (capture == null) {
            throw new IllegalStateException("ProcessExplosion capture was not started");
        }
        HELPERS.entrySet().removeIf(entry -> entry.getValue() == explosion);
        return new CaptureSnapshot(List.copyOf(capture.events));
    }

    public static synchronized List<Event> events(ProcessExplosion explosion) {
        MutableCapture capture = CAPTURES.get(explosion);
        return capture == null ? List.of() : List.copyOf(capture.events);
    }

    public static synchronized void markDetonation(ProcessExplosion explosion) {
        MutableCapture capture = CAPTURES.get(explosion);
        if (capture != null) {
            capture.phase = Phase.DETONATION;
        }
    }

    public static synchronized void attachHelper(ProcessExplosion explosion, ExplosionHelper helper) {
        if (CAPTURES.containsKey(explosion)) {
            HELPERS.put(helper, explosion);
        }
    }

    public static void recordAcceptedPosition(ProcessExplosion explosion, double x, double y, double z) {
        record(
                explosion,
                Kind.ACCEPTED_POSITION,
                Double.doubleToRawLongBits(x),
                Double.doubleToRawLongBits(y),
                Double.doubleToRawLongBits(z),
                "");
    }

    public static void recordRandomInt(ProcessExplosion explosion, int bound, int value) {
        record(explosion, Kind.RANDOM_INT, bound, value, 0L, "");
    }

    public static void recordRandomDouble(ProcessExplosion explosion, double value) {
        record(explosion, Kind.RANDOM_DOUBLE, Double.doubleToRawLongBits(value), 0L, 0L, "");
    }

    public static void recordBlockStateRead(ProcessExplosion explosion, BlockPos position, BlockState state) {
        record(explosion, Kind.BLOCK_STATE_READ, position.asLong(), 0L, 0L, state.toString());
    }

    public static void recordEmptyBlockRead(ProcessExplosion explosion, BlockPos position, boolean empty) {
        record(explosion, Kind.EMPTY_BLOCK_READ, position.asLong(), empty ? 1L : 0L, 0L, "");
    }

    public static void recordSetInsertion(
            ProcessExplosion explosion, HashSet<?> target, Object value, boolean inserted) {
        String set = setName(explosion, target);
        long encodedValue = value instanceof Long packed ? packed : value == null ? 0L : value.hashCode();
        record(explosion, Kind.SET_INSERTION, encodedValue, inserted ? 1L : 0L, 0L, set);
    }

    public static void recordTraceStart(
            ProcessExplosion explosion,
            double x,
            double y,
            double z,
            double power,
            int distance,
            int direction,
            double resistance,
            int travel) {
        record(
                explosion,
                Kind.TRACE_START,
                Double.doubleToRawLongBits(x),
                Double.doubleToRawLongBits(y),
                Double.doubleToRawLongBits(z),
                "power="
                        + Long.toUnsignedString(Double.doubleToRawLongBits(power))
                        + ",distance="
                        + distance
                        + ",direction="
                        + direction
                        + ",resistance="
                        + Long.toUnsignedString(Double.doubleToRawLongBits(resistance))
                        + ",travel="
                        + travel);
    }

    public static void recordTraceReturn(ProcessExplosion explosion, double value) {
        record(explosion, Kind.TRACE_RETURN, Double.doubleToRawLongBits(value), 0L, 0L, "");
    }

    public static void recordAirWrite(ExplosionHelper helper, BlockPos position, BlockState previousState) {
        ProcessExplosion explosion;
        synchronized (DraconicProcessExplosionOracle.class) {
            explosion = HELPERS.get(helper);
        }
        if (explosion != null) {
            record(explosion, Kind.WORLD_WRITE, position.asLong(), 0L, 0L, previousState + " -> minecraft:air");
        }
    }

    public static void recordLavaWrite(
            ProcessExplosion explosion, BlockPos position, BlockState state, boolean changed) {
        record(
                explosion,
                Kind.WORLD_WRITE,
                position.asLong(),
                changed ? 1L : 0L,
                0L,
                state.toString());
    }

    public static void recordPacket(
            ProcessExplosion explosion,
            RegistryAccess registryAccess,
            ResourceKey<Level> dimension,
            BlockPos position,
            int radius,
            boolean reload) {
        record(
                explosion,
                Kind.EXPLOSION_PACKET,
                position.asLong(),
                radius,
                reload ? 1L : 0L,
                dimension.location().toString());
    }

    public static void recordPacket(
            ExplosionHelper helper,
            RegistryAccess registryAccess,
            ResourceKey<Level> dimension,
            BlockPos position,
            int radius,
            boolean reload) {
        ProcessExplosion explosion;
        synchronized (DraconicProcessExplosionOracle.class) {
            explosion = HELPERS.get(helper);
        }
        if (explosion != null) {
            recordPacket(explosion, registryAccess, dimension, position, radius, reload);
        }
    }

    public static void recordDamage(ProcessExplosion explosion, Entity entity, float amount, boolean applied) {
        record(
                explosion,
                Kind.ENTITY_DAMAGE,
                Integer.toUnsignedLong(Float.floatToRawIntBits(amount)),
                entity.blockPosition().asLong(),
                applied ? 1L : 0L,
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    public static void recordDamageQuery(
            ProcessExplosion explosion,
            double calculationRadius,
            AABB bounds,
            List<? extends Entity> entities) {
        String entitySummary = entities.stream()
                .map(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                        + "@"
                        + entity.position())
                .toList()
                .toString();
        record(
                explosion,
                Kind.ENTITY_DAMAGE_QUERY,
                entities.size(),
                Double.doubleToRawLongBits(calculationRadius),
                0L,
                bounds + "; entities=" + entitySummary);
    }

    private static synchronized void record(
            ProcessExplosion explosion,
            Kind kind,
            long first,
            long second,
            long third,
            String detail) {
        MutableCapture capture = CAPTURES.get(explosion);
        if (capture != null) {
            capture.events.add(new Event(capture.phase, kind, first, second, third, detail));
        }
    }

    private static String setName(ProcessExplosion explosion, HashSet<?> target) {
        if (target == explosion.destroyedCache) {
            return "destroyedCache";
        }
        if (target == explosion.scannedCache) {
            return "scannedCache";
        }
        if (target == explosion.blocksToUpdate) {
            return "blocksToUpdate";
        }
        if (target == explosion.lavaPositions) {
            return "lavaPositions";
        }
        return "unknown";
    }

    public enum Phase {
        CALCULATION,
        DETONATION
    }

    public enum Kind {
        ACCEPTED_POSITION,
        RANDOM_INT,
        RANDOM_DOUBLE,
        BLOCK_STATE_READ,
        EMPTY_BLOCK_READ,
        SET_INSERTION,
        TRACE_START,
        TRACE_RETURN,
        WORLD_WRITE,
        EXPLOSION_PACKET,
        ENTITY_DAMAGE_QUERY,
        ENTITY_DAMAGE
    }

    public record Event(Phase phase, Kind kind, long first, long second, long third, String detail) {}

    public record CaptureSnapshot(List<Event> events) {
        public CaptureSnapshot {
            events = List.copyOf(events);
        }

        public long count(Phase phase, Kind kind) {
            return events.stream()
                    .filter(event -> event.phase() == phase && event.kind() == kind)
                    .count();
        }
    }

    private static final class MutableCapture {
        private final List<Event> events = new ArrayList<>();
        private Phase phase = Phase.CALCULATION;
    }
}
