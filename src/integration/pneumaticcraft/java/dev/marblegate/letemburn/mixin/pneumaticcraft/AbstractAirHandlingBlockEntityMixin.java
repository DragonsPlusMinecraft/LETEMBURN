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

package dev.marblegate.letemburn.mixin.pneumaticcraft;

import dev.marblegate.letemburn.integration.pneumaticcraft.PneumaticLeakThrust;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.desht.pneumaticcraft.common.block.entity.AbstractAirHandlingBlockEntity;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;

@Restriction(require = {
        @Condition("sable"),
        @Condition("pneumaticcraft") })
@Mixin(AbstractAirHandlingBlockEntity.class)
public abstract class AbstractAirHandlingBlockEntityMixin implements BlockEntitySubLevelActor {
    @Override
    public void sable$physicsTick(
            ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        PneumaticLeakThrust.apply((AbstractAirHandlingBlockEntity) (Object) this, subLevel, timeStep);
    }
}
