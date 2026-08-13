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

import codechicken.lib.vec.Vector3;
import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import com.brandon3055.draconicevolution.lib.ExplosionHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.marblegate.letemburn.gametest.draconic.DraconicAnnulusMode;
import dev.marblegate.letemburn.gametest.draconic.DraconicProcessExplosionOracle;
import dev.marblegate.letemburn.gametest.draconic.ProcessExplosionAlgorithmAccess;
import dev.marblegate.letemburn.gametest.draconic.ProcessExplosionState;
import dev.marblegate.letemburn.integration.draconic.DraconicAnnulusPredicates;
import dev.marblegate.letemburn.integration.draconic.DraconicAnnulusPredicates.ScanLine;
import java.util.HashSet;
import java.util.Objects;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Restriction(require = @Condition("draconicevolution"))
@Mixin(value = ProcessExplosion.class, remap = false)
public abstract class ProcessExplosionOracleMixin implements ProcessExplosionAlgorithmAccess {
    @Unique
    private DraconicAnnulusMode letemburn$annulusMode;

    @Unique
    private int letemburn$annulusCentreZ;

    @Unique
    private ScanLine letemburn$annulusScanLine = DraconicAnnulusPredicates.scanLine(0, 0);

    @Shadow
    @Final
    public Vector3 origin;

    @Shadow
    protected boolean calculationComplete;

    @Shadow
    protected boolean detonated;

    @Shadow
    protected long startTime;

    @Shadow
    protected long calcWait;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    @Final
    private int minimumDelay;

    @Shadow
    private BlockState lavaState;

    @Shadow
    public int radius;

    /**
     * Selects rejected candidates only inside the GameTest output. PRODUCTION and LEGACY both invoke the
     * untouched upstream distance calculation.
     */
    @WrapOperation(method = "updateCalculation", at = @At(value = "INVOKE", target = "Lcom/brandon3055/brandonscore/utils/Utils;getDistance(DDDD)D"))
    private double letemburn$selectAuditDistance(
            double x,
            double z,
            double centreX,
            double centreZ,
            Operation<Double> original) {
        DraconicAnnulusMode mode = letemburn$getAnnulusMode();
        if (mode == DraconicAnnulusMode.PRODUCTION || mode == DraconicAnnulusMode.LEGACY) {
            return original.call(x, z, centreX, centreZ);
        }
        return DraconicAnnulusPredicates.membershipDistance(radius, x, z, centreX, centreZ);
    }

    @ModifyVariable(method = "updateCalculation", at = @At("STORE"), index = 7)
    private int letemburn$selectAuditScanStart(
            int originalStart, @Local(index = 6) int x) {
        if (letemburn$getAnnulusMode() != DraconicAnnulusMode.A1) {
            return originalStart;
        }
        BlockPos centre = origin.pos();
        letemburn$annulusCentreZ = centre.getZ();
        letemburn$annulusScanLine = DraconicAnnulusPredicates.scanLine(radius, x - centre.getX());
        if (letemburn$annulusScanLine.isEmpty()) {
            return centre.getZ() + radius;
        }
        return centre.getZ() + letemburn$annulusScanLine.firstDeltaZ();
    }

    @Inject(method = "updateCalculation", at = @At(value = "JUMP", opcode = Opcodes.GOTO, ordinal = 0))
    private void letemburn$selectAuditScanIncrement(
            CallbackInfo ci, @Local(index = 7) LocalIntRef z) {
        if (letemburn$getAnnulusMode() != DraconicAnnulusMode.A1) {
            return;
        }
        int relativeZ = z.get() - letemburn$annulusCentreZ;
        z.set(letemburn$annulusCentreZ + letemburn$annulusScanLine.adjustIncrementedDeltaZ(relativeZ));
    }

    @WrapOperation(method = "updateCalculation", at = @At(value = "INVOKE", target = "Lcodechicken/lib/vec/Vector3;set(DDD)Lcodechicken/lib/vec/Vector3;"))
    private Vector3 letemburn$recordAcceptedPosition(
            Vector3 vector, double x, double y, double z, Operation<Vector3> original) {
        DraconicProcessExplosionOracle.recordAcceptedPosition((ProcessExplosion) (Object) this, x, y, z);
        return original.call(vector, x, y, z);
    }

    @WrapOperation(method = "updateCalculation", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int letemburn$recordRandomInt(RandomSource random, int bound, Operation<Integer> original) {
        int value = original.call(random, bound);
        DraconicProcessExplosionOracle.recordRandomInt((ProcessExplosion) (Object) this, bound, value);
        return value;
    }

    @WrapOperation(method = "updateCalculation", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextDouble()D"))
    private double letemburn$recordRandomDouble(RandomSource random, Operation<Double> original) {
        double value = original.call(random);
        DraconicProcessExplosionOracle.recordRandomDouble((ProcessExplosion) (Object) this, value);
        return value;
    }

    @WrapOperation(method = "trace", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState letemburn$recordBlockStateRead(
            ServerLevel level, BlockPos position, Operation<BlockState> original) {
        BlockState state = original.call(level, position);
        DraconicProcessExplosionOracle.recordBlockStateRead((ProcessExplosion) (Object) this, position, state);
        return state;
    }

    @WrapOperation(method = "trace", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean letemburn$recordEmptyBlockRead(
            ServerLevel level, BlockPos position, Operation<Boolean> original) {
        boolean empty = original.call(level, position);
        DraconicProcessExplosionOracle.recordEmptyBlockRead((ProcessExplosion) (Object) this, position, empty);
        return empty;
    }

    @WrapOperation(method = "trace", at = @At(value = "INVOKE", target = "Ljava/util/HashSet;add(Ljava/lang/Object;)Z"))
    private boolean letemburn$recordSetInsertion(
            HashSet<Object> target, Object value, Operation<Boolean> original) {
        boolean inserted = original.call(target, value);
        DraconicProcessExplosionOracle.recordSetInsertion(
                (ProcessExplosion) (Object) this, target, value, inserted);
        return inserted;
    }

    @Inject(method = "trace", at = @At("HEAD"))
    private void letemburn$recordTraceStart(
            Vector3 position,
            double power,
            int distance,
            int direction,
            double resistance,
            int travel,
            CallbackInfoReturnable<Double> cir) {
        DraconicProcessExplosionOracle.recordTraceStart(
                (ProcessExplosion) (Object) this,
                position.x,
                position.y,
                position.z,
                power,
                distance,
                direction,
                resistance,
                travel);
    }

    @Inject(method = "trace", at = @At("RETURN"))
    private void letemburn$recordTraceReturn(
            Vector3 position,
            double power,
            int distance,
            int direction,
            double resistance,
            int travel,
            CallbackInfoReturnable<Double> cir) {
        DraconicProcessExplosionOracle.recordTraceReturn(
                (ProcessExplosion) (Object) this, cir.getReturnValueD());
    }

    @Inject(method = "detonate", at = @At("HEAD"))
    private void letemburn$markDetonation(CallbackInfoReturnable<Boolean> cir) {
        DraconicProcessExplosionOracle.markDetonation((ProcessExplosion) (Object) this);
    }

    @WrapOperation(method = "detonate", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean letemburn$recordLavaWrite(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            Operation<Boolean> original) {
        boolean changed = original.call(level, position, state);
        DraconicProcessExplosionOracle.recordLavaWrite(
                (ProcessExplosion) (Object) this, position, state, changed);
        return changed;
    }

    @WrapOperation(method = "detonate", at = @At(value = "INVOKE", target = "Lcom/brandon3055/draconicevolution/network/DraconicNetwork;sendExplosionEffect(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;IZ)V"))
    private void letemburn$recordInitialPacket(
            RegistryAccess registryAccess,
            ResourceKey<Level> dimension,
            BlockPos position,
            int radius,
            boolean reload,
            Operation<Void> original) {
        DraconicProcessExplosionOracle.recordPacket(
                (ProcessExplosion) (Object) this,
                registryAccess,
                dimension,
                position,
                radius,
                reload);
        original.call(registryAccess, dimension, position, radius, reload);
    }

    @WrapOperation(method = "detonate", at = @At(value = "INVOKE", target = "Lcom/brandon3055/draconicevolution/lib/ExplosionHelper;finish()V"))
    private void letemburn$attachRemovalHelper(ExplosionHelper helper, Operation<Void> original) {
        DraconicProcessExplosionOracle.attachHelper((ProcessExplosion) (Object) this, helper);
        original.call(helper);
    }

    @Override
    public void letemburn$setAnnulusMode(DraconicAnnulusMode mode) {
        letemburn$annulusMode = Objects.requireNonNull(mode);
    }

    @Override
    public DraconicAnnulusMode letemburn$getAnnulusMode() {
        return letemburn$annulusMode == null ? DraconicAnnulusMode.PRODUCTION : letemburn$annulusMode;
    }

    @Override
    public ProcessExplosionState letemburn$captureState() {
        return ProcessExplosionState.capture(
                (ProcessExplosion) (Object) this,
                level,
                server,
                minimumDelay,
                calculationComplete,
                detonated,
                startTime,
                calcWait,
                lavaState);
    }
}
