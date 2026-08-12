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

package dev.marblegate.letemburn.integration.moretnt;

import io.github.discusser.moretnt.MoreTNT;
import io.github.discusser.moretnt.objects.PrimedTNTObject;
import io.github.discusser.moretnt.objects.blocks.BaseTNTBlock;
import io.github.discusser.moretnt.objects.entities.BasePrimedTNT;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class MoreTntNativeFactory {
    private MoreTntNativeFactory() {}

    public static @Nullable NativeTntSpec inspect(BlockState state) {
        if (!(state.getBlock() instanceof BaseTNTBlock block)) {
            return null;
        }
        PrimedTNTObject primedObject = MoreTNT.blockToPrimedTNTMap.get(block);
        if (primedObject == null) {
            return null;
        }
        EntityType<? extends BasePrimedTNT> entityType = primedObject.entityType.get();
        Direction facing = state.getOptionalValue(BaseTNTBlock.FACING)
                .orElse(BasePrimedTNT.DEFAULT_DIRECTION);
        return new NativeTntSpec(
                block,
                entityType,
                BuiltInRegistries.BLOCK.getKey(block),
                BuiltInRegistries.ENTITY_TYPE.getKey(entityType),
                facing,
                block.size,
                block.fire);
    }

    public static BasePrimedTNT create(
            ServerLevel level, Vec3 position, NativeTntSpec spec, Direction projectedFacing) {
        BasePrimedTNT primedTnt = new BasePrimedTNT(
                spec.entityType(),
                spec.block(),
                level,
                position.x,
                position.y,
                position.z,
                spec.size(),
                spec.fire(),
                projectedFacing);
        primedTnt.setFuse(1);
        return primedTnt;
    }

    public record NativeTntSpec(
            BaseTNTBlock block,
            EntityType<? extends BasePrimedTNT> entityType,
            ResourceLocation blockId,
            ResourceLocation entityTypeId,
            Direction localFacing,
            float size,
            boolean fire) {}
}
