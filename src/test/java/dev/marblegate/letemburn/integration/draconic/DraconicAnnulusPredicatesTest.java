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
import java.util.List;
import org.junit.jupiter.api.Test;

class DraconicAnnulusPredicatesTest {
    private static final int MAX_AUDITED_RADIUS = 500;

    @Test
    void a0PreservesOnlyTheLegacyCoordinateSequenceThroughRadiusFiveHundred() {
        for (int radius = 0; radius <= MAX_AUDITED_RADIUS; radius++) {
            assertEquals(
                    legacyCoordinates(radius),
                    a0Coordinates(radius),
                    "coordinate mismatch at radius " + radius);
        }
    }

    @Test
    void a1PreservesOnlyTheLegacyCoordinateSequenceThroughRadiusFiveHundred() {
        for (int radius = 0; radius <= MAX_AUDITED_RADIUS; radius++) {
            SparseScan sparse = sparseCoordinates(radius);
            assertEquals(
                    legacyCoordinates(radius),
                    sparse.positions(),
                    "sparse coordinate mismatch at radius " + radius);
            assertEquals(sparse.positions().size(), sparse.candidates(), "A1 visited a rejected coordinate");
            assertTrue(
                    sparse.candidates() <= (8L * radius) + 1L,
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
    void membershipClassifierIsNotTheOriginalRadialDistance() {
        assertEquals(4.5D, DraconicAnnulusPredicates.membershipDistance(5, 4D, 0D, 0D, 0D));
        assertEquals(4D, Math.sqrt((4D * 4D) + (0D * 0D)));
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

    private static List<Long> legacyCoordinates(int radius) {
        List<Long> positions = new ArrayList<>();
        for (int deltaX = -radius; deltaX < radius; deltaX++) {
            for (int deltaZ = -radius; deltaZ < radius; deltaZ++) {
                if (legacyContains(radius, deltaX, deltaZ)) {
                    positions.add(pack(deltaX, deltaZ));
                }
            }
        }
        return positions;
    }

    private static List<Long> a0Coordinates(int radius) {
        List<Long> positions = new ArrayList<>();
        for (int deltaX = -radius; deltaX < radius; deltaX++) {
            for (int deltaZ = -radius; deltaZ < radius; deltaZ++) {
                double classified = DraconicAnnulusPredicates.membershipDistance(
                        radius, deltaX, deltaZ, 0D, 0D);
                if (classified < radius && classified >= radius - 1D) {
                    positions.add(pack(deltaX, deltaZ));
                }
            }
        }
        return positions;
    }

    private static SparseScan sparseCoordinates(int radius) {
        List<Long> positions = new ArrayList<>();
        long candidates = 0L;
        for (int deltaX = -radius; deltaX < radius; deltaX++) {
            DraconicAnnulusPredicates.ScanLine line = DraconicAnnulusPredicates.scanLine(radius, deltaX);
            long lineCandidates = 0L;
            for (int deltaZ = line.firstDeltaZ(); !line.isEmpty() && deltaZ <= line.lastDeltaZ();) {
                candidates++;
                lineCandidates++;
                assertTrue(
                        DraconicAnnulusPredicates.containsSquared(radius, deltaX, deltaZ),
                        "A1 emitted a rejected coordinate");
                positions.add(pack(deltaX, deltaZ));
                deltaZ = line.adjustIncrementedDeltaZ(deltaZ + 1);
            }
            assertEquals(line.candidateCount(), lineCandidates, "scan-line count mismatch");
        }
        return new SparseScan(List.copyOf(positions), candidates);
    }

    private static boolean legacyContains(int radius, int deltaX, int deltaZ) {
        double distance = Math.sqrt(((double) deltaX * deltaX) + ((double) deltaZ * deltaZ));
        return distance < radius && distance >= radius - 1D;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private record SparseScan(List<Long> positions, long candidates) {}
}
