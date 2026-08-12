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

package dev.marblegate.letemburn.compat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExplosionImpulseBridgeTest {
    @Test
    void followsConfiguredFalloffFormula() {
        assertEquals(
                37.5D,
                ExplosionImpulseBridge.calculateImpulseMagnitude(
                        100.0D, 100.0D, 50.0D, 1.5D, 1.0D, 16.0D),
                0.0D);
        assertEquals(
                13.125D,
                ExplosionImpulseBridge.calculateImpulseMagnitude(
                        100.0D, 100.0D, 50.0D, 1.5D, 0.35D, 16.0D),
                0.0D);
    }

    @Test
    void capsImpulseByBodyMassTimesMaximumDeltaVelocity() {
        assertEquals(
                16.0D,
                ExplosionImpulseBridge.calculateImpulseMagnitude(
                        1.0D, 1000.0D, 0.0D, 1.5D, 1.0D, 16.0D),
                0.0D);
    }

    @Test
    void rejectsOutsideAndInvalidInputs() {
        assertEquals(
                0.0D,
                ExplosionImpulseBridge.calculateImpulseMagnitude(
                        100.0D, 10.0D, 10.0D, 1.5D, 1.0D, 16.0D),
                0.0D);
        assertEquals(
                0.0D,
                ExplosionImpulseBridge.calculateImpulseMagnitude(
                        0.0D, 10.0D, 1.0D, 1.5D, 1.0D, 16.0D),
                0.0D);
    }
}
