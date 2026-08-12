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

import dev.marblegate.letemburn.compat.pneumaticcraft.PneumaticBlockSupport;
import dev.marblegate.letemburn.compat.pneumaticcraft.PneumaticImpactCollisionCallback;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import me.desht.pneumaticcraft.common.block.PneumaticCraftEntityBlock;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;

@Restriction(require = {
        @Condition("sable"),
        @Condition("pneumaticcraft") })
@Mixin(value = PneumaticCraftEntityBlock.class, remap = false)
public interface PneumaticCraftEntityBlockMixin extends BlockWithSubLevelCollisionCallback {
    @Override
    default BlockSubLevelCollisionCallback sable$getCallback() {
        return PneumaticBlockSupport.exposesMachineAirHandler((Block) this)
                ? PneumaticImpactCollisionCallback.INSTANCE
                : null;
    }
}
