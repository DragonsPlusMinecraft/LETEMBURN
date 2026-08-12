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

package dev.marblegate.letemburn.compat.pneumaticcraft;

public final class PneumaticImpactModel {
    public enum Action {
        NONE,
        LEAK,
        RUPTURE
    }

    public record Result(Action action, double impact, double pressureRatio, double severity) {}

    private PneumaticImpactModel() {}

    public static Result evaluate(
            double impactVelocity,
            double pressure,
            double criticalPressure,
            double leakThreshold,
            double ruptureThreshold) {
        double speed = Double.isFinite(impactVelocity) ? Math.abs(impactVelocity) : 0.0D;
        double absolutePressure = Double.isFinite(pressure) ? Math.abs(pressure) : 0.0D;
        double absoluteCritical = Double.isFinite(criticalPressure) ? Math.abs(criticalPressure) : 0.0D;
        double impact = speed * speed / 16.0D;
        double pressureRatio = absoluteCritical > 0.0D
                ? Math.clamp(absolutePressure / absoluteCritical, 0.0D, 2.0D)
                : 0.0D;
        double severity = impact * (0.5D + 0.5D * pressureRatio);

        boolean criticalImpact = impact >= 1.0D
                && absoluteCritical > 0.0D
                && absolutePressure >= absoluteCritical;
        if (criticalImpact || severity >= ruptureThreshold) {
            return new Result(Action.RUPTURE, impact, pressureRatio, severity);
        }
        if (severity >= leakThreshold) {
            return new Result(Action.LEAK, impact, pressureRatio, severity);
        }
        return new Result(Action.NONE, impact, pressureRatio, severity);
    }
}
