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

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.effect.EffectKey;
import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.config.LetEmBurnConfig;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector3d;

public final class PneumaticImpactCoordinator {
    public static final PneumaticImpactCoordinator INSTANCE = new PneumaticImpactCoordinator();

    private static final String IMPACT_FINGERPRINT = "pneumaticcraft:impact";

    private final Map<ServerLevel, LinkedHashMap<EffectKey, PendingImpact>> pendingByLevel = Collections.synchronizedMap(new IdentityHashMap<>());
    private boolean registered;

    private PneumaticImpactCoordinator() {}

    public synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SableEventPlatform.INSTANCE.onPostPhysicsTick(this::postPhysicsTick);
        NeoForge.EVENT_BUS.addListener(this::levelUnload);
    }

    public synchronized void enqueue(
            ProjectedEffectContext context, Direction face, PneumaticImpactModel.Result result) {
        LinkedHashMap<EffectKey, PendingImpact> pending = pendingByLevel.computeIfAbsent(
                context.level(), ignored -> new LinkedHashMap<>());
        EffectKey key = context.key(IMPACT_FINGERPRINT);
        PendingImpact incoming = new PendingImpact(context, face, result);
        pending.merge(key, incoming, PendingImpact::stronger);
    }

    public synchronized int pendingCount(ServerLevel level) {
        Map<EffectKey, PendingImpact> pending = pendingByLevel.get(level);
        return pending == null ? 0 : pending.size();
    }

    private void postPhysicsTick(SubLevelPhysicsSystem physicsSystem, double timeStep) {
        List<PendingImpact> drained;
        synchronized (this) {
            LinkedHashMap<EffectKey, PendingImpact> pending = pendingByLevel.get(physicsSystem.getLevel());
            if (pending == null || pending.isEmpty()) {
                return;
            }
            drained = new ArrayList<>(pending.values());
            pending.clear();
        }
        for (PendingImpact impact : drained) {
            try {
                commit(physicsSystem, timeStep, impact);
            } catch (RuntimeException exception) {
                LetEmBurn.LOGGER.error(
                        "Failed to commit PneumaticCraft impact at {}",
                        impact.context().localBlockPosition(),
                        exception);
            }
        }
    }

    private static void commit(
            SubLevelPhysicsSystem physicsSystem, double timeStep, PendingImpact pendingImpact) {
        ProjectedEffectContext context = pendingImpact.context();
        if (context.subLevel().isRemoved()) {
            return;
        }
        BlockEntity blockEntity = context.level().getBlockEntity(context.localBlockPosition());
        if (blockEntity == null) {
            return;
        }
        IAirHandlerMachine impactedHandler = PneumaticAirHandlers.resolve(blockEntity, pendingImpact.face());
        if (impactedHandler == null) {
            return;
        }
        if (pendingImpact.result().action() == PneumaticImpactModel.Action.LEAK) {
            impactedHandler.setSideLeaking(pendingImpact.face());
            PneumaticImpactAudit.record(context.globalImpactPosition(), PneumaticImpactModel.Action.LEAK);
            return;
        }
        rupture(physicsSystem, timeStep, context, blockEntity, pendingImpact.face());
    }

    private static void rupture(
            SubLevelPhysicsSystem physicsSystem,
            double timeStep,
            ProjectedEffectContext context,
            BlockEntity blockEntity,
            Direction face) {
        BlockState expectedState = context.level().getBlockState(context.localBlockPosition());
        if (expectedState.isAir() || context.level().getBlockEntity(context.localBlockPosition()) != blockEntity) {
            return;
        }

        List<IAirHandlerMachine> handlers = PneumaticAirHandlers.unique(blockEntity);
        if (handlers.isEmpty()) {
            return;
        }
        Map<IAirHandlerMachine, CompoundTag> snapshots = new IdentityHashMap<>();
        Vector3d rawImpulse = new Vector3d();
        for (IAirHandlerMachine handler : handlers) {
            Tag serialized = handler.serializeNBT();
            if (!(serialized instanceof CompoundTag compoundTag)) {
                throw new IllegalStateException("PneumaticCraft machine air handler did not serialize to a compound tag");
            }
            snapshots.put(handler, compoundTag.copy());
            rawImpulse.add(PneumaticThrustModel.rawImpulse(
                    face,
                    handler.getPressure(),
                    Math.abs((long) handler.getAir()),
                    timeStep,
                    LetEmBurnConfig.PNEUMATIC_THRUST_SCALE.get()));
        }

        double bodyMass = context.subLevel().getMassTracker().getMass();
        Vector3d centerOfMass = new Vector3d(context.subLevel().getMassTracker().getCenterOfMass());
        Vector3d applicationPoint = faceCenter(context.localBlockPosition(), face);
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(context.subLevel());

        boolean removed = false;
        try {
            handlers.forEach(handler -> handler.setPressure(0.0F));
            removed = context.level().setBlock(
                    context.localBlockPosition(), Blocks.AIR.defaultBlockState(), 11);
            if (!removed) {
                throw new IllegalStateException("PneumaticCraft rupture could not remove its source block");
            }
        } finally {
            if (!removed
                    && context.level().getBlockState(context.localBlockPosition()).equals(expectedState)
                    && context.level().getBlockEntity(context.localBlockPosition()) == blockEntity) {
                snapshots.forEach(IAirHandlerMachine::deserializeNBT);
                blockEntity.setChanged();
            }
        }

        applyBoundedDischargeImpulse(handle, bodyMass, centerOfMass, applicationPoint, rawImpulse);
        PneumaticImpactAudit.record(context.globalImpactPosition(), PneumaticImpactModel.Action.RUPTURE);
    }

    private static void applyBoundedDischargeImpulse(
            RigidBodyHandle handle,
            double bodyMass,
            Vector3d centerOfMass,
            Vector3d applicationPoint,
            Vector3d rawImpulse) {
        if (!handle.isValid() || rawImpulse.lengthSquared() == 0.0D) {
            return;
        }
        double scale = PneumaticThrustModel.capScale(
                rawImpulse.length(), bodyMass, LetEmBurnConfig.PNEUMATIC_MAX_DELTA_V_PER_SUBSTEP.get());
        if (scale == 0.0D) {
            return;
        }
        Vector3d impulse = new Vector3d(rawImpulse).mul(scale);
        Vector3d torque = new Vector3d(applicationPoint).sub(centerOfMass).cross(impulse, new Vector3d());
        handle.applyLinearAndAngularImpulse(impulse, torque);
    }

    private static Vector3d faceCenter(net.minecraft.core.BlockPos position, Direction face) {
        return new Vector3d(
                position.getX() + 0.5D + face.getStepX() * 0.5D,
                position.getY() + 0.5D + face.getStepY() * 0.5D,
                position.getZ() + 0.5D + face.getStepZ() * 0.5D);
    }

    private synchronized void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            pendingByLevel.remove(level);
            PneumaticLeakTracker.clearLevel(level);
        }
    }

    private record PendingImpact(
            ProjectedEffectContext context, Direction face, PneumaticImpactModel.Result result) {
        private static PendingImpact stronger(PendingImpact first, PendingImpact second) {
            int actionComparison = Integer.compare(
                    second.result().action().ordinal(),
                    first.result().action().ordinal());
            if (actionComparison != 0) {
                return actionComparison > 0 ? second : first;
            }
            return second.result().severity() > first.result().severity() ? second : first;
        }
    }
}
