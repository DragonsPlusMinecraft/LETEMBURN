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

package dev.marblegate.letemburn.compat.moretnt;

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.compat.core.ImpactPayloadAdapter;
import dev.marblegate.letemburn.compat.core.PayloadSnapshot;
import dev.marblegate.letemburn.compat.core.ProjectedEffectContext;
import dev.marblegate.letemburn.compat.core.TransactionalEffect;
import dev.marblegate.letemburn.compat.moretnt.MoreTntNativeFactory.NativeTntSpec;
import io.github.discusser.moretnt.objects.blocks.BaseTNTBlock;
import io.github.discusser.moretnt.objects.entities.BasePrimedTNT;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHooks;

public final class MoreTntImpactAdapter implements ImpactPayloadAdapter {
    public static final MoreTntImpactAdapter INSTANCE = new MoreTntImpactAdapter();
    public static final double IMPACT_SPEED = 5.0D;

    private static final Set<String> WARNED_UNMAPPED_BLOCKS = ConcurrentHashMap.newKeySet();

    private MoreTntImpactAdapter() {}

    @Override
    public Probe probe(ProjectedEffectContext context, PayloadSnapshot payload) {
        if (!(payload.payloadState().getBlock() instanceof BaseTNTBlock)) {
            return Probe.notHandled();
        }
        NativeTntSpec spec = MoreTntNativeFactory.inspect(payload.payloadState());
        if (spec == null) {
            String description = payload.payloadState().toString();
            if (WARNED_UNMAPPED_BLOCKS.add(description)) {
                LetEmBurn.LOGGER.error(
                        "More Fun TNTs payload has no native primed-entity mapping; leaving it intact: {}",
                        description);
            }
            return Probe.belowThreshold("moretnt-unmapped");
        }
        String fingerprint = "moretnt:" + spec.blockId();
        if (context.impactVelocity() * context.impactVelocity() < IMPACT_SPEED * IMPACT_SPEED) {
            return Probe.belowThreshold(fingerprint);
        }
        return Probe.ready(fingerprint, spec);
    }

    @Override
    public void commit(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            Probe probe,
            TransactionalEffect.CommitMarker marker) {
        NativeTntSpec spec = probe.attachment(NativeTntSpec.class);
        Vec3 spawnPosition = context.projectLocalPosition(new Vec3(
                context.localBlockPosition().getX() + 0.5D,
                context.localBlockPosition().getY(),
                context.localBlockPosition().getZ() + 0.5D));
        Direction projectedFacing = projectHorizontalFacing(context, spec.localFacing());
        BlockPos parentPosition = BlockPos.containing(spawnPosition);
        boolean parentContainedSourceBlock = context.level().getBlockState(parentPosition).is(spec.block());
        BasePrimedTNT primedTnt = MoreTntNativeFactory.create(
                context.level(), spawnPosition, spec, projectedFacing);
        try {
            if (!context.level().addFreshEntity(primedTnt)) {
                throw new IllegalStateException("Failed to add projected More Fun TNTs entity");
            }
            if (!payload.removeOuter(context.level(), context.localBlockPosition())) {
                throw new IllegalStateException("Failed to consume projected More Fun TNTs payload");
            }
            spec.block().sendEntityFacingPacket(primedTnt);
            marker.markNativeEffectStarted();
            if (GameTestHooks.isGametestServer()) {
                MoreTntImpactAudit.record(
                        context.subLevel().getUniqueId(),
                        spec,
                        spawnPosition,
                        projectedFacing,
                        payload.envelopeDepth(),
                        primedTnt.getFuse(),
                        parentContainedSourceBlock);
            }
        } catch (RuntimeException exception) {
            if (!marker.nativeEffectStarted()) {
                primedTnt.discard();
            }
            throw exception;
        }
    }

    private static Direction projectHorizontalFacing(
            ProjectedEffectContext context, Direction localFacing) {
        Vec3 projected = context.projectLocalDirection(Vec3.atLowerCornerOf(localFacing.getNormal()));
        double absX = Math.abs(projected.x);
        double absZ = Math.abs(projected.z);
        if (absX < 1.0E-9D && absZ < 1.0E-9D) {
            return localFacing;
        }
        if (absX >= absZ) {
            return projected.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return projected.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }
}
