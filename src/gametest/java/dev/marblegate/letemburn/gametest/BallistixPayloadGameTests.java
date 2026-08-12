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

import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.absolutePosition;
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.spawnSingleBlockSubLevel;

import ballistix.api.event.BlastEvent;
import ballistix.common.blast.tier3.BlastAntimatter;
import ballistix.common.blast.tier3.BlastDarkmatter;
import ballistix.common.blast.tier3.BlastLargeAntimatter;
import ballistix.common.blast.tier3.BlastNuclear;
import ballistix.common.blast.util.Blast;
import ballistix.common.block.subtype.SubtypeBlast;
import ballistix.common.settings.BallistixConfig;
import ballistix.registers.BallistixBlocks;
import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.compat.ballistix.BallistixCompatibilityHooks;
import dev.marblegate.letemburn.compat.ballistix.BallistixImpactAudit;
import dev.marblegate.letemburn.compat.ballistix.BallistixImpulseProfiles;
import dev.marblegate.letemburn.compat.core.ExplosionImpulseBridge.ApplicationResult;
import dev.marblegate.letemburn.compat.core.ExplosionImpulseProfile;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import voltaic.api.radiation.RadiationSystem;

@PrefixGameTestTemplate(false)
public final class BallistixPayloadGameTests {
    private BallistixPayloadGameTests() {}

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 120)
    public static void directCondensiveImpactUsesNativeBlast(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        BallistixImpactAudit.clearWithin(helper.getBounds());
        BlockState payload = BallistixBlocks.BLOCKS_EXPLOSIVE
                .getValue(SubtypeBlast.condensive)
                .defaultBlockState();
        ServerSubLevel subLevel = spawnSingleBlockSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D)),
                payload);
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    if (BallistixImpactAudit.impactsWithin(helper.getBounds()) != 1) {
                        helper.fail("Direct Ballistix payload did not perform exactly one native blast");
                    }
                    if (BallistixImpactAudit.bridgeStartsWithin(helper.getBounds()) != 1) {
                        helper.fail("Direct Ballistix payload did not enter the Sable bridge exactly once");
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 60)
    public static void nativeBlastIdentityIsDeduplicatedPerTick(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        ServerSubLevel subLevel = spawnSingleBlockSubLevel(
                container,
                absolutePosition(helper, new Vector3d(5.5D, 3.0D, 5.5D)),
                Blocks.IRON_BLOCK.defaultBlockState());
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        AtomicReference<ApplicationResult> firstResult = new AtomicReference<>();
        AtomicReference<Vector3d> immediateLinearVelocity = new AtomicReference<>();
        AtomicReference<Vector3d> immediateAngularVelocity = new AtomicReference<>();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    Blast blast = SubtypeBlast.repulsive.createBlast(
                            helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null);
                    ApplicationResult first = BallistixCompatibilityHooks.onNativeBlastStarted(blast);
                    ApplicationResult second = BallistixCompatibilityHooks.onNativeBlastStarted(blast);
                    if (first.deduplicated() || first.affectedBodies() != 1 || !second.deduplicated()) {
                        helper.fail("Blast identity/game-tick deduplication failed");
                    }
                    firstResult.set(first);
                    immediateLinearVelocity.set(handle.getLinearVelocity(new Vector3d()));
                    immediateAngularVelocity.set(handle.getAngularVelocity(new Vector3d()));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    Vector3d linearVelocity = handle.getLinearVelocity(new Vector3d());
                    Vector3d angularVelocity = handle.getAngularVelocity(new Vector3d());
                    if (linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z <= 1.0E-12D) {
                        helper.fail(("Native Ballistix blast did not displace the Sable structure; "
                                + "immediateLinear=%s, delayedLinear=%s, immediateAngular=%s, "
                                + "delayedAngular=%s, mass=%s, bounds=%s, pose=%s, result=%s")
                                        .formatted(
                                                immediateLinearVelocity.get(),
                                                linearVelocity,
                                                immediateAngularVelocity.get(),
                                                angularVelocity,
                                                subLevel.getMassTracker().getMass(),
                                                subLevel.getPlot().getBoundingBox(),
                                                subLevel.logicalPose(),
                                                firstResult.get()));
                    }
                    if (angularVelocity.lengthSquared() <= 1.0E-12D) {
                        helper.fail("Off-centre Ballistix impulse did not produce Sable torque");
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 120)
    public static void cancelledNativeBlastRestoresProjectedPayload(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        BallistixImpactAudit.clearWithin(helper.getBounds());
        BlockState payload = BallistixBlocks.BLOCKS_EXPLOSIVE
                .getValue(SubtypeBlast.condensive)
                .defaultBlockState();
        ServerSubLevel subLevel = spawnSingleBlockSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D)),
                payload);
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        AtomicInteger cancellations = new AtomicInteger();
        var listener = new java.util.function.Consumer<BlastEvent.ConstructBlastEvent>() {
            @Override
            public void accept(BlastEvent.ConstructBlastEvent event) {
                if (event.iExplosion.getBlastType() == SubtypeBlast.condensive
                        && helper.getBounds().inflate(2.0D).contains(event.iExplosion.position.getCenter())
                        && cancellations.getAndIncrement() == 0) {
                    event.setCanceled(true);
                    Vector3d stoppingImpulse = handle.getLinearVelocity(new Vector3d())
                            .negate()
                            .mul(subLevel.getMassTracker().getMass());
                    handle.applyLinearImpulse(stoppingImpulse);
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> NeoForge.EVENT_BUS.unregister(listener))
                .thenExecute(() -> {
                    BlockPos payloadPosition = subLevel.getPlot().getCenterBlock();
                    if (cancellations.get() < 1) {
                        helper.fail("Projected Ballistix blast did not reach the cancellable native event");
                    }
                    if (!subLevel.getLevel().getBlockState(payloadPosition).equals(payload)) {
                        helper.fail("Cancelled Ballistix blast did not restore its projected payload");
                    }
                    if (BallistixImpactAudit.impactsWithin(helper.getBounds()) != 0
                            || BallistixImpactAudit.bridgeStartsWithin(helper.getBounds()) != 0) {
                        helper.fail("Cancelled Ballistix blast was incorrectly recorded as started");
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 40)
    public static void destructiveTierThreeBlastsAreConstructOnly(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        int radiationSourcesBefore = RadiationSystem.getRadiationSources(helper.getLevel()).size();
        Blast nuclear = SubtypeBlast.nuclear.createBlast(helper.getLevel(), position, null, null);
        Blast antimatter = SubtypeBlast.antimatter.createBlast(helper.getLevel(), position, null, null);
        Blast largeAntimatter = SubtypeBlast.largeantimatter.createBlast(helper.getLevel(), position, null, null);
        Blast darkmatter = SubtypeBlast.darkmatter.createBlast(helper.getLevel(), position, null, null);
        if (!(nuclear instanceof BlastNuclear)
                || !(antimatter instanceof BlastAntimatter)
                || !(largeAntimatter instanceof BlastLargeAntimatter)
                || !(darkmatter instanceof BlastDarkmatter)) {
            helper.fail("High-cost blast factory mapping changed");
        }

        AtomicInteger constructEvents = new AtomicInteger();
        var listener = new java.util.function.Consumer<BlastEvent.ConstructBlastEvent>() {
            @Override
            public void accept(BlastEvent.ConstructBlastEvent event) {
                if (event.iExplosion == nuclear) {
                    constructEvents.incrementAndGet();
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        NeoForge.EVENT_BUS.post(new BlastEvent.ConstructBlastEvent(helper.getLevel(), nuclear));
        NeoForge.EVENT_BUS.unregister(listener);
        if (constructEvents.get() != 1) {
            helper.fail("Ballistix construct event ownership changed");
        }
        if (RadiationSystem.getRadiationSources(helper.getLevel()).size() != radiationSourcesBefore) {
            helper.fail("Construct-only compatibility check created a second radiation source");
        }

        ExplosionImpulseProfile nuclearProfile = BallistixImpulseProfiles.resolve(SubtypeBlast.nuclear);
        double expectedEntityRadius = 4.0D
                * BallistixConfig.INSTANCE.EXPLOSIVE_NUCLEAR_SIZE.getAsDouble();
        if (nuclearProfile == null
                || Double.compare(nuclearProfile.radius(), expectedEntityRadius) != 0) {
            helper.fail("Nuclear impulse profile was not constructible");
        }
        ResourceLocation type = nuclear.getBlastType().id();
        if (!type.equals(SubtypeBlast.nuclear.id())) {
            helper.fail("Nuclear blast type identity changed");
        }
        // No performExplosion(), preExplode(), explode(), or postExplode() call is permitted here.
        helper.succeed();
    }
}
