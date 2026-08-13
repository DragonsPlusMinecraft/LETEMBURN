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

import dev.marblegate.letemburn.gametest.access.NuclearFissionReactorAccess;
import dev.marblegate.letemburn.gametest.audit.NuclearFissionProjectionAudit;
import dev.marblegate.letemburn.gametest.audit.NuclearFissionProjectionAudit.Kind;
import dev.marblegate.letemburn.integration.nuclearscience.NuclearFissionProjection;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import nuclearscience.common.tile.reactor.fission.TileFissionReactorCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Restriction(require = @Condition("nuclearscience"))
@Mixin(value = NuclearFissionProjection.class, remap = false)
public abstract class NuclearFissionProjectionAuditMixin {
    @Inject(method = "scheduleMeltdown", at = @At("HEAD"))
    private static void letemburn$recordScheduledMeltdown(
            TileFissionReactorCore core,
            CallbackInfoReturnable<Boolean> cir) {
        Projection projection = projection(core, Vec3.atCenterOf(core.getBlockPos()));
        if (projection != null) {
            NuclearFissionProjectionAudit.recordScheduled(
                    core,
                    projection.subLevel().getUniqueId(),
                    core.getBlockPos(),
                    projection.globalPosition(),
                    ((NuclearFissionReactorAccess) core).letemburn$getTicksOverheating());
        }
    }

    @Inject(method = "projectRadiationPosition", at = @At("RETURN"))
    private static void letemburn$recordRadiationProjection(
            TileFissionReactorCore core,
            BlockPos localPosition,
            CallbackInfoReturnable<BlockPos> cir) {
        recordProjection(core, localPosition, Vec3.atCenterOf(localPosition), Kind.RADIATION);
    }

    @Inject(method = "projectSoundPosition", at = @At("RETURN"))
    private static void letemburn$recordSoundProjection(
            TileFissionReactorCore core,
            BlockPos localPosition,
            CallbackInfoReturnable<BlockPos> cir) {
        recordProjection(core, localPosition, Vec3.atCenterOf(localPosition), Kind.SOUND);
    }

    @Inject(method = "projectHeatPosition", at = @At("RETURN"))
    private static void letemburn$recordHeatProjection(
            TileFissionReactorCore core,
            Vec3 localPosition,
            CallbackInfoReturnable<Vec3> cir) {
        recordProjection(core, core.getBlockPos(), localPosition, Kind.HEAT_QUERY);
    }

    @Inject(method = "skipInitialParentCoreWrite", at = @At("RETURN"))
    private static void letemburn$recordSkippedInitialWrite(
            TileFissionReactorCore core, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            NuclearFissionProjectionAudit.recordFromMeltdown(
                    core, Kind.INITIAL_PARENT_CORE_WRITE_SKIPPED);
        }
    }

    @Inject(method = "beginNativeEffects", at = @At(value = "INVOKE", target = "Ldev/marblegate/letemburn/common/effect/TransactionalEffect$CommitMarker;markNativeEffectStarted()V", shift = At.Shift.AFTER))
    private static void letemburn$recordNativeEffectStart(
            TileFissionReactorCore core, CallbackInfo ci) {
        NuclearFissionProjectionAudit.recordFromMeltdown(core, Kind.NATIVE_EFFECT_STARTED);
    }

    private static void recordProjection(
            TileFissionReactorCore core,
            BlockPos localBlockPosition,
            Vec3 localPosition,
            Kind kind) {
        Projection projection = projection(core, localPosition);
        if (projection != null) {
            NuclearFissionProjectionAudit.record(
                    kind,
                    projection.subLevel().getUniqueId(),
                    localBlockPosition,
                    projection.globalPosition(),
                    -1);
        }
    }

    private static Projection projection(TileFissionReactorCore core, Vec3 localPosition) {
        Level level = core.getLevel();
        if (!(level instanceof ServerLevel serverLevel)
                || !(Sable.HELPER.getContaining(core) instanceof ServerSubLevel subLevel)) {
            return null;
        }
        return new Projection(
                subLevel, Sable.HELPER.projectOutOfSubLevel(serverLevel, localPosition));
    }

    private record Projection(ServerSubLevel subLevel, Vec3 globalPosition) {}
}
