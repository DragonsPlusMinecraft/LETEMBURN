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

package dev.marblegate.letemburn.mixin.pneumaticcraft;

import dev.marblegate.letemburn.compat.pneumaticcraft.PneumaticLeakTracker;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import me.desht.pneumaticcraft.common.capabilities.MachineAirHandler;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = {
        @Condition("sable"),
        @Condition("pneumaticcraft") })
@Mixin(MachineAirHandler.class)
public abstract class MachineAirHandlerMixin {
    @Unique
    private float letemburn$pressureBeforeLeak;

    @Unique
    private int letemburn$airBeforeLeak;

    @Inject(method = "handleAirLeak", at = @At("HEAD"), remap = false)
    private void letemburn$captureLeakStart(
            BlockEntity blockEntity, Direction direction, CallbackInfo callback) {
        IAirHandlerMachine self = (IAirHandlerMachine) this;
        letemburn$pressureBeforeLeak = self.getPressure();
        letemburn$airBeforeLeak = self.getAir();
    }

    @Inject(method = "handleAirLeak", at = @At("RETURN"), remap = false)
    private void letemburn$captureActualLeak(
            BlockEntity blockEntity, Direction direction, CallbackInfo callback) {
        IAirHandlerMachine self = (IAirHandlerMachine) this;
        PneumaticLeakTracker.record(
                self,
                blockEntity,
                direction,
                letemburn$pressureBeforeLeak,
                letemburn$airBeforeLeak,
                self.getAir());
    }
}
