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

package dev.marblegate.letemburn.integration.draconic;

import com.brandon3055.brandonscore.handlers.ProcessHandler;
import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class DraconicExplosionScheduler {
    private DraconicExplosionScheduler() {}

    public static void schedule(ServerLevel level, BlockPos position, int radius) {
        ProcessHandler.addProcess(new ProcessExplosion(position, radius, level, 0));
    }
}
