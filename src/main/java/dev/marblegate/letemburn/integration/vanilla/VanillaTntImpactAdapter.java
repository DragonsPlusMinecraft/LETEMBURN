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

package dev.marblegate.letemburn.integration.vanilla;

import dev.marblegate.letemburn.common.effect.TransactionalEffect;
import dev.marblegate.letemburn.common.impact.ImpactPayloadAdapter;
import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.common.payload.PayloadSnapshot;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHooks;

public final class VanillaTntImpactAdapter implements ImpactPayloadAdapter {
    public static final VanillaTntImpactAdapter INSTANCE = new VanillaTntImpactAdapter();
    public static final double IMPACT_SPEED = 5.0D;

    private VanillaTntImpactAdapter() {}

    @Override
    public Probe probe(ProjectedEffectContext context, PayloadSnapshot payload) {
        if (!payload.payloadState().is(Blocks.TNT)) {
            return Probe.notHandled();
        }
        if (context.impactVelocity() * context.impactVelocity() < IMPACT_SPEED * IMPACT_SPEED) {
            return Probe.belowThreshold("minecraft-tnt");
        }
        return Probe.ready("minecraft-tnt", null);
    }

    @Override
    public void commit(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            Probe probe,
            TransactionalEffect.CommitMarker marker) {
        Vec3 spawnPosition = context.projectLocalPosition(new Vec3(
                context.localBlockPosition().getX() + 0.5D,
                context.localBlockPosition().getY(),
                context.localBlockPosition().getZ() + 0.5D));
        PrimedTnt primedTnt = new PrimedTnt(
                context.level(), spawnPosition.x, spawnPosition.y, spawnPosition.z, null);
        primedTnt.setBlockState(payload.payloadState());
        primedTnt.setFuse(4);
        if (!context.level().addFreshEntity(primedTnt)) {
            throw new IllegalStateException("Failed to add projected TNT entity");
        }
        if (!payload.removeOuter(context.level(), context.localBlockPosition())) {
            primedTnt.discard();
            throw new IllegalStateException("Failed to consume projected TNT payload");
        }
        marker.markNativeEffectStarted();
        if (GameTestHooks.isGametestServer()) {
            VanillaTntImpactAudit.record(spawnPosition, context.globalImpactPosition());
        }
    }
}
