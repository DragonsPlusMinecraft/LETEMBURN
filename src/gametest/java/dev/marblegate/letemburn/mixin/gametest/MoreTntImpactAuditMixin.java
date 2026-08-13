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
import dev.marblegate.letemburn.gametest.audit.MoreTntImpactAudit;
import dev.marblegate.letemburn.integration.moretnt.MoreTntImpactAdapter;
import dev.marblegate.letemburn.integration.moretnt.MoreTntNativeFactory.NativeTntSpec;
import io.github.discusser.moretnt.objects.entities.BasePrimedTNT;
import java.util.Comparator;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = @Condition("moretnt"))
@Mixin(value = MoreTntImpactAdapter.class, remap = false)
public abstract class MoreTntImpactAuditMixin {
    @Inject(method = "commit", at = @At("RETURN"))
    private void letemburn$recordSpawn(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            ImpactPayloadAdapter.Probe probe,
            TransactionalEffect.CommitMarker marker,
            CallbackInfo ci) {
        NativeTntSpec spec = probe.attachment(NativeTntSpec.class);
        Vec3 position = context.projectLocalPosition(new Vec3(
                context.localBlockPosition().getX() + 0.5D,
                context.localBlockPosition().getY(),
                context.localBlockPosition().getZ() + 0.5D));
        Direction facing = projectHorizontalFacing(context, spec.localFacing());
        BasePrimedTNT entity = context.level()
                .getEntitiesOfClass(BasePrimedTNT.class, AABB.ofSize(position, 2.0D, 2.0D, 2.0D))
                .stream()
                .min(Comparator.comparingDouble(candidate -> candidate.position().distanceToSqr(position)))
                .orElse(null);
        MoreTntImpactAudit.record(
                context.subLevel().getUniqueId(),
                spec,
                position,
                facing,
                payload.envelopeDepth(),
                entity == null ? -1 : entity.getFuse(),
                context.level().getBlockState(BlockPos.containing(position)).is(spec.block()));
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
