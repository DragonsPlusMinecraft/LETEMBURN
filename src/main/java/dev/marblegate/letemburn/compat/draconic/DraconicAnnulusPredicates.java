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

/** Exact integer predicates for the horizontal annulus scanned by a Draconic reactor explosion. */
public final class DraconicAnnulusPredicates {
    private DraconicAnnulusPredicates() {}

    /**
     * Returns a classification value for ProcessExplosion's two distance comparisons.
     *
     * <p>The intercepted value is used only by {@code distance < radius && distance >= radius - 1}.
     * Returning a value in the matching comparison interval avoids a square root while retaining both
     * original branches. If an upstream version supplies non-integral coordinates, this method falls back to
     * the original Euclidean calculation instead of changing its behaviour.
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
}
