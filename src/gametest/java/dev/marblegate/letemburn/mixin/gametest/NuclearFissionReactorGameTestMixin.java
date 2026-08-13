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
import dev.marblegate.letemburn.gametest.access.NuclearFissionReactorAccess;
import dev.marblegate.letemburn.gametest.audit.NuclearFissionProjectionAudit;
import dev.marblegate.letemburn.gametest.audit.NuclearFissionProjectionAudit.Kind;
import dev.marblegate.letemburn.integration.nuclearscience.NuclearFissionProjection;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import nuclearscience.common.tile.reactor.fission.TileFissionReactorCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import voltaic.prefab.tile.components.type.ComponentTickable;

@Restriction(require = @Condition("nuclearscience"))
@Mixin(value = TileFissionReactorCore.class, remap = false)
public abstract class NuclearFissionReactorGameTestMixin implements NuclearFissionReactorAccess {
    @Shadow
    private int ticksOverheating;

    @Override
    @Invoker("tickServer")
    public abstract void letemburn$invokeTickServer(ComponentTickable tickable);

    @Override
    public int letemburn$getTicksOverheating() {
        return ticksOverheating;
    }

    @WrapOperation(method = "meltdown", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"))
    private Explosion letemburn$recordNativeExplosion(
            Level level,
            Entity source,
            double x,
            double y,
            double z,
            float radius,
            Level.ExplosionInteraction interaction,
            Operation<Explosion> original) {
        NuclearFissionProjectionAudit.recordFromMeltdown(
                (TileFissionReactorCore) (Object) this, Kind.NATIVE_EXPLOSION);
        return original.call(level, source, x, y, z, radius, interaction);
    }

    @Inject(method = "meltdown", at = @At("RETURN"))
    private void letemburn$recordCompletedMeltdown(CallbackInfo ci) {
        TileFissionReactorCore core = (TileFissionReactorCore) (Object) this;
        if (NuclearFissionProjection.isExecuting(core)) {
            NuclearFissionProjectionAudit.recordFromMeltdown(core, Kind.MELTDOWN_COMPLETE);
        }
    }
}
