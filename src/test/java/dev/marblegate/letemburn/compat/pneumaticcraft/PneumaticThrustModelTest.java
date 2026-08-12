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

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class PneumaticThrustModelTest {
    @Test
    void positivePressureThrustOpposesOutwardFlow() {
        var impulse = PneumaticThrustModel.rawImpulse(
                Direction.EAST, 2.0D, 100.0D, 0.025D, 5.0E-5D);
        assertEquals(-0.005D, impulse.x, 1.0E-15D);
        assertEquals(0.0D, impulse.y, 0.0D);
        assertEquals(0.0D, impulse.z, 0.0D);
    }

    @Test
    void negativePressureReversesThrustDirection() {
        var impulse = PneumaticThrustModel.rawImpulse(
                Direction.EAST, -2.0D, 100.0D, 0.025D, 5.0E-5D);
        assertEquals(0.005D, impulse.x, 1.0E-15D);
        assertEquals(0.0D, impulse.y, 0.0D);
        assertEquals(0.0D, impulse.z, 0.0D);
    }

    @Test
    void forceUsesActualLeakRate() {
        assertEquals(0.2D, PneumaticThrustModel.forceMagnitude(2.0D, 100.0D, 5.0E-5D), 1.0E-15D);
    }

    @Test
    void aggregateImpulseIsCappedByDeltaVelocity() {
        assertEquals(0.5D, PneumaticThrustModel.capScale(1.0D, 1.0D, 0.5D));
        assertEquals(1.0D, PneumaticThrustModel.capScale(0.25D, 1.0D, 0.5D));
    }
}
