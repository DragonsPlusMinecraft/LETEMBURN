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

import ballistix.api.blast.IBlast;
import dev.marblegate.letemburn.common.effect.TransactionalEffect;
import dev.marblegate.letemburn.common.impact.ImpactPayloadAdapter;
import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.common.payload.PayloadSnapshot;
import dev.marblegate.letemburn.gametest.audit.BallistixImpactAudit;
import dev.marblegate.letemburn.integration.ballistix.BallistixImpactPayloadAdapter;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = @Condition("ballistix"))
@Mixin(value = BallistixImpactPayloadAdapter.class, remap = false)
public abstract class BallistixImpactAuditMixin {
    @Inject(method = "commit", at = @At("RETURN"))
    private void letemburn$recordImpact(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            ImpactPayloadAdapter.Probe probe,
            TransactionalEffect.CommitMarker marker,
            CallbackInfo ci) {
        IBlast blastType = probe.attachment(IBlast.class);
        Vec3 globalCenter = context.projectLocalPosition(Vec3.atCenterOf(context.localBlockPosition()));
        BallistixImpactAudit.recordImpact(
                blastType.id(), BlockPos.containing(globalCenter), payload.envelopeDepth());
    }
}
