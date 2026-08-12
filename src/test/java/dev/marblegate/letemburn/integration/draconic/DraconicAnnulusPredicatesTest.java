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

package dev.marblegate.letemburn.integration.draconic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DraconicAnnulusPredicatesTest {
    private static final int MAX_AUDITED_RADIUS = 500;

    @Test
    void a0PreservesTheCompleteObservableTraceThroughRadiusFiveHundred() {
        for (int radius = 0; radius <= MAX_AUDITED_RADIUS; radius++) {
            Trace legacy = scan(radius, DraconicAnnulusPredicatesTest::legacyContains);
            Trace optimized = scan(radius, DraconicAnnulusPredicates::containsSquared);
            assertEquals(legacy.snapshot(), optimized.snapshot(), "trace mismatch at radius " + radius);
        }
    }

    @Test
    void a1PreservesEveryExplosionObservableThroughRadiusFiveHundred() {
        for (int radius = 0; radius <= MAX_AUDITED_RADIUS; radius++) {
            Trace legacy = scan(radius, DraconicAnnulusPredicatesTest::legacyContains);
            Trace sparse = scanSparse(radius);

            assertEquals(
                    legacy.observableSnapshot(),
                    sparse.observableSnapshot(),
                    "observable trace mismatch at radius " + radius);
            assertEquals(sparse.acceptedPositions.size(), sparse.candidates, "A1 visited a rejected coordinate");
            assertTrue(
                    sparse.candidates <= (8L * radius) + 1L,
                    "A1 candidate count did not remain bounded by circumference at radius " + radius);
        }
    }

    @Test
    void redirectedComparisonHasExactlyTheLegacyBoundaries() {
        for (int radius = 0; radius <= MAX_AUDITED_RADIUS; radius++) {
            for (int deltaX = -radius; deltaX < radius; deltaX++) {
                for (int deltaZ = -radius; deltaZ < radius; deltaZ++) {
                    boolean legacy = legacyContains(radius, deltaX, deltaZ);
                    double classified = DraconicAnnulusPredicates.membershipDistance(
                            radius, deltaX, deltaZ, 0D, 0D);
                    boolean redirected = classified < radius && classified >= radius - 1D;
                    if (legacy != redirected) {
                        fail("redirect mismatch at radius=" + radius + ", x=" + deltaX + ", z=" + deltaZ);
                    }
                }
            }
        }
    }

    @Test
    void nonIntegralCoordinatesRetainTheLegacyDistanceCalculation() {
        double distance = DraconicAnnulusPredicates.membershipDistance(12, 4.25D, -2.5D, 1.5D, -7.75D);
        assertEquals(Math.sqrt((2.75D * 2.75D) + (5.25D * 5.25D)), distance);
    }

    @Test
    void squaredPredicateRetainsBothOpenAndClosedEdges() {
        assertFalse(DraconicAnnulusPredicates.containsSquared(5, 5, 0));
        assertTrue(DraconicAnnulusPredicates.containsSquared(5, 4, 0));
        assertFalse(DraconicAnnulusPredicates.containsSquared(5, 3, 0));
    }

    @Test
    void integerSquareRootIsExactAtLongBoundaries() {
        assertEquals(0L, DraconicAnnulusPredicates.floorSquareRoot(0L));
        assertEquals(1L, DraconicAnnulusPredicates.floorSquareRoot(1L));
        assertEquals(1L, DraconicAnnulusPredicates.floorSquareRoot(2L));
        assertEquals(2L, DraconicAnnulusPredicates.floorSquareRoot(8L));
        assertEquals(3L, DraconicAnnulusPredicates.floorSquareRoot(9L));
        assertEquals(3_037_000_499L, DraconicAnnulusPredicates.floorSquareRoot(Long.MAX_VALUE));
        assertEquals(3_037_000_500L, DraconicAnnulusPredicates.ceilingSquareRoot(Long.MAX_VALUE));
    }

    private static Trace scan(int radius, AnnulusPredicate predicate) {
        Trace trace = new Trace(radius);
        for (int deltaX = -radius; deltaX < radius; deltaX++) {
            for (int deltaZ = -radius; deltaZ < radius; deltaZ++) {
                trace.candidate();
                if (predicate.contains(radius, deltaX, deltaZ)) {
                    trace.accept(deltaX, deltaZ);
                }
            }
        }
        return trace;
    }

    private static Trace scanSparse(int radius) {
        Trace trace = new Trace(radius);
        for (int deltaX = -radius; deltaX < radius; deltaX++) {
            DraconicAnnulusPredicates.ScanLine line = DraconicAnnulusPredicates.scanLine(radius, deltaX);
            long lineCandidates = 0L;
            for (int deltaZ = line.firstDeltaZ(); !line.isEmpty() && deltaZ <= line.lastDeltaZ();) {
                trace.candidate();
                lineCandidates++;
                assertTrue(
                        DraconicAnnulusPredicates.containsSquared(radius, deltaX, deltaZ),
                        "A1 emitted a rejected coordinate");
                trace.accept(deltaX, deltaZ);
                deltaZ = line.adjustIncrementedDeltaZ(deltaZ + 1);
            }
            assertEquals(line.candidateCount(), lineCandidates, "scan-line count mismatch");
        }
        return trace;
    }

    private static boolean legacyContains(int radius, int deltaX, int deltaZ) {
        double distance = Math.sqrt(((double) deltaX * deltaX) + ((double) deltaZ * deltaZ));
        return distance < radius && distance >= radius - 1D;
    }

    @FunctionalInterface
    private interface AnnulusPredicate {
        boolean contains(int radius, int deltaX, int deltaZ);
    }

    private static final class Trace {
        private final int radius;
        private final Random random;
        private final List<Long> acceptedPositions = new ArrayList<>();
        private final List<Long> worldReadOrder = new ArrayList<>();
        private final List<Long> worldWriteOrder = new ArrayList<>();
        private final LinkedHashSet<Long> setInsertionOrder = new LinkedHashSet<>();
        private final List<Long> floatingPointRawBits = new ArrayList<>();
        private final List<Long> damageRawBits = new ArrayList<>();
        private long candidates;
        private long randomCalls;

        private Trace(int radius) {
            this.radius = radius;
            random = new Random(0x4C4554454D425552L ^ radius);
        }

        private void candidate() {
            candidates++;
        }

        private void accept(int deltaX, int deltaZ) {
            long position = pack(deltaX, deltaZ);
            acceptedPositions.add(position);

            int randomInt = random.nextInt(10);
            double randomUp = random.nextDouble();
            double randomDown = random.nextDouble();
            randomCalls += 3;

            long upperRead = pack(deltaX, deltaZ) ^ 0x5555555555555555L;
            long lowerRead = pack(deltaX, deltaZ) ^ 0xAAAAAAAAAAAAAAAAL;
            worldReadOrder.add(upperRead);
            worldReadOrder.add(lowerRead);

            double radialScale = radius == 0 ? 0D : 1D - ((double) radius / (radius + 1D));
            double resistance = ((randomInt + 1D) * (Math.abs(deltaX) + 1D)) / (Math.abs(deltaZ) + 1D);
            double upperResult = (resistance * radialScale) + randomUp;
            double lowerResult = (resistance * (1D - radialScale)) + randomDown;
            floatingPointRawBits.add(Double.doubleToRawLongBits(upperResult));
            floatingPointRawBits.add(Double.doubleToRawLongBits(lowerResult));
            floatingPointRawBits.add((long) Float.floatToRawIntBits((float) (upperResult + lowerResult)));
            damageRawBits.add(Double.doubleToRawLongBits((upperResult + lowerResult) / (radius + 1D)));

            if (((upperRead ^ randomInt) & 1L) == 0L) {
                worldWriteOrder.add(position);
            }
            setInsertionOrder.add(position);
        }

        private TraceSnapshot snapshot() {
            return new TraceSnapshot(
                    candidates,
                    List.copyOf(acceptedPositions),
                    randomCalls,
                    List.copyOf(worldReadOrder),
                    List.copyOf(worldWriteOrder),
                    List.copyOf(setInsertionOrder),
                    List.copyOf(floatingPointRawBits),
                    List.copyOf(damageRawBits),
                    packetTrace(),
                    finalStateDigest());
        }

        private ObservableTraceSnapshot observableSnapshot() {
            return new ObservableTraceSnapshot(
                    List.copyOf(acceptedPositions),
                    randomCalls,
                    List.copyOf(worldReadOrder),
                    List.copyOf(worldWriteOrder),
                    List.copyOf(setInsertionOrder),
                    List.copyOf(floatingPointRawBits),
                    List.copyOf(damageRawBits),
                    packetTrace(),
                    finalStateDigest());
        }

        private List<Long> packetTrace() {
            return List.of(((long) radius << 32) ^ acceptedPositions.size(), finalStateDigest());
        }

        private long finalStateDigest() {
            long digest = 0xCBF29CE484222325L;
            for (long position : worldWriteOrder) {
                digest = (digest ^ position) * 0x100000001B3L;
            }
            for (long position : setInsertionOrder) {
                digest = (digest ^ Long.rotateLeft(position, 17)) * 0x100000001B3L;
            }
            return digest;
        }

        private static long pack(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }
    }

    private record TraceSnapshot(
            long candidates,
            List<Long> acceptedPositions,
            long randomCalls,
            List<Long> worldReadOrder,
            List<Long> worldWriteOrder,
            List<Long> setInsertionOrder,
            List<Long> floatingPointRawBits,
            List<Long> damageRawBits,
            List<Long> packetTrace,
            long finalStateDigest) {}

    private record ObservableTraceSnapshot(
            List<Long> acceptedPositions,
            long randomCalls,
            List<Long> worldReadOrder,
            List<Long> worldWriteOrder,
            List<Long> setInsertionOrder,
            List<Long> floatingPointRawBits,
            List<Long> damageRawBits,
            List<Long> packetTrace,
            long finalStateDigest) {}
}
