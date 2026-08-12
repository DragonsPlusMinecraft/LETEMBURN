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

package dev.marblegate.letemburn.gametest;

import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.absoluteDirection;
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.absolutePosition;
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.localPosition;
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.spawnSubLevel;

import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore.ReactorState;
import com.brandon3055.draconicevolution.init.DEContent;
import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.effect.ChainReactionCoordinator;
import dev.marblegate.letemburn.common.impact.ImpactPayloadAdapter;
import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.common.payload.PayloadEnvelopeResolver;
import dev.marblegate.letemburn.integration.draconic.DraconicExplosionAudit;
import dev.marblegate.letemburn.integration.draconic.DraconicReactorImpactAdapter;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@PrefixGameTestTemplate(false)
public final class DraconicPayloadGameTests {
    static final int SAFE_COUNTDOWN = 10_000;

    private DraconicPayloadGameTests() {}

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 20)
    public static void sparseAnnulusMixinExecutesWithoutDetonating(GameTestHelper helper) {
        Vector3d absoluteOrigin = absolutePosition(helper, new Vector3d(5.5D, 20.0D, 5.5D));
        BlockPos origin = BlockPos.containing(absoluteOrigin.x, absoluteOrigin.y, absoluteOrigin.z);
        ProcessExplosion calculation = new ProcessExplosion(origin, 5, helper.getLevel(), -1);
        calculation.enableEffect = false;
        for (int step = 0; step < 5; step++) {
            calculation.updateCalculation();
        }

        if (!calculation.isCalculationComplete() || calculation.radius != 5) {
            helper.fail("Sparse annulus calculation did not reach the expected safe radius");
            return;
        }
        if (calculation.destroyedBlocks.size() != 5) {
            helper.fail("Sparse annulus calculation changed the number of staged radius layers");
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 20)
    public static void failedReactorProbeUsesExactImpactThreshold(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(5.5D, 40.0D, 5.5D)),
                accessor -> placeFailedReactor(accessor, false));
        BlockPos corePosition = subLevel.getPlot().getCenterBlock();
        PayloadEnvelopeResolver.Resolution resolution = PayloadEnvelopeResolver.INSTANCE.resolve(
                helper.getLevel(), corePosition);
        if (!resolution.valid()) {
            helper.fail("Failed reactor snapshot could not be resolved: " + resolution.failure());
            return;
        }

        ProjectedEffectContext below = new ProjectedEffectContext(
                helper.getLevel(),
                subLevel,
                corePosition,
                null,
                Vec3.atCenterOf(corePosition),
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, -1.0D),
                3.999D,
                null);
        ProjectedEffectContext exact = new ProjectedEffectContext(
                helper.getLevel(),
                subLevel,
                corePosition,
                null,
                Vec3.atCenterOf(corePosition),
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, -1.0D),
                4.0D,
                null);
        ImpactPayloadAdapter.Probe belowProbe = DraconicReactorImpactAdapter.INSTANCE.probe(
                below, resolution.snapshot());
        ImpactPayloadAdapter.Probe exactProbe = DraconicReactorImpactAdapter.INSTANCE.probe(
                exact, resolution.snapshot());
        if (belowProbe.disposition() != ImpactPayloadAdapter.ProbeDisposition.ARMED_BUT_BELOW_THRESHOLD
                || exactProbe.disposition() != ImpactPayloadAdapter.ProbeDisposition.READY) {
            helper.fail("Failed reactor probe did not preserve the exact 4.0 impact threshold");
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 320)
    public static void failedReactorTransportPreservesCountdownAndSurroundings(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(5.5D, 40.0D, 5.5D)),
                accessor -> placeFailedReactor(accessor, true));
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        Vector3d initialPosition = new Vector3d(subLevel.logicalPose().position());
        Vector3d targetVelocity = absoluteDirection(helper, new Vector3d(0.5D, 0.5D, 0.0D));

        helper.startSequence()
                .thenExecuteFor(
                        240,
                        () -> LetEmBurnGameTests.maintainVelocity(
                                handle, subLevel, targetVelocity))
                .thenExecute(() -> {
                    if (!(subLevel.getPlot()
                            .getEmbeddedLevelAccessor()
                            .getBlockEntity(BlockPos.ZERO) instanceof TileReactorCore core)) {
                        helper.fail("Failed reactor disappeared during safe Sable transport");
                        return;
                    }
                    if (!subLevel.getPlot()
                            .getEmbeddedLevelAccessor()
                            .getBlockState(BlockPos.ZERO.east())
                            .is(Blocks.STONE)) {
                        helper.fail("Failed reactor eroded an adjacent payload block in the Sable sublevel");
                    }
                    int countdown = core.explosionCountdown.get();
                    if (countdown < 0 || countdown >= SAFE_COUNTDOWN) {
                        helper.fail("Failed reactor countdown was not preserved and advanced during transport");
                    }
                    if (new Vector3d(subLevel.logicalPose().position()).distance(initialPosition) < 0.25D) {
                        helper.fail("Failed reactor payload did not move under Sable physics");
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 140)
    public static void failedReactorBelowThresholdRemainsPayload(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        DraconicExplosionAudit.clearWithin(helper.getBounds());
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D)),
                accessor -> placeFailedReactor(accessor, false));
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        BlockPos corePosition = subLevel.getPlot().getCenterBlock();
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 3.5D));

        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    if (DraconicExplosionAudit.suppressedDetonationsWithin(helper.getBounds()) != 0) {
                        helper.fail("Below-threshold reactor impact queued an explosion");
                    }
                    if (!subLevel.getLevel().getBlockState(corePosition).is(DEContent.REACTOR_CORE.get())) {
                        helper.fail("Below-threshold reactor impact consumed its payload");
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 160)
    public static void failedReactorImpactQueuesExactlyOnceWithoutDetonation(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        DraconicExplosionAudit.clearWithin(helper.getBounds());
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D)),
                accessor -> placeFailedReactor(accessor, false));
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        BlockPos corePosition = subLevel.getPlot().getCenterBlock();
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

        helper.startSequence()
                .thenExecuteFor(20, () -> {
                    if (DraconicExplosionAudit.suppressedDetonationsWithin(helper.getBounds()) > 1) {
                        helper.fail("One reactor impact queued more than one native explosion");
                    }
                })
                .thenExecute(() -> {
                    if (DraconicExplosionAudit.suppressedDetonationsWithin(helper.getBounds()) != 1) {
                        helper.fail(("Reactor impact did not construct exactly one native explosion; "
                                + "body=%s, velocity=%s, mass=%s, payload=%s, pending=%d")
                                        .formatted(
                                                localPosition(helper, subLevel.logicalPose().position()),
                                                LetEmBurnGameTests.velocityOrRemoved(handle),
                                                subLevel.getMassTracker().getMass(),
                                                subLevel.getLevel().getBlockState(corePosition),
                                                ChainReactionCoordinator.INSTANCE.pendingCount(helper.getLevel())));
                    }
                    if (!subLevel.getLevel().getBlockState(corePosition).isAir()) {
                        helper.fail("Committed reactor payload remained in the Sable sublevel");
                    }
                })
                .thenSucceed();
    }

    static void placeFailedReactor(CommonLevelAccessor accessor, boolean includeIntrusion) {
        accessor.setBlock(BlockPos.ZERO, DEContent.REACTOR_CORE.get().defaultBlockState(), 3);
        if (!(accessor.getBlockEntity(BlockPos.ZERO) instanceof TileReactorCore core)) {
            throw new IllegalStateException("Draconic reactor core block entity was not created");
        }
        configureFailedReactor(core);
        if (includeIntrusion) {
            accessor.setBlock(BlockPos.ZERO.east(), Blocks.STONE.defaultBlockState(), 3);
        }
    }

    static void configureFailedReactor(TileReactorCore core) {
        core.convertedFuel.set(72.0D);
        core.reactableFuel.set(72.0D);
        core.explosionCountdown.set(SAFE_COUNTDOWN);
        core.reactorState.set(ReactorState.BEYOND_HOPE);
        core.setChanged();
    }
}
