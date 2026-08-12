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

package dev.marblegate.letemburn.compat.ballistix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ballistix.common.block.subtype.SubtypeBlast;
import dev.marblegate.letemburn.compat.core.ExplosionImpulseProfile.Direction;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BallistixImpulseProfilesTest {
    @Test
    void everyPublishedBlastIdHasAnExplicitProfile() {
        assertEquals(SubtypeBlast.values().length, BallistixImpulseProfiles.explicitProfileCount());
        for (SubtypeBlast type : SubtypeBlast.values()) {
            assertTrue(BallistixImpulseProfiles.hasExplicitProfile(type.id()), type.id().toString());
        }
    }

    @Test
    void stateSpawnAndRepairEffectsAreExplicitlyNone() {
        Set<SubtypeBlast> none = Set.of(
                SubtypeBlast.incendiary,
                SubtypeBlast.shrapnel,
                SubtypeBlast.chemical,
                SubtypeBlast.anvil,
                SubtypeBlast.infestive,
                SubtypeBlast.debilitation,
                SubtypeBlast.fragmentation,
                SubtypeBlast.contagious,
                SubtypeBlast.emp,
                SubtypeBlast.rejuvination,
                SubtypeBlast.landmine);
        for (SubtypeBlast type : none) {
            assertEquals(Direction.NONE, BallistixImpulseProfiles.directionOf(type), type.id().toString());
        }
    }

    @Test
    void directionalNativeEffectsRetainTheirDirection() {
        assertEquals(Direction.INWARD, BallistixImpulseProfiles.directionOf(SubtypeBlast.attractive));
        assertEquals(Direction.OUTWARD, BallistixImpulseProfiles.directionOf(SubtypeBlast.repulsive));
        assertEquals(Direction.UPWARD, BallistixImpulseProfiles.directionOf(SubtypeBlast.antigravity));
        assertEquals(Direction.INWARD, BallistixImpulseProfiles.directionOf(SubtypeBlast.darkmatter));
    }
}
