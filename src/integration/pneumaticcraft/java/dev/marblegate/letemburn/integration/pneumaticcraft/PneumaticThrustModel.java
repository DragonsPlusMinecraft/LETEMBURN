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

package dev.marblegate.letemburn.integration.pneumaticcraft;

import net.minecraft.core.Direction;
import org.joml.Vector3d;

public final class PneumaticThrustModel {
    private static final double TICKS_PER_SECOND = 20.0D;

    private PneumaticThrustModel() {}

    public static double forceMagnitude(double pressure, double leakRateMlPerTick, double thrustScale) {
        if (!Double.isFinite(pressure)
                || !Double.isFinite(leakRateMlPerTick)
                || !Double.isFinite(thrustScale)
                || thrustScale <= 0.0D) {
            return 0.0D;
        }
        return thrustScale * Math.abs(pressure) * Math.abs(leakRateMlPerTick) * TICKS_PER_SECOND;
    }

    public static Vector3d rawImpulse(
            Direction leakFace,
            double pressure,
            double leakRateMlPerTick,
            double timeStep,
            double thrustScale) {
        double force = forceMagnitude(pressure, leakRateMlPerTick, thrustScale);
        if (force == 0.0D || !Double.isFinite(timeStep) || timeStep <= 0.0D || pressure == 0.0D) {
            return new Vector3d();
        }
        double reactionSign = -Math.copySign(1.0D, pressure);
        double magnitude = force * timeStep * reactionSign;
        return new Vector3d(
                leakFace.getStepX() * magnitude,
                leakFace.getStepY() * magnitude,
                leakFace.getStepZ() * magnitude);
    }

    public static double capScale(double totalImpulseMagnitude, double bodyMass, double maximumDeltaVelocity) {
        if (!Double.isFinite(totalImpulseMagnitude)
                || !Double.isFinite(bodyMass)
                || !Double.isFinite(maximumDeltaVelocity)
                || totalImpulseMagnitude <= 0.0D
                || bodyMass <= 0.0D
                || maximumDeltaVelocity <= 0.0D) {
            return 0.0D;
        }
        return Math.min(1.0D, bodyMass * maximumDeltaVelocity / totalImpulseMagnitude);
    }
}
