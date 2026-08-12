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

package dev.marblegate.letemburn.common.impulse;

import java.util.Objects;

public record ExplosionImpulseProfile(String id, Direction direction, double radius) {
    public ExplosionImpulseProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        if (!Double.isFinite(radius) || radius < 0.0D) {
            throw new IllegalArgumentException("Explosion impulse radius must be finite and non-negative");
        }
        if (direction == Direction.NONE && radius != 0.0D) {
            throw new IllegalArgumentException("A NONE impulse profile must have a zero radius");
        }
    }

    public static ExplosionImpulseProfile none(String id) {
        return new ExplosionImpulseProfile(id, Direction.NONE, 0.0D);
    }

    public enum Direction {
        NONE,
        OUTWARD,
        INWARD,
        UPWARD
    }
}
