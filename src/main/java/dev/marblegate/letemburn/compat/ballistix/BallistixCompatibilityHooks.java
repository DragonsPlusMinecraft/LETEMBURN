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

package dev.marblegate.letemburn.compat.ballistix;

import ballistix.common.blast.util.Blast;
import dev.marblegate.letemburn.LetEmBurnConfig;
import dev.marblegate.letemburn.compat.core.EffectCancelledException;
import dev.marblegate.letemburn.compat.core.ExplosionImpulseBridge;
import dev.marblegate.letemburn.compat.core.ExplosionImpulseBridge.ApplicationResult;
import dev.marblegate.letemburn.compat.core.ExplosionImpulseProfile;
import dev.marblegate.letemburn.compat.core.TransactionalEffect;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHooks;

public final class BallistixCompatibilityHooks {
    private static final ThreadLocal<ProjectedCommit> ACTIVE_PROJECTED_COMMIT = new ThreadLocal<>();

    private BallistixCompatibilityHooks() {}

    public static void performProjectedNativeBlast(
            Blast blast, TransactionalEffect.CommitMarker marker, Runnable consumePayload)
            throws EffectCancelledException {
        if (ACTIVE_PROJECTED_COMMIT.get() != null) {
            throw new IllegalStateException("Nested projected Ballistix blast transaction");
        }
        ACTIVE_PROJECTED_COMMIT.set(new ProjectedCommit(marker, consumePayload));
        try {
            blast.performExplosion();
            if (!marker.nativeEffectStarted()) {
                throw new EffectCancelledException("Ballistix native blast was cancelled before it started");
            }
        } finally {
            ACTIVE_PROJECTED_COMMIT.remove();
        }
    }

    public static ApplicationResult onNativeBlastStarted(Blast blast) {
        ProjectedCommit commit = ACTIVE_PROJECTED_COMMIT.get();
        if (commit != null) {
            commit.consumePayload().run();
            commit.marker().markNativeEffectStarted();
        }
        if (!(blast.world instanceof ServerLevel level)) {
            return ApplicationResult.NO_BODIES;
        }
        ExplosionImpulseProfile profile = BallistixImpulseProfiles.resolve(blast.getBlastType());
        if (profile == null) {
            return ApplicationResult.NONE_PROFILE;
        }
        ApplicationResult result = ExplosionImpulseBridge.INSTANCE.applyOnce(
                level,
                blast,
                blast.position.getCenter(),
                profile,
                LetEmBurnConfig.BALLISTIX_IMPULSE_COEFFICIENT.get(),
                LetEmBurnConfig.BALLISTIX_OCCLUDED_FACTOR.get(),
                LetEmBurnConfig.BALLISTIX_MAX_DELTA_V.get());
        if (GameTestHooks.isGametestServer()) {
            BallistixImpactAudit.recordBridge(blast.getBlastType().id(), blast.position, result);
        }
        return result;
    }

    private record ProjectedCommit(
            TransactionalEffect.CommitMarker marker, Runnable consumePayload) {}
}
