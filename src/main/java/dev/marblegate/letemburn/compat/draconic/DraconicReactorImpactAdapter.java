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

package dev.marblegate.letemburn.compat.draconic;

import com.brandon3055.brandonscore.utils.MathUtils;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore.ReactorState;
import com.brandon3055.draconicevolution.init.DEContent;
import dev.marblegate.letemburn.LetEmBurnConfig;
import dev.marblegate.letemburn.compat.core.ImpactPayloadAdapter;
import dev.marblegate.letemburn.compat.core.PayloadSnapshot;
import dev.marblegate.letemburn.compat.core.ProjectedEffectContext;
import dev.marblegate.letemburn.compat.core.TransactionalEffect;
import dev.marblegate.letemburn.waaoh.RememberDatPos;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHooks;

public final class DraconicReactorImpactAdapter implements ImpactPayloadAdapter {
    public static final DraconicReactorImpactAdapter INSTANCE = new DraconicReactorImpactAdapter();

    private DraconicReactorImpactAdapter() {}

    @Override
    public Probe probe(ProjectedEffectContext context, PayloadSnapshot payload) {
        if (!payload.payloadState().is(DEContent.REACTOR_CORE.get())) {
            return Probe.notHandled();
        }
        CompoundTag blockEntityTag = payload.payloadBlockEntityTag();
        if (blockEntityTag == null || !blockEntityTag.contains("bc_managed_data")) {
            return Probe.notHandled();
        }
        CompoundTag managedData = blockEntityTag.getCompound("bc_managed_data");
        int reactorState = managedData
                .getCompound("reactor_state")
                .getByte("value")
                & 0xFF;
        boolean failed = reactorState == ReactorState.BEYOND_HOPE.ordinal()
                || (managedData.contains("explosion_countdown", Tag.TAG_INT)
                        && managedData.getInt("explosion_countdown") >= 0);
        if (!failed) {
            return Probe.notHandled();
        }

        double threshold = LetEmBurnConfig.DRACONIC_IMPACT_SPEED.get();
        if (context.impactVelocity() * context.impactVelocity() < threshold * threshold) {
            return Probe.belowThreshold("draconic-reactor");
        }
        ReactorFuel fuel = new ReactorFuel(
                managedData.getDouble("converted_fuel"), managedData.getDouble("reactable_fuel"));
        return Probe.ready("draconic-reactor", fuel);
    }

    @Override
    public void commit(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            Probe probe,
            TransactionalEffect.CommitMarker marker) {
        ReactorFuel fuel = probe.attachment(ReactorFuel.class);
        Vec3 globalCenter = context.projectLocalPosition(Vec3.atCenterOf(context.localBlockPosition()));
        BlockPos globalPosition = BlockPos.containing(globalCenter);
        double radius = MathUtils.map(
                fuel.convertedFuel() + fuel.reactableFuel(),
                144.0F,
                10368.0F,
                50.0F,
                350.0F)
                * DEConfig.reactorExplosionScale;
        ProcessExplosion explosion = new ProcessExplosion(globalPosition, (int) radius, context.level(), -1);
        if (explosion instanceof RememberDatPos rememberedExplosion) {
            rememberedExplosion.remember(globalPosition);
        }
        if (!payload.removeOuter(context.level(), context.localBlockPosition())) {
            throw new IllegalStateException("Failed to consume projected Draconic reactor payload");
        }
        marker.markNativeEffectStarted();

        if (GameTestHooks.isGametestServer()) {
            DraconicExplosionAudit.record(
                    globalPosition, context.globalImpactPosition(), (int) radius);
            return;
        }
        explosion.detonate();
    }

    private record ReactorFuel(double convertedFuel, double reactableFuel) {}
}
