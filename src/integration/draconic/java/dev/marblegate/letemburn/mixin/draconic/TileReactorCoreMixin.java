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

package dev.marblegate.letemburn.mixin.draconic;

import com.brandon3055.brandonscore.blocks.TileBCore;
import com.brandon3055.brandonscore.handlers.IProcess;
import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.marblegate.letemburn.integration.draconic.ProcessExplosionOriginAccess;
import dev.ryanhcode.sable.Sable;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = {
        @Condition("sable"),
        @Condition("draconicevolution") })
@Mixin(value = TileReactorCore.class, remap = false)
public abstract class TileReactorCoreMixin extends TileBCore {
    public TileReactorCoreMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "checkBlockIntrusions", at = @At("HEAD"), cancellable = true)
    private void letemburn$preserveSubLevelPayload(CallbackInfo ci) {
        if (Sable.HELPER.isInPlotGrid(this)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "updateCriticalState", at = @At(value = "INVOKE", target = "Lcom/brandon3055/brandonscore/handlers/ProcessHandler;addProcess(Lcom/brandon3055/brandonscore/handlers/IProcess;)V"))
    private void letemburn$skipSubLevelPrecalculation(IProcess process, Operation<Void> original) {
        if (!Sable.HELPER.isInPlotGrid(this)) {
            original.call(process);
        }
    }

    @ModifyExpressionValue(method = "updateCriticalState", at = @At(value = "INVOKE", target = "Lcom/brandon3055/draconicevolution/blocks/reactor/ProcessExplosion;isCalculationComplete()Z"))
    private boolean letemburn$treatSubLevelCalculationAsComplete(boolean original) {
        return Sable.HELPER.isInPlotGrid(this) || original;
    }

    @WrapOperation(method = "updateCriticalState", at = @At(value = "INVOKE", target = "Lcom/brandon3055/draconicevolution/blocks/reactor/ProcessExplosion;detonate()Z"))
    private boolean letemburn$projectExplosionOrigin(ProcessExplosion instance, Operation<Boolean> original) {
        var helper = Sable.HELPER;
        if (helper.isInPlotGrid(this) && instance instanceof ProcessExplosionOriginAccess originAccess) {
            BlockPos pos = BlockPos.containing(helper.projectOutOfSubLevel(this.level, Vec3.atCenterOf(getBlockPos())));
            originAccess.letemburn$setProjectedOrigin(pos);
        }
        return original.call(instance);
    }
}
