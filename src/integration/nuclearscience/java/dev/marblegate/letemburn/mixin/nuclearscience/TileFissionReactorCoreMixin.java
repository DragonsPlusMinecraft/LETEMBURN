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
import dev.marblegate.letemburn.integration.nuclearscience.NuclearFissionProjection;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nuclearscience.common.tile.reactor.fission.TileFissionReactorCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = {
        @Condition("sable"),
        @Condition("nuclearscience") })
@Mixin(value = TileFissionReactorCore.class, remap = false)
public abstract class TileFissionReactorCoreMixin {
    @Shadow
    private int ticksOverheating;

    @ModifyArg(method = "tickServer", at = @At(value = "INVOKE", target = "Lvoltaic/api/radiation/SimpleRadiationSource;<init>(DDIZILnet/minecraft/core/BlockPos;ZZ)V"), index = 5)
    private BlockPos letemburn$projectRadiationPosition(BlockPos original) {
        return NuclearFissionProjection.projectRadiationPosition(
                (TileFissionReactorCore) (Object) this, original);
    }

    @ModifyArg(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"), index = 1)
    private BlockPos letemburn$projectReactorSound(BlockPos original) {
        return NuclearFissionProjection.projectSoundPosition(
                (TileFissionReactorCore) (Object) this, original);
    }

    @ModifyArg(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;ofSize(Lnet/minecraft/world/phys/Vec3;DDD)Lnet/minecraft/world/phys/AABB;"), index = 0)
    private Vec3 letemburn$projectExternalHeatQuery(Vec3 original) {
        return NuclearFissionProjection.projectHeatPosition(
                (TileFissionReactorCore) (Object) this, original);
    }

    @Inject(method = "meltdown", at = @At("HEAD"), cancellable = true)
    private void letemburn$deferProjectedMeltdown(CallbackInfo ci) {
        TileFissionReactorCore core = (TileFissionReactorCore) (Object) this;
        if (!NuclearFissionProjection.isExecuting(core)
                && NuclearFissionProjection.scheduleMeltdown(core, ticksOverheating)) {
            ci.cancel();
        }
    }

    @Redirect(method = "meltdown", at = @At(value = "FIELD", target = "Lnuclearscience/common/tile/reactor/fission/TileFissionReactorCore;worldPosition:Lnet/minecraft/core/BlockPos;"))
    private BlockPos letemburn$useFrozenMeltdownOrigin(TileFissionReactorCore core) {
        return NuclearFissionProjection.projectedWorldPosition(core, core.getBlockPos());
    }

    @WrapOperation(method = "meltdown", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean letemburn$commitBeforeParentWorldMutation(
            Level level,
            BlockPos position,
            BlockState state,
            Operation<Boolean> original) {
        TileFissionReactorCore core = (TileFissionReactorCore) (Object) this;
        if (NuclearFissionProjection.skipInitialParentCoreWrite(core)) {
            return true;
        }
        NuclearFissionProjection.beginNativeEffects(core);
        return original.call(level, position, state);
    }
}
