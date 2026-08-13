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

/** Experimental integer predicates retained for audit tests and pure-computation benchmarks. */
public final class DraconicAnnulusPredicates {
    private DraconicAnnulusPredicates() {}

    /**
     * Returns a classification value that preserves ProcessExplosion's two annulus comparisons.
     *
     * <p>This is not a behavior-preserving replacement for the original distance. ProcessExplosion also feeds
     * that distance into radial-resistance accumulation, so substituting this classification value changes the
     * explosion even when the accepted coordinate sequence is identical. It is intentionally not used by the
     * production mixin. Non-integral coordinates use the original Euclidean calculation.
     */
    public static double membershipDistance(
            int radius, double x, double z, double centreX, double centreZ) {
        double deltaX = x - centreX;
        double deltaZ = z - centreZ;
        long integralX = (long) deltaX;
        long integralZ = (long) deltaZ;

        if ((double) integralX != deltaX || (double) integralZ != deltaZ) {
            return Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
        }

        long distanceSquared = (integralX * integralX) + (integralZ * integralZ);
        long outerSquared = (long) radius * radius;
        long innerRadius = (long) radius - 1L;
        long innerSquared = innerRadius * innerRadius;

        if (distanceSquared >= outerSquared) {
            return radius;
        }
        if (distanceSquared < innerSquared) {
            return radius - 2D;
        }
        return radius - 0.5D;
    }

    public static boolean containsSquared(int radius, int deltaX, int deltaZ) {
        long distanceSquared = ((long) deltaX * deltaX) + ((long) deltaZ * deltaZ);
        long outerSquared = (long) radius * radius;
        long innerRadius = (long) radius - 1L;
        long innerSquared = innerRadius * innerRadius;
        return distanceSquared < outerSquared && distanceSquared >= innerSquared;
    }

    public static ScanLine scanLine(int radius, int deltaX) {
        if (radius <= 0) {
            return ScanLine.EMPTY;
        }

        long deltaXSquared = (long) deltaX * deltaX;
        long outerSquared = (long) radius * radius;
        long outerRemainder = outerSquared - deltaXSquared - 1L;
        if (outerRemainder < 0L) {
            return ScanLine.EMPTY;
        }

        int outerAbsoluteZ = Math.toIntExact(floorSquareRoot(outerRemainder));
        long innerRadius = (long) radius - 1L;
        long innerRemainder = (innerRadius * innerRadius) - deltaXSquared;
        int lowerAbsoluteZ = innerRemainder <= 0L ? 0 : Math.toIntExact(ceilingSquareRoot(innerRemainder));
        if (lowerAbsoluteZ > outerAbsoluteZ) {
            return ScanLine.EMPTY;
        }
        return new ScanLine(-outerAbsoluteZ, lowerAbsoluteZ, outerAbsoluteZ);
    }

    static long floorSquareRoot(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("Cannot take the integer square root of a negative value");
        }

        long remainder = value;
        long root = 0L;
        long bit = 1L << 62;
        while (bit > remainder) {
            bit >>>= 2;
        }
        while (bit != 0L) {
            long trial = root + bit;
            if (remainder >= trial) {
                remainder -= trial;
                root = (root >>> 1) + bit;
            } else {
                root >>>= 1;
            }
            bit >>>= 2;
        }
        return root;
    }

    static long ceilingSquareRoot(long value) {
        long floor = floorSquareRoot(value);
        return floor * floor == value ? floor : floor + 1L;
    }

    public record ScanLine(int firstDeltaZ, int lowerAbsoluteZ, int lastDeltaZ) {
        private static final ScanLine EMPTY = new ScanLine(1, 0, 0);

        public boolean isEmpty() {
            return firstDeltaZ > lastDeltaZ;
        }

        public int adjustIncrementedDeltaZ(int incrementedDeltaZ) {
            if (lowerAbsoluteZ > 0
                    && incrementedDeltaZ > -lowerAbsoluteZ
                    && incrementedDeltaZ < lowerAbsoluteZ) {
                return lowerAbsoluteZ;
            }
            return incrementedDeltaZ;
        }

        public long candidateCount() {
            if (isEmpty()) {
                return 0L;
            }
            if (lowerAbsoluteZ == 0) {
                return (long) lastDeltaZ - firstDeltaZ + 1L;
            }
            return 2L * (lastDeltaZ - lowerAbsoluteZ + 1L);
        }
    }
}
