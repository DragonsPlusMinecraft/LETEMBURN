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

package dev.marblegate.letemburn.waaoh;

import com.brandon3055.brandonscore.utils.MathUtils;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback.CollisionResult;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class DraconicReactorImpact {
    public static final double MIN_IMPACT_SPEED_SQUARED = 16.0D;

    private DraconicReactorImpact() {}

    public static boolean isHardEnough(double impactVelocity) {
        return impactVelocity * impactVelocity >= MIN_IMPACT_SPEED_SQUARED;
    }

    public static CollisionResult detonate(BlockPos localPos, double convertedFuel, double reactableFuel) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        ServerLevel level = system.getLevel();
        Vec3 globalCenter = SableCompanion.INSTANCE.projectOutOfSubLevel(level, Vec3.atCenterOf(localPos));
        BlockPos globalPos = BlockPos.containing(globalCenter);
        double radius = MathUtils.map(
                convertedFuel + reactableFuel, 144.0F, 10368.0F, 50.0F, 350.0F)
                * DEConfig.reactorExplosionScale;
        ProcessExplosion explosion = new ProcessExplosion(globalPos, (int) radius, level, -1);

        level.setBlock(localPos, Blocks.AIR.defaultBlockState(), 11);
        if (explosion instanceof RememberDatPos rememberedExplosion) {
            rememberedExplosion.remember(globalPos);
        }
        explosion.detonate();
        return new CollisionResult(JOMLConversion.ZERO, true);
    }
}
