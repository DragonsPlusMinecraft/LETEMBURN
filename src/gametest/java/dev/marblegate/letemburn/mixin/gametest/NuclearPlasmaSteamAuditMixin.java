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
import dev.marblegate.letemburn.gametest.audit.NuclearPlasmaProjectionAudit;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.server.level.ServerLevel;
import nuclearscience.api.turbine.ISteamReceiver;
import nuclearscience.common.tile.reactor.fusion.TilePlasma;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(require = @Condition("nuclearscience"))
@Mixin(value = TilePlasma.class, remap = false)
public abstract class NuclearPlasmaSteamAuditMixin {
    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnuclearscience/api/turbine/ISteamReceiver;receiveSteam(II)I"))
    private int letemburn$recordNativeSteamDelivery(
            ISteamReceiver receiver,
            int amount,
            int temperature,
            Operation<Integer> original) {
        int accepted = original.call(receiver, amount, temperature);
        TilePlasma plasma = (TilePlasma) (Object) this;
        if (plasma.getLevel() instanceof ServerLevel level
                && !(Sable.HELPER.getContaining(plasma) instanceof ServerSubLevel)) {
            NuclearPlasmaProjectionAudit.recordNativeSteamDelivery(
                    plasma.getBlockPos(), amount, temperature, accepted, level.getGameTime());
        }
        return accepted;
    }
}
