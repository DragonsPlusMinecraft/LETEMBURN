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

package dev.marblegate.letemburn.integration.ballistix;

import ballistix.api.blast.IBlast;
import ballistix.common.blast.util.Blast;
import ballistix.common.block.BlockExplosive;
import dev.marblegate.letemburn.common.effect.EffectCancelledException;
import dev.marblegate.letemburn.common.effect.TransactionalEffect;
import dev.marblegate.letemburn.common.impact.ImpactPayloadAdapter;
import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.common.payload.PayloadSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHooks;

public final class BallistixImpactPayloadAdapter implements ImpactPayloadAdapter {
    public static final BallistixImpactPayloadAdapter INSTANCE = new BallistixImpactPayloadAdapter();
    public static final double IMPACT_SPEED = 5.0D;

    private BallistixImpactPayloadAdapter() {}

    @Override
    public Probe probe(ProjectedEffectContext context, PayloadSnapshot payload) {
        if (!(payload.payloadState().getBlock() instanceof BlockExplosive block)) {
            return Probe.notHandled();
        }
        IBlast blastType = block.explosive;
        String suffix = "ballistix:" + blastType.id();
        if (context.impactVelocity() * context.impactVelocity() < IMPACT_SPEED * IMPACT_SPEED) {
            return Probe.belowThreshold(suffix);
        }
        return Probe.ready(suffix, blastType);
    }

    @Override
    public void commit(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            Probe probe,
            TransactionalEffect.CommitMarker marker)
            throws EffectCancelledException {
        IBlast blastType = probe.attachment(IBlast.class);
        Vec3 globalCenter = context.projectLocalPosition(Vec3.atCenterOf(context.localBlockPosition()));
        BlockPos globalPosition = BlockPos.containing(globalCenter);
        Blast blast = blastType.createBlast(context.level(), globalPosition, context.owner(), null);
        BallistixCompatibilityHooks.performProjectedNativeBlast(blast, marker, () -> {
            if (!payload.removeOuter(context.level(), context.localBlockPosition())) {
                throw new IllegalStateException("Failed to consume projected Ballistix payload");
            }
        });
        if (GameTestHooks.isGametestServer()) {
            BallistixImpactAudit.recordImpact(blastType.id(), globalPosition, payload.envelopeDepth());
        }
    }
}
