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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

final class PneumaticAirHandlers {
    private PneumaticAirHandlers() {}

    static @Nullable IAirHandlerMachine resolve(BlockEntity blockEntity, Direction face) {
        return PNCCapabilities.getAirHandler(blockEntity, face).orElse(null);
    }

    static List<IAirHandlerMachine> unique(BlockEntity blockEntity) {
        Set<IAirHandlerMachine> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        PNCCapabilities.getAirHandler(blockEntity).ifPresent(identities::add);
        for (Direction direction : Direction.values()) {
            PNCCapabilities.getAirHandler(blockEntity, direction).ifPresent(identities::add);
        }
        return new ArrayList<>(identities);
    }
}
