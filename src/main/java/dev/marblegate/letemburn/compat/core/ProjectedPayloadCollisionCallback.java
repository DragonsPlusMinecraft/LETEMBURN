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

package dev.marblegate.letemburn.compat.core;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class ProjectedPayloadCollisionCallback implements BlockSubLevelCollisionCallback {
    public static final ProjectedPayloadCollisionCallback INSTANCE = new ProjectedPayloadCollisionCallback();

    private static final CollisionResult REMOVE_COLLISION = new CollisionResult(JOMLConversion.ZERO, true);

    private ProjectedPayloadCollisionCallback() {}

    @Override
    public CollisionResult sable$onCollision(
            BlockPos blockPosition,
            @Nullable BlockPos otherBlockPosition,
            Vector3d impactPosition,
            double impactVelocity) {
        ProjectedEffectContext context = ProjectedEffectContext.fromCollision(
                blockPosition, otherBlockPosition, impactPosition, impactVelocity);
        if (context == null) {
            return CollisionResult.NONE;
        }
        PayloadEnvelopeResolver.Resolution resolution = PayloadEnvelopeResolver.INSTANCE.resolve(
                context.level(), context.localBlockPosition());
        if (!resolution.valid()) {
            return CollisionResult.NONE;
        }
        ImpactStatus status = ImpactPayloadRegistry.INSTANCE.dispatch(context, resolution.snapshot());
        return status == ImpactStatus.QUEUED || status == ImpactStatus.CONSUMED
                ? REMOVE_COLLISION
                : CollisionResult.NONE;
    }
}
