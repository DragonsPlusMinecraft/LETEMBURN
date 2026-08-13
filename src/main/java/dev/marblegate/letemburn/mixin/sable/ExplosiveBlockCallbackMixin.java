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

package dev.marblegate.letemburn.mixin.sable;

import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.common.impact.ProjectedPayloadCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.callback.ExplosiveBlockCallback;
import dev.ryanhcode.sable.physics.callback.FragileBlockCallback;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Restriction(require = @Condition("sable"))
@Mixin(value = FragileBlockCallback.class, remap = false)
public abstract class ExplosiveBlockCallbackMixin {
    @Inject(method = "sable$onCollision", at = @At("HEAD"), cancellable = true, remap = false)
    private void letemburn$deferNativeTnt(
            BlockPos blockPosition,
            @Nullable BlockPos otherBlockPosition,
            Vector3d impactPosition,
            double impactVelocity,
            CallbackInfoReturnable<BlockSubLevelCollisionCallback.CollisionResult> callback) {
        if (!((Object) this instanceof ExplosiveBlockCallback)) {
            return;
        }
        BlockSubLevelCollisionCallback.CollisionResult result = ProjectedPayloadCollisionCallback.INSTANCE
                .sable$onCollision(blockPosition, otherBlockPosition, impactPosition, impactVelocity);
        if (result.removeCollision()) {
            callback.setReturnValue(result);
            return;
        }

        // A vanilla TNT payload below its threshold is handled but intentionally remains intact.
        var context = ProjectedEffectContext.fromCollision(
                blockPosition, otherBlockPosition, impactPosition, impactVelocity);
        if (context != null
                && context.subLevel()
                        .getLevel()
                        .getBlockState(blockPosition)
                        .is(net.minecraft.world.level.block.Blocks.TNT)) {
            callback.setReturnValue(BlockSubLevelCollisionCallback.CollisionResult.NONE);
        }
    }
}
