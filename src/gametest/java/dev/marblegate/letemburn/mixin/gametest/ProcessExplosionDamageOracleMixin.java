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

import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.marblegate.letemburn.gametest.draconic.DraconicProcessExplosionOracle;
import java.util.List;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(require = @Condition("draconicevolution"))
@Mixin(targets = "com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion$1", remap = false)
public abstract class ProcessExplosionDamageOracleMixin {
    @Shadow
    @Final
    private ProcessExplosion this$0;

    @Shadow
    @Final
    private double val$calcRadius;

    @WrapOperation(method = "execute", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> letemburn$recordDamageQuery(
            ServerLevel level,
            Class<T> entityClass,
            AABB bounds,
            Operation<List<T>> original) {
        List<T> entities = original.call(level, entityClass, bounds);
        DraconicProcessExplosionOracle.recordDamageQuery(this$0, val$calcRadius, bounds, entities);
        return entities;
    }

    @WrapOperation(method = "execute", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean letemburn$recordDamage(
            Entity entity, DamageSource source, float amount, Operation<Boolean> original) {
        boolean applied = original.call(entity, source, amount);
        DraconicProcessExplosionOracle.recordDamage(this$0, entity, amount, applied);
        return applied;
    }
}
