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

package dev.marblegate.letemburn.integration.pneumaticcraft;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class PneumaticLeakTracker {
    private static final Map<IAirHandlerMachine, LeakSample> SAMPLES = new WeakHashMap<>();

    private PneumaticLeakTracker() {}

    public static synchronized void record(
            IAirHandlerMachine handler,
            BlockEntity blockEntity,
            Direction direction,
            float pressureBefore,
            int airBefore,
            int airAfter) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long actualLeakRate = Math.abs((long) airAfter - airBefore);
        if (actualLeakRate == 0L) {
            SAMPLES.remove(handler);
            return;
        }
        SAMPLES.put(
                handler,
                new LeakSample(
                        level,
                        blockEntity,
                        direction,
                        pressureBefore,
                        (int) Math.min(Integer.MAX_VALUE, actualLeakRate),
                        level.getGameTime()));
    }

    static synchronized @Nullable LeakSample current(
            IAirHandlerMachine handler, BlockEntity blockEntity, long gameTime) {
        LeakSample sample = SAMPLES.get(handler);
        if (sample == null || sample.blockEntity() != blockEntity) {
            return null;
        }
        long age = gameTime - sample.gameTime();
        if (age < 0L || age > 1L) {
            SAMPLES.remove(handler);
            return null;
        }
        return sample;
    }

    static synchronized void clearLevel(ServerLevel level) {
        Iterator<LeakSample> iterator = SAMPLES.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().level() == level) {
                iterator.remove();
            }
        }
    }

    record LeakSample(
            ServerLevel level,
            BlockEntity blockEntity,
            Direction direction,
            double pressure,
            int actualLeakRateMlPerTick,
            long gameTime) {}
}
