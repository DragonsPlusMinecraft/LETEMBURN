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

import java.util.Arrays;
import java.util.function.IntToLongFunction;

public final class DraconicAnnulusBenchmark {
    private static final int[] RADII = { 50, 150, 350 };
    private static final int WARMUP_ITERATIONS = 12;
    private static final int MEASURED_ITERATIONS = 21;
    private static volatile long blackhole;

    private DraconicAnnulusBenchmark() {}

    public static void main(String[] args) {
        System.out.println("Draconic annulus predicate benchmark (median nanoseconds per complete radius scan)");
        for (int radius : RADII) {
            Result legacy = measure(radius, DraconicAnnulusBenchmark::scanLegacy);
            Result a0 = measure(radius, DraconicAnnulusBenchmark::scanA0);
            Result a1 = measure(radius, DraconicAnnulusBenchmark::scanA1);
            if (legacy.acceptedPositions != a0.acceptedPositions
                    || legacy.acceptedPositions != a1.acceptedPositions) {
                throw new IllegalStateException("An optimized scan changed the accepted position count at radius " + radius);
            }
            if (radius == 350 && a1.medianNanos >= a0.medianNanos) {
                throw new IllegalStateException("A1 was not faster than A0 at radius 350");
            }
            System.out.printf(
                    "radius=%d boxCandidates=%d sparseCandidates=%d legacy=%d ns A0=%d ns A1=%d ns A1/A0=%.3fx%n",
                    radius,
                    (long) radius * 2L * radius * 2L,
                    legacy.acceptedPositions,
                    legacy.medianNanos,
                    a0.medianNanos,
                    a1.medianNanos,
                    (double) a0.medianNanos / a1.medianNanos);
        }
    }

    private static Result measure(int radius, IntToLongFunction operation) {
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            blackhole ^= operation.applyAsLong(radius);
        }

        long[] samples = new long[MEASURED_ITERATIONS];
        long accepted = 0L;
        for (int iteration = 0; iteration < MEASURED_ITERATIONS; iteration++) {
            long start = System.nanoTime();
            accepted = operation.applyAsLong(radius);
            samples[iteration] = System.nanoTime() - start;
            blackhole ^= accepted;
        }
        Arrays.sort(samples);
        return new Result(samples[samples.length / 2], accepted);
    }

    private static long scanLegacy(int radius) {
        long accepted = 0L;
        for (int deltaX = -radius; deltaX < radius; deltaX++) {
            for (int deltaZ = -radius; deltaZ < radius; deltaZ++) {
                double distance = Math.sqrt(((double) deltaX * deltaX) + ((double) deltaZ * deltaZ));
                if (distance < radius && distance >= radius - 1D) {
                    accepted++;
                }
            }
        }
        return accepted;
    }

    private static long scanA0(int radius) {
        long accepted = 0L;
        for (int deltaX = -radius; deltaX < radius; deltaX++) {
            for (int deltaZ = -radius; deltaZ < radius; deltaZ++) {
                if (DraconicAnnulusPredicates.containsSquared(radius, deltaX, deltaZ)) {
                    accepted++;
                }
            }
        }
        return accepted;
    }

    private static long scanA1(int radius) {
        long accepted = 0L;
        for (int deltaX = -radius; deltaX < radius; deltaX++) {
            DraconicAnnulusPredicates.ScanLine line = DraconicAnnulusPredicates.scanLine(radius, deltaX);
            for (int deltaZ = line.firstDeltaZ(); !line.isEmpty() && deltaZ <= line.lastDeltaZ();) {
                if (!DraconicAnnulusPredicates.containsSquared(radius, deltaX, deltaZ)) {
                    throw new IllegalStateException("A1 emitted a rejected coordinate");
                }
                accepted++;
                deltaZ = line.adjustIncrementedDeltaZ(deltaZ + 1);
            }
        }
        return accepted;
    }

    private record Result(long medianNanos, long acceptedPositions) {}
}
