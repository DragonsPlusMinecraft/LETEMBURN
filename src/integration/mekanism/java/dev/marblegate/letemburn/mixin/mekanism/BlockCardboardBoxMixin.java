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

package dev.marblegate.letemburn.mixin.mekanism;

import dev.marblegate.letemburn.common.impact.ProjectedPayloadCollisionCallback;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import mekanism.common.block.BlockCardboardBox;
import mekanism.common.block.BlockMekanism;
import mekanism.common.block.interfaces.IHasTileEntity;
import mekanism.common.block.states.IStateStorage;
import mekanism.common.tile.TileEntityCardboardBox;
import org.spongepowered.asm.mixin.Mixin;

@Restriction(require = {
        @Condition("sable"),
        @Condition("mekanism") })
@Mixin(BlockCardboardBox.class)
public abstract class BlockCardboardBoxMixin extends BlockMekanism implements IStateStorage, IHasTileEntity<TileEntityCardboardBox>, BlockWithSubLevelCollisionCallback {
    protected BlockCardboardBoxMixin(Properties properties) {
        super(properties);
    }

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return ProjectedPayloadCollisionCallback.INSTANCE;
    }
}
