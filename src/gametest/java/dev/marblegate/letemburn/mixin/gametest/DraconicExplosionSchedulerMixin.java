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

import dev.marblegate.letemburn.gametest.draconic.DraconicExplosionScheduleAudit;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = @Condition("draconicevolution"))
@Mixin(targets = "dev.marblegate.letemburn.integration.draconic.DraconicExplosionScheduler", remap = false)
public abstract class DraconicExplosionSchedulerMixin {
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private static void letemburn$suppressDangerousExplosion(
            ServerLevel level, BlockPos position, int radius, CallbackInfo ci) {
        DraconicExplosionScheduleAudit.record(position);
        ci.cancel();
    }
}
