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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.marblegate.letemburn.common.effect.ChainReactionCoordinator;
import dev.marblegate.letemburn.common.effect.EffectKey;
import dev.marblegate.letemburn.common.effect.TransactionalEffect;
import dev.marblegate.letemburn.common.impact.ImpactStatus;
import dev.marblegate.letemburn.gametest.audit.NuclearPlasmaProjectionAudit;
import dev.marblegate.letemburn.gametest.audit.NuclearPlasmaProjectionAudit.Kind;
import dev.marblegate.letemburn.integration.nuclearscience.NuclearPlasmaProjection;
import dev.ryanhcode.sable.Sable;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nuclearscience.common.tile.reactor.fusion.TilePlasma;
import nuclearscience.registers.NuclearScienceBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(require = @Condition("nuclearscience"))
@Mixin(value = NuclearPlasmaProjection.class, remap = false)
public abstract class NuclearPlasmaProjectionAuditMixin {
    @WrapOperation(method = "observeNativeBlockWrite", at = @At(value = "INVOKE", target = "Ldev/marblegate/letemburn/common/effect/ChainReactionCoordinator;reserve(Lnet/minecraft/server/level/ServerLevel;Ldev/marblegate/letemburn/common/effect/EffectKey;Ldev/marblegate/letemburn/common/effect/TransactionalEffect;Ljava/lang/Runnable;)Ldev/marblegate/letemburn/common/impact/ImpactStatus;"))
    private static ImpactStatus letemburn$recordEscapeCandidate(
            ChainReactionCoordinator coordinator,
            ServerLevel level,
            EffectKey key,
            TransactionalEffect effect,
            Runnable rollback,
            Operation<ImpactStatus> original,
            @Local(argsOnly = true) TilePlasma source) {
        ImpactStatus status = original.call(coordinator, level, key, effect, rollback);
        Vec3 globalPosition = Sable.HELPER.projectOutOfSubLevel(
                level, Vec3.atCenterOf(key.localPosition()));
        NuclearPlasmaProjectionAudit.recordCandidate(
                level,
                key,
                globalPosition,
                Math.clamp(source.spread.getValue() - 1, 0, 6),
                status);
        return status;
    }

    @WrapOperation(method = "createNativeParentSeed", at = @At(value = "INVOKE", target = "Ldev/marblegate/letemburn/integration/nuclearscience/NuclearPlasmaProjection;canNativePlasmaOccupy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean letemburn$recordProtectedTarget(
            Level level,
            BlockPos position,
            BlockState state,
            Operation<Boolean> original) {
        boolean viable = original.call(level, position, state);
        if (!viable && level instanceof ServerLevel serverLevel) {
            NuclearPlasmaProjectionAudit.recordOutcome(
                    serverLevel, position, Kind.PARENT_TARGET_PROTECTED);
        }
        return viable;
    }

    @WrapOperation(method = "createNativeParentSeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean letemburn$recordParentSeed(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            Operation<Boolean> original) {
        boolean changed = original.call(level, position, state);
        if (level.getBlockState(position).is(NuclearScienceBlocks.BLOCK_PLASMA.get())) {
            NuclearPlasmaProjectionAudit.recordOutcome(
                    level, position, Kind.PARENT_SEED_CREATED);
        }
        return changed;
    }
}
