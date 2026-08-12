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
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Immutable collision data captured while Sable still owns the projected sublevel.
 *
 * <p>Removing a one-block payload can immediately invalidate and unregister its sublevel. Deferred effects must
 * therefore use the frozen pose below instead of looking the sublevel up again during the post-physics phase.
 */
public final class ProjectedEffectContext {
    private final ServerLevel level;
    private final ServerSubLevel subLevel;
    private final UUID subLevelId;
    private final BlockPos localBlockPosition;
    private final @Nullable BlockPos otherBlockPosition;
    private final Vec3 localImpactPosition;
    private final Vec3 globalImpactPosition;
    private final Vec3 localImpactNormal;
    private final double impactVelocity;
    private final @Nullable Entity owner;
    private final Pose3d projectionPose;

    public ProjectedEffectContext(
            ServerLevel level,
            ServerSubLevel subLevel,
            BlockPos localBlockPosition,
            @Nullable BlockPos otherBlockPosition,
            Vec3 localImpactPosition,
            Vec3 globalImpactPosition,
            Vec3 localImpactNormal,
            double impactVelocity,
            @Nullable Entity owner) {
        this(
                level,
                subLevel,
                localBlockPosition,
                otherBlockPosition,
                localImpactPosition,
                globalImpactPosition,
                localImpactNormal,
                impactVelocity,
                owner,
                new Pose3d(subLevel.logicalPose()));
    }

    private ProjectedEffectContext(
            ServerLevel level,
            ServerSubLevel subLevel,
            BlockPos localBlockPosition,
            @Nullable BlockPos otherBlockPosition,
            Vec3 localImpactPosition,
            Vec3 globalImpactPosition,
            Vec3 localImpactNormal,
            double impactVelocity,
            @Nullable Entity owner,
            Pose3d projectionPose) {
        this.level = level;
        this.subLevel = subLevel;
        this.subLevelId = subLevel.getUniqueId();
        this.localBlockPosition = localBlockPosition.immutable();
        this.otherBlockPosition = otherBlockPosition == null ? null : otherBlockPosition.immutable();
        this.localImpactPosition = localImpactPosition;
        this.globalImpactPosition = globalImpactPosition;
        this.localImpactNormal = localImpactNormal;
        this.impactVelocity = impactVelocity;
        this.owner = owner;
        this.projectionPose = new Pose3d(projectionPose);
    }

    public static @Nullable ProjectedEffectContext fromCollision(
            BlockPos blockPosition,
            @Nullable BlockPos otherBlockPosition,
            Vector3d impactPosition,
            double impactVelocity) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        ServerLevel level = system.getLevel();
        SubLevel containing = Sable.HELPER.getContaining(level, blockPosition);
        if (!(containing instanceof ServerSubLevel subLevel)) {
            return null;
        }

        Pose3d pose = new Pose3d(subLevel.logicalPose());
        Vec3 localImpact = new Vec3(impactPosition.x, impactPosition.y, impactPosition.z);
        Vec3 globalImpact = JOMLConversion.toMojang(
                pose.transformPosition(JOMLConversion.toJOML(localImpact), new Vector3d()));
        return new ProjectedEffectContext(
                level,
                subLevel,
                blockPosition,
                otherBlockPosition,
                localImpact,
                globalImpact,
                inferImpactNormal(blockPosition, localImpact),
                impactVelocity,
                null,
                pose);
    }

    public ServerLevel level() {
        return level;
    }

    public ServerSubLevel subLevel() {
        return subLevel;
    }

    public BlockPos localBlockPosition() {
        return localBlockPosition;
    }

    public @Nullable BlockPos otherBlockPosition() {
        return otherBlockPosition;
    }

    public Vec3 localImpactPosition() {
        return localImpactPosition;
    }

    public Vec3 globalImpactPosition() {
        return globalImpactPosition;
    }

    public Vec3 localImpactNormal() {
        return localImpactNormal;
    }

    public double impactVelocity() {
        return impactVelocity;
    }

    public @Nullable Entity owner() {
        return owner;
    }

    public EffectKey key(String payloadFingerprint) {
        return new EffectKey(
                level.dimension(), subLevelId, localBlockPosition, level.getGameTime(), payloadFingerprint);
    }

    public Vec3 projectLocalPosition(Vec3 localPosition) {
        return JOMLConversion.toMojang(
                projectionPose.transformPosition(JOMLConversion.toJOML(localPosition), new Vector3d()));
    }

    public Vec3 projectLocalDirection(Vec3 localDirection) {
        return JOMLConversion.toMojang(
                projectionPose.orientation().transform(JOMLConversion.toJOML(localDirection), new Vector3d()));
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
