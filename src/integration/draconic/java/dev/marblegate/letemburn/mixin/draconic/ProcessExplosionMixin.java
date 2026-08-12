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

import codechicken.lib.vec.Vector3;
import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.marblegate.letemburn.integration.draconic.DraconicAnnulusPredicates;
import dev.marblegate.letemburn.integration.draconic.DraconicAnnulusPredicates.ScanLine;
import dev.marblegate.letemburn.integration.draconic.ProcessExplosionOriginAccess;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Restriction(require = {
        @Condition("sable"),
        @Condition("draconicevolution") })
@Mixin(value = ProcessExplosion.class, remap = false)
public abstract class ProcessExplosionMixin implements ProcessExplosionOriginAccess {
    @Unique
    private BlockPos letemburn$projectedOrigin;

    @Unique
    private int letemburn$annulusCentreZ;

    @Unique
    private ScanLine letemburn$annulusScanLine = DraconicAnnulusPredicates.scanLine(0, 0);

    @Mutable
    @Shadow
    @Final
    public Vector3 origin;

    @Mutable
    @Shadow
    protected boolean calculationComplete;

    @Shadow
    public int radius;

    @Shadow
    public abstract void updateCalculation();

    @Redirect(method = "updateCalculation", at = @At(value = "INVOKE", target = "Lcom/brandon3055/brandonscore/utils/Utils;getDistance(DDDD)D"), require = 1)
    private double letemburn$replaceAnnulusSquareRoot(
            double x, double z, double centreX, double centreZ) {
        return DraconicAnnulusPredicates.membershipDistance(radius, x, z, centreX, centreZ);
    }

    @ModifyVariable(method = "updateCalculation", at = @At("STORE"), index = 7, require = 1)
    private int letemburn$startAtFirstAnnulusCoordinate(
            int originalStart, @Local(index = 6) int x) {
        BlockPos centre = origin.pos();
        letemburn$annulusCentreZ = centre.getZ();
        letemburn$annulusScanLine = DraconicAnnulusPredicates.scanLine(radius, x - centre.getX());
        if (letemburn$annulusScanLine.isEmpty()) {
            return centre.getZ() + radius;
        }
        return centre.getZ() + letemburn$annulusScanLine.firstDeltaZ();
    }

    @Inject(method = "updateCalculation", at = @At(value = "JUMP", opcode = Opcodes.GOTO, ordinal = 0), require = 1)
    private void letemburn$skipAnnulusInterior(
            CallbackInfo ci, @Local(index = 7) LocalIntRef z) {
        int relativeZ = z.get() - letemburn$annulusCentreZ;
        z.set(letemburn$annulusCentreZ + letemburn$annulusScanLine.adjustIncrementedDeltaZ(relativeZ));
    }

    @Inject(method = "detonate", at = @At(value = "HEAD"))
    private void letemburn$calculateAtProjectedOrigin(CallbackInfoReturnable<Boolean> cir) {
        if (letemburn$projectedOrigin != null) {
            origin = Vector3.fromBlockPosCenter(letemburn$projectedOrigin);
            while (!calculationComplete) {
                updateCalculation();
            }
        }
    }

    @Override
    public void letemburn$setProjectedOrigin(BlockPos position) {
        letemburn$projectedOrigin = position;
    }
}
