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

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;

/** Adds one bounded Sable rigid-body impulse alongside, never in place of, a native explosion. */
public final class ExplosionImpulseBridge {
    public static final ExplosionImpulseBridge INSTANCE = new ExplosionImpulseBridge();

    private final Map<ServerLevel, TickIdentitySet> applied = Collections.synchronizedMap(new IdentityHashMap<>());

    private ExplosionImpulseBridge() {}

    public ApplicationResult applyOnce(
            ServerLevel level,
            Object nativeExplosion,
            Vec3 origin,
            ExplosionImpulseProfile profile,
            double coefficient,
            double occludedFactor,
            double maxDeltaVelocity) {
        if (profile.direction() == ExplosionImpulseProfile.Direction.NONE || profile.radius() == 0.0D) {
            return ApplicationResult.NONE_PROFILE;
        }
        if (!reserveIdentity(level, nativeExplosion)) {
            return ApplicationResult.DEDUPLICATED;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || container.physicsSystem() == null) {
            return ApplicationResult.NO_BODIES;
        }
        SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        int affectedBodies = 0;
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (applyToBody(
                    level,
                    physicsSystem,
                    subLevel,
                    origin,
                    profile,
                    coefficient,
                    occludedFactor,
                    maxDeltaVelocity)) {
                affectedBodies++;
            }
        }
        return affectedBodies == 0
                ? ApplicationResult.NO_BODIES
                : new ApplicationResult(true, false, affectedBodies);
    }

    private boolean applyToBody(
            ServerLevel level,
            SubLevelPhysicsSystem physicsSystem,
            ServerSubLevel subLevel,
            Vec3 origin,
            ExplosionImpulseProfile profile,
            double coefficient,
            double occludedFactor,
            double maxDeltaVelocity) {
        if (subLevel.isRemoved()) {
            return false;
        }
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) {
            return false;
        }

        Vec3 localOrigin = subLevel.logicalPose().transformPositionInverse(origin);
        Vec3 localNearest = new Vec3(
                Mth.clamp(localOrigin.x, bounds.minX(), bounds.maxX() + 1.0D),
                Mth.clamp(localOrigin.y, bounds.minY(), bounds.maxY() + 1.0D),
                Mth.clamp(localOrigin.z, bounds.minZ(), bounds.maxZ() + 1.0D));
        Vec3 globalNearest = subLevel.logicalPose().transformPosition(localNearest);
        double distance = origin.distanceTo(globalNearest);
        if (!(distance < profile.radius())) {
            return false;
        }

        double mass = subLevel.getMassTracker().getMass();
        boolean occluded = isOccluded(level, origin, globalNearest);
        double impulseMagnitude = calculateImpulseMagnitude(
                mass,
                profile.radius(),
                distance,
                coefficient,
                occluded ? occludedFactor : 1.0D,
                maxDeltaVelocity);
        if (!(impulseMagnitude > 0.0D)) {
            return false;
        }

        Vec3 globalDirection = direction(profile.direction(), origin, globalNearest, subLevel);
        Vec3 localImpulse = subLevel.logicalPose()
                .transformNormalInverse(globalDirection.scale(impulseMagnitude));
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        if (!handle.isValid()) {
            return false;
        }
        Vector3d impulse = new Vector3d(localImpulse.x, localImpulse.y, localImpulse.z);
        Vector3d centerOfMass = new Vector3d(subLevel.getMassTracker().getCenterOfMass());
        Vector3d torque = new Vector3d(
                localNearest.x - centerOfMass.x,
                localNearest.y - centerOfMass.y,
                localNearest.z - centerOfMass.z)
                        .cross(impulse, new Vector3d());
        handle.applyLinearAndAngularImpulse(impulse, torque);
        return true;
    }

    private static Vec3 direction(
            ExplosionImpulseProfile.Direction direction,
            Vec3 origin,
            Vec3 nearest,
            ServerSubLevel subLevel) {
        if (direction == ExplosionImpulseProfile.Direction.UPWARD) {
            return new Vec3(0.0D, 1.0D, 0.0D);
        }
        Vec3 radial = nearest.subtract(origin);
        if (radial.lengthSqr() < 1.0E-12D) {
            Vector3d center = subLevel.boundingBox().center(new Vector3d());
            radial = new Vec3(center.x - origin.x, center.y - origin.y, center.z - origin.z);
        }
        if (radial.lengthSqr() < 1.0E-12D) {
            radial = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            radial = radial.normalize();
        }
        return direction == ExplosionImpulseProfile.Direction.INWARD ? radial.reverse() : radial;
    }

    private static boolean isOccluded(ServerLevel level, Vec3 origin, Vec3 target) {
        if (origin.distanceToSqr(target) < 1.0E-12D) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(
                origin,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return hit.getType() != HitResult.Type.MISS
                && hit.getLocation().distanceToSqr(target) > 1.0E-6D;
    }

    public static double calculateImpulseMagnitude(
            double bodyMass,
            double radius,
            double distance,
            double coefficient,
            double occlusion,
            double maxDeltaVelocity) {
        if (!(bodyMass > 0.0D)
                || !(radius > 0.0D)
                || distance >= radius
                || coefficient <= 0.0D
                || occlusion <= 0.0D
                || maxDeltaVelocity <= 0.0D) {
            return 0.0D;
        }
        double falloff = 1.0D - Math.max(0.0D, distance) / radius;
        return Math.min(
                bodyMass * maxDeltaVelocity,
                coefficient * radius * falloff * falloff * occlusion);
    }

    private boolean reserveIdentity(ServerLevel level, Object nativeExplosion) {
        synchronized (applied) {
            TickIdentitySet state = applied.computeIfAbsent(level, ignored -> new TickIdentitySet());
            long gameTime = level.getGameTime();
            if (state.gameTime != gameTime) {
                state.gameTime = gameTime;
                state.identities.clear();
            }
            return state.identities.add(nativeExplosion);
        }
    }

    void clearLevel(ServerLevel level) {
        applied.remove(level);
    }

    public record ApplicationResult(boolean considered, boolean deduplicated, int affectedBodies) {
        public static final ApplicationResult NONE_PROFILE = new ApplicationResult(false, false, 0);
        public static final ApplicationResult DEDUPLICATED = new ApplicationResult(true, true, 0);
        public static final ApplicationResult NO_BODIES = new ApplicationResult(true, false, 0);
    }

    private static final class TickIdentitySet {
        private long gameTime = Long.MIN_VALUE;
        private final Set<Object> identities = Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
