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

import com.brandon3055.draconicevolution.lib.ExplosionHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.marblegate.letemburn.gametest.draconic.DraconicProcessExplosionOracle;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(require = @Condition("draconicevolution"))
@Mixin(targets = "com.brandon3055.draconicevolution.lib.ExplosionHelper$RemovalProcess", remap = false)
public abstract class ExplosionHelperRemovalOracleMixin {
    @Shadow
    private ExplosionHelper helper;

    @WrapOperation(method = "updateProcess", at = @At(value = "INVOKE", target = "Lcom/brandon3055/draconicevolution/network/DraconicNetwork;sendExplosionEffect(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;IZ)V"))
    private void letemburn$recordCompletionPacket(
            RegistryAccess registryAccess,
            ResourceKey<Level> dimension,
            BlockPos position,
            int radius,
            boolean reload,
            Operation<Void> original) {
        DraconicProcessExplosionOracle.recordPacket(
                helper, registryAccess, dimension, position, radius, reload);
        original.call(registryAccess, dimension, position, radius, reload);
    }
}
