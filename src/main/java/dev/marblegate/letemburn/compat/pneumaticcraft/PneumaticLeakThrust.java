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

import dev.marblegate.letemburn.LetEmBurnConfig;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import me.desht.pneumaticcraft.common.block.entity.AbstractAirHandlingBlockEntity;
import org.joml.Vector3d;

public final class PneumaticLeakThrust {
    private PneumaticLeakThrust() {}

    public static int apply(
            AbstractAirHandlingBlockEntity blockEntity, ServerSubLevel subLevel, double timeStep) {
        double mass = subLevel.getMassTracker().getMass();
        if (!Double.isFinite(mass) || mass <= 0.0D) {
            return 0;
        }

        List<Contribution> contributions = new ArrayList<>();
        double totalMagnitude = 0.0D;
        for (var handler : PneumaticAirHandlers.unique(blockEntity)) {
            PneumaticLeakTracker.LeakSample sample = PneumaticLeakTracker.current(
                    handler, blockEntity, subLevel.getLevel().getGameTime());
            if (sample == null) {
                continue;
            }
            Vector3d impulse = PneumaticThrustModel.rawImpulse(
                    sample.direction(),
                    sample.pressure(),
                    sample.actualLeakRateMlPerTick(),
                    timeStep,
                    LetEmBurnConfig.PNEUMATIC_THRUST_SCALE.get());
            if (impulse.lengthSquared() == 0.0D) {
                continue;
            }
            var position = blockEntity.getBlockPos();
            Vector3d point = new Vector3d(
                    position.getX() + 0.5D + sample.direction().getStepX() * 0.5D,
                    position.getY() + 0.5D + sample.direction().getStepY() * 0.5D,
                    position.getZ() + 0.5D + sample.direction().getStepZ() * 0.5D);
            contributions.add(new Contribution(point, impulse));
            totalMagnitude += impulse.length();
        }

        double scale = PneumaticThrustModel.capScale(
                totalMagnitude, mass, LetEmBurnConfig.PNEUMATIC_MAX_DELTA_V_PER_SUBSTEP.get());
        if (scale == 0.0D) {
            return 0;
        }
        var forceGroup = subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());
        for (Contribution contribution : contributions) {
            forceGroup.applyAndRecordPointForce(
                    contribution.point(), new Vector3d(contribution.impulse()).mul(scale));
        }
        return contributions.size();
    }

    private record Contribution(Vector3d point, Vector3d impulse) {}
}
