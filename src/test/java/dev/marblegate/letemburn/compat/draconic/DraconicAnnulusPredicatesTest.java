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

package dev.marblegate.letemburn.compat.draconic;

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
                    List.copyOf(floatingPointRawBits));
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
            List<Long> floatingPointRawBits) {}
}
