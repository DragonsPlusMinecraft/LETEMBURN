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

import ballistix.common.blast.util.Blast;
import dev.marblegate.letemburn.common.impulse.ExplosionImpulseBridge.ApplicationResult;
import dev.marblegate.letemburn.gametest.audit.BallistixImpactAudit;
import dev.marblegate.letemburn.integration.ballistix.BallistixCompatibilityHooks;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Restriction(require = @Condition("ballistix"))
@Mixin(value = BallistixCompatibilityHooks.class, remap = false)
public abstract class BallistixBridgeAuditMixin {
    @Inject(method = "onNativeBlastStarted", at = @At("RETURN"))
    private static void letemburn$recordBridge(
            Blast blast, CallbackInfoReturnable<ApplicationResult> cir) {
        BallistixImpactAudit.recordBridge(
                blast.getBlastType().id(), blast.position, cir.getReturnValue());
    }
}
