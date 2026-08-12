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

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public record ProjectedEffectContext(
        ServerLevel level,
        ServerSubLevel subLevel,
        BlockPos localBlockPosition,
        @Nullable BlockPos otherBlockPosition,
        Vec3 localImpactPosition,
        Vec3 globalImpactPosition,
        Vec3 localImpactNormal,
        double impactVelocity,
        @Nullable Entity owner) {
    public ProjectedEffectContext {
        localBlockPosition = localBlockPosition.immutable();
        otherBlockPosition = otherBlockPosition == null ? null : otherBlockPosition.immutable();
    }

    public static @Nullable ProjectedEffectContext fromCollision(
            BlockPos blockPosition,
            @Nullable BlockPos otherBlockPosition,
            Vector3d impactPosition,
            double impactVelocity) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (system == null) {
            return null;
        }
        ServerLevel level = system.getLevel();
        SubLevel containing = Sable.HELPER.getContaining(level, blockPosition);
        if (!(containing instanceof ServerSubLevel subLevel)) {
            return null;
        }

        Vec3 localImpact = new Vec3(impactPosition.x, impactPosition.y, impactPosition.z);
        Vec3 globalImpact = Sable.HELPER.projectOutOfSubLevel(level, localImpact);
        return new ProjectedEffectContext(
                level,
                subLevel,
                blockPosition,
                otherBlockPosition,
                localImpact,
                globalImpact,
                inferImpactNormal(blockPosition, localImpact),
                impactVelocity,
                null);
    }

    public EffectKey key(String payloadFingerprint) {
        return new EffectKey(
                level.dimension(),
                subLevel.getUniqueId(),
                localBlockPosition,
                level.getGameTime(),
                payloadFingerprint);
    }

    private static Vec3 inferImpactNormal(BlockPos blockPosition, Vec3 impactPosition) {
        double x = impactPosition.x - (blockPosition.getX() + 0.5D);
        double y = impactPosition.y - (blockPosition.getY() + 0.5D);
        double z = impactPosition.z - (blockPosition.getZ() + 0.5D);
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);
        if (absX >= absY && absX >= absZ) {
            return new Vec3(Math.copySign(1.0D, x == 0.0D ? 1.0D : x), 0.0D, 0.0D);
        }
        if (absY >= absZ) {
            return new Vec3(0.0D, Math.copySign(1.0D, y == 0.0D ? 1.0D : y), 0.0D);
        }
        return new Vec3(0.0D, 0.0D, Math.copySign(1.0D, z == 0.0D ? 1.0D : z));
    }
}
