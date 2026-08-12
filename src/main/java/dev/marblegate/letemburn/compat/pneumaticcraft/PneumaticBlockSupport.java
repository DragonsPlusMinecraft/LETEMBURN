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

package dev.marblegate.letemburn.compat.pneumaticcraft;

import java.util.IdentityHashMap;
import java.util.Map;
import me.desht.pneumaticcraft.common.block.entity.AbstractAirHandlingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;

public final class PneumaticBlockSupport {
    private static final Map<Block, Boolean> AIR_HANDLING_BLOCKS = new IdentityHashMap<>();

    private PneumaticBlockSupport() {}

    public static synchronized boolean exposesMachineAirHandler(Block block) {
        return AIR_HANDLING_BLOCKS.computeIfAbsent(block, PneumaticBlockSupport::inspect);
    }

    private static boolean inspect(Block block) {
        if (!(block instanceof EntityBlock entityBlock)) {
            return false;
        }
        return entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof AbstractAirHandlingBlockEntity;
    }
}
