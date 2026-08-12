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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PneumaticImpactAudit {
    private static final List<Event> EVENTS = new ArrayList<>();

    private PneumaticImpactAudit() {}

    static synchronized void record(Vec3 position, PneumaticImpactModel.Action action) {
        EVENTS.add(new Event(position, action));
    }

    public static synchronized int countWithin(AABB bounds, PneumaticImpactModel.Action action) {
        return (int) EVENTS.stream()
                .filter(event -> event.action() == action && bounds.contains(event.position()))
                .count();
    }

    public static synchronized void clearWithin(AABB bounds) {
        EVENTS.removeIf(event -> bounds.contains(event.position()));
    }

    private record Event(Vec3 position, PneumaticImpactModel.Action action) {}
}
