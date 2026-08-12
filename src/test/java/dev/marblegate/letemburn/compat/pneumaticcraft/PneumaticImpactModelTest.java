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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PneumaticImpactModelTest {
    @Test
    void leakBoundaryIsInclusive() {
        double exactSpeed = Math.sqrt(32.0D);
        assertEquals(
                PneumaticImpactModel.Action.NONE,
                PneumaticImpactModel.evaluate(
                        Math.nextDown(exactSpeed), 0.0D, 5.0D, 1.0D, 2.25D)
                        .action());
        assertEquals(
                PneumaticImpactModel.Action.LEAK,
                PneumaticImpactModel.evaluate(exactSpeed, 0.0D, 5.0D, 1.0D, 2.25D)
                        .action());
    }

    @Test
    void ruptureBoundaryIsInclusive() {
        double exactSpeed = Math.sqrt(72.0D);
        assertEquals(
                PneumaticImpactModel.Action.LEAK,
                PneumaticImpactModel.evaluate(
                        Math.nextDown(exactSpeed), 0.0D, 5.0D, 1.0D, 2.25D)
                        .action());
        assertEquals(
                PneumaticImpactModel.Action.RUPTURE,
                PneumaticImpactModel.evaluate(
                        Math.nextUp(exactSpeed), 0.0D, 5.0D, 1.0D, 2.25D)
                        .action());
    }

    @Test
    void criticalPressureRupturesAtUnitImpact() {
        PneumaticImpactModel.Result result = PneumaticImpactModel.evaluate(4.0D, 5.0D, 5.0D, 1.0D, 2.25D);
        assertEquals(1.0D, result.impact());
        assertEquals(1.0D, result.pressureRatio());
        assertEquals(PneumaticImpactModel.Action.RUPTURE, result.action());
    }

    @Test
    void pressureRatioIsClampedToTwo() {
        PneumaticImpactModel.Result result = PneumaticImpactModel.evaluate(1.0D, -50.0D, 5.0D, 1.0D, 2.25D);
        assertEquals(2.0D, result.pressureRatio());
        assertEquals(3.0D / 32.0D, result.severity());
    }
}
