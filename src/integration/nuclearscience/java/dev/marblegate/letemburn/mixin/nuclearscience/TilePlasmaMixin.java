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

package dev.marblegate.letemburn.mixin.nuclearscience;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.marblegate.letemburn.integration.nuclearscience.NuclearPlasmaProjection;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import nuclearscience.common.tile.reactor.fusion.TilePlasma;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import voltaic.prefab.tile.components.type.ComponentTickable;

@Restriction(require = {
        @Condition("sable"),
        @Condition("nuclearscience") })
@Mixin(value = TilePlasma.class, remap = false)
public abstract class TilePlasmaMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void letemburn$findSubLevelEscape(ComponentTickable tickable, CallbackInfo ci) {
        NuclearPlasmaProjection.observeNativeTickStart((TilePlasma) (Object) this);
    }

    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"), require = 2)
    private boolean letemburn$observeNativePlasmaWrite(
            Level level,
            BlockPos position,
            BlockState state,
            Operation<Boolean> original) {
        boolean changed = original.call(level, position, state);
        NuclearPlasmaProjection.observeNativeBlockWrite(
                (TilePlasma) (Object) this, level, position, state);
        return changed;
    }
}
