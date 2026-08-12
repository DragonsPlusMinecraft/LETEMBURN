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

import dev.marblegate.letemburn.LetEmBurn;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class ChainReactionCoordinator {
    public static final ChainReactionCoordinator INSTANCE = new ChainReactionCoordinator();

    private final Map<ServerLevel, DeferredEffectQueue<EffectKey>> queues = Collections.synchronizedMap(new IdentityHashMap<>());
    private boolean registered;

    private ChainReactionCoordinator() {}

    public synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SableEventPlatform.INSTANCE.onPostPhysicsTick(this::postPhysicsTick);
        NeoForge.EVENT_BUS.addListener(this::levelUnload);
    }

    public ImpactStatus reserve(
            ProjectedEffectContext context,
            String payloadFingerprint,
            TransactionalEffect effect,
            Runnable rollback) {
        EffectKey key = context.key(payloadFingerprint);
        DeferredEffectQueue<EffectKey> queue = queues.computeIfAbsent(context.level(), ignored -> new DeferredEffectQueue<>());
        return queue.reserve(context.level().getGameTime(), key, effect, rollback)
                ? ImpactStatus.QUEUED
                : ImpactStatus.CONSUMED;
    }

    public int pendingCount(ServerLevel level) {
        DeferredEffectQueue<EffectKey> queue = queues.get(level);
        return queue == null ? 0 : queue.pendingCount();
    }

    public boolean cancel(ProjectedEffectContext context, String payloadFingerprint) {
        DeferredEffectQueue<EffectKey> queue = queues.get(context.level());
        return queue != null && queue.cancel(context.key(payloadFingerprint));
    }

    private void postPhysicsTick(SubLevelPhysicsSystem physicsSystem, double timeStep) {
        DeferredEffectQueue<EffectKey> queue = queues.get(physicsSystem.getLevel());
        if (queue == null) {
            return;
        }
        List<DeferredEffectQueue.Failure<EffectKey>> failures = queue.drain();
        for (DeferredEffectQueue.Failure<EffectKey> failure : failures) {
            LetEmBurn.LOGGER.error(
                    "Failed to commit projected destructive effect {} (rollback attempted: {})",
                    failure.key(),
                    failure.rollbackAttempted(),
                    failure.commitFailure());
            if (failure.rollbackFailure() != null) {
                LetEmBurn.LOGGER.error("Failed to restore projected payload {}", failure.key(), failure.rollbackFailure());
            }
        }
    }

    private void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            queues.remove(level);
        }
    }
}
