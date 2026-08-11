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

import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore.ReactorState;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class ReleaseDirectReactor implements BlockSubLevelCollisionCallback {
    public static final ReleaseDirectReactor INSTANCE = new ReleaseDirectReactor();

    private ReleaseDirectReactor() {}

    @Override
    public CollisionResult sable$onCollision(
            BlockPos blockPos, @Nullable BlockPos otherBlockPos, Vector3d pos, double impactVelocity) {
        if (!DraconicReactorImpact.isHardEnough(impactVelocity)) {
            return CollisionResult.NONE;
        }

        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        ServerLevel level = system.getLevel();
        if (!(level.getBlockEntity(blockPos) instanceof TileReactorCore reactor)
                || reactor.reactorState.get() != ReactorState.BEYOND_HOPE) {
            return CollisionResult.NONE;
        }

        return DraconicReactorImpact.detonate(
                blockPos, reactor.convertedFuel.get(), reactor.reactableFuel.get());
    }
}
