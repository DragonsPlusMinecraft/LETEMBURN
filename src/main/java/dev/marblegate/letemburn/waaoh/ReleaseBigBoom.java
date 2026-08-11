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

package dev.marblegate.letemburn.waaoh;

import static com.brandon3055.draconicevolution.init.DEContent.REACTOR_CORE;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.Optional;
import mekanism.common.attachments.BlockData;
import mekanism.common.block.BlockCardboardBox;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tile.TileEntityCardboardBox;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Unique;

@Unique
public class ReleaseBigBoom implements BlockSubLevelCollisionCallback {
    public static final ReleaseBigBoom INSTANCE = new ReleaseBigBoom();

    @Override
    public CollisionResult sable$onCollision(
            BlockPos blockPos, @Nullable BlockPos otherBlockPos, Vector3d pos, double impactVelocity) {
        if (!DraconicReactorImpact.isHardEnough(impactVelocity)) {
            return CollisionResult.NONE;
        }

        final SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        final ServerLevel level = system.getLevel();

        // double check
        if (!(level.getBlockState(blockPos).getBlock() instanceof BlockCardboardBox))
            return CollisionResult.NONE;

        Optional<BlockData> inside = Optional.ofNullable(WorldUtils.getTileEntity(TileEntityCardboardBox.class, level, blockPos))
                .map((box) -> box.components().get(MekanismDataComponents.BLOCK_DATA.value()));

        if (inside.isPresent()) {
            var block = inside.get().blockState();
            if (block.is(REACTOR_CORE)) {
                var nbt = inside.get().blockEntityTag().getCompound("bc_managed_data");
                if (nbt.getInt("explosion_countdown") > -1) {
                    var convertedFuel = nbt.getDouble("converted_fuel");
                    var reactableFuel = nbt.getDouble("reactable_fuel");
                    return DraconicReactorImpact.detonate(blockPos, convertedFuel, reactableFuel);
                }
            }
        }
        return CollisionResult.NONE;
    }
}
