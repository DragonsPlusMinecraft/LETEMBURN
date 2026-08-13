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

package dev.marblegate.letemburn.mixin.gametest;

import dev.marblegate.letemburn.common.effect.TransactionalEffect;
import dev.marblegate.letemburn.common.impact.ImpactPayloadAdapter;
import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.common.payload.PayloadSnapshot;
import dev.marblegate.letemburn.gametest.audit.VanillaTntImpactAudit;
import dev.marblegate.letemburn.integration.vanilla.VanillaTntImpactAdapter;
import java.util.Comparator;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VanillaTntImpactAdapter.class, remap = false)
public abstract class VanillaTntImpactAuditMixin {
    @Inject(method = "probe", at = @At("RETURN"))
    private void letemburn$recordBelowThreshold(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            CallbackInfoReturnable<ImpactPayloadAdapter.Probe> cir) {
        if (cir.getReturnValue().disposition() == ImpactPayloadAdapter.ProbeDisposition.ARMED_BUT_BELOW_THRESHOLD) {
            VanillaTntImpactAudit.recordBelowThreshold(
                    context.globalImpactPosition(), context.impactVelocity(), payload.envelopeDepth());
        }
    }

    @Inject(method = "commit", at = @At("RETURN"))
    private void letemburn$recordSpawn(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            ImpactPayloadAdapter.Probe probe,
            TransactionalEffect.CommitMarker marker,
            CallbackInfo ci) {
        Vec3 position = context.projectLocalPosition(new Vec3(
                context.localBlockPosition().getX() + 0.5D,
                context.localBlockPosition().getY(),
                context.localBlockPosition().getZ() + 0.5D));
        PrimedTnt entity = context.level()
                .getEntitiesOfClass(PrimedTnt.class, AABB.ofSize(position, 2.0D, 2.0D, 2.0D))
                .stream()
                .min(Comparator.comparingDouble(candidate -> candidate.position().distanceToSqr(position)))
                .orElse(null);
        VanillaTntImpactAudit.recordSpawn(
                position,
                context.globalImpactPosition(),
                entity == null ? -1 : entity.getFuse(),
                payload.envelopeDepth());
    }
}
