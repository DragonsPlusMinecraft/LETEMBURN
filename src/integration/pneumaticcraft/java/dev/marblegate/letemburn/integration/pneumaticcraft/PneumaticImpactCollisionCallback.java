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

import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.config.LetEmBurnConfig;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class PneumaticImpactCollisionCallback implements BlockSubLevelCollisionCallback {
    public static final PneumaticImpactCollisionCallback INSTANCE = new PneumaticImpactCollisionCallback();

    private PneumaticImpactCollisionCallback() {}

    @Override
    public CollisionResult sable$onCollision(
            BlockPos blockPosition,
            @Nullable BlockPos otherBlockPosition,
            Vector3d impactPosition,
            double impactVelocity) {
        ProjectedEffectContext context = ProjectedEffectContext.fromCollision(
                blockPosition, otherBlockPosition, impactPosition, impactVelocity);
        if (context != null) {
            handle(context);
        }
        return CollisionResult.NONE;
    }

    public PneumaticImpactModel.Action handle(ProjectedEffectContext context) {
        Direction face = nearestDirection(context.localImpactNormal());
        BlockEntity blockEntity = context.level().getBlockEntity(context.localBlockPosition());
        if (blockEntity == null) {
            return PneumaticImpactModel.Action.NONE;
        }
        IAirHandlerMachine handler = PneumaticAirHandlers.resolve(blockEntity, face);
        if (handler == null) {
            return PneumaticImpactModel.Action.NONE;
        }

        PneumaticImpactModel.Result result = PneumaticImpactModel.evaluate(
                context.impactVelocity(),
                handler.getPressure(),
                handler.getCriticalPressure(),
                LetEmBurnConfig.PNEUMATIC_LEAK_SEVERITY.get(),
                LetEmBurnConfig.PNEUMATIC_RUPTURE_SEVERITY.get());
        if (result.action() != PneumaticImpactModel.Action.NONE) {
            PneumaticImpactCoordinator.INSTANCE.enqueue(context, face, result);
        }
        return result.action();
    }

    private static Direction nearestDirection(net.minecraft.world.phys.Vec3 normal) {
        double absX = Math.abs(normal.x);
        double absY = Math.abs(normal.y);
        double absZ = Math.abs(normal.z);
        if (absX >= absY && absX >= absZ) {
            return normal.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (absY >= absZ) {
            return normal.y >= 0.0D ? Direction.UP : Direction.DOWN;
        }
        return normal.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }
}
