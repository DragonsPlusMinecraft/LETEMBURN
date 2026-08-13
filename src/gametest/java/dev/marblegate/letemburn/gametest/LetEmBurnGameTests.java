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
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.spawnSingleBlockSubLevel;

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.effect.ChainReactionCoordinator;
import dev.marblegate.letemburn.config.LetEmBurnConfig;
import dev.marblegate.letemburn.gametest.audit.VanillaTntImpactAudit;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder(LetEmBurn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LetEmBurnGameTests {
    private LetEmBurnGameTests() {}

    @GameTest(template = "bootstrap", timeoutTicks = 20)
    public static void bootstrapAndConfigLoad(GameTestHelper helper) {
        if (LetEmBurnConfig.MAX_ENVELOPE_DEPTH.get() != 8
                || LetEmBurnConfig.MAX_PAYLOAD_BYTES.get() != 1_048_576) {
            helper.fail("LET!EM!BURN! defaults were not loaded");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "bootstrap", timeoutTicks = 140)
    public static void directVanillaTntUsesDeferredNativeEntity(GameTestHelper helper) {
        ServerSubLevelContainer container = requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = requirePhysics(container);
        addWall(helper, 3);
        VanillaTntImpactAudit.clearWithin(helper.getBounds());

        ServerSubLevel subLevel = spawnSingleBlockSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D)),
                Blocks.TNT.defaultBlockState());
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        AtomicBoolean observedNativeTnt = new AtomicBoolean();
        launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

        helper.startSequence()
                .thenExecuteFor(20, () -> {
                    helper.getLevel()
                            .getEntitiesOfClass(PrimedTnt.class, helper.getBounds())
                            .forEach(tnt -> {
                                if (tnt.getFuse() <= 4 && tnt.getBlockState().is(Blocks.TNT)) {
                                    observedNativeTnt.set(true);
                                }
                            });
                })
                .thenExecute(() -> {
                    var spawnEvents = VanillaTntImpactAudit.spawnEventsWithin(helper.getBounds());
                    if (spawnEvents.size() != 1
                            || spawnEvents.getFirst().initialFuse() != 4
                            || spawnEvents.getFirst().envelopeDepth() != 0
                            || !observedNativeTnt.get()) {
                        Vector3d local = localPosition(helper, subLevel.logicalPose().position());
                        helper.fail(("A direct projected TNT payload did not create exactly one native "
                                + "PrimedTnt with an initial 4 tick fuse; events=%s, "
                                + "observed=%s, body=%s, velocity=%s, mass=%s, payload=%s, "
                                + "lastSpawn=%s, pending=%d")
                                        .formatted(
                                                spawnEvents,
                                                observedNativeTnt.get(),
                                                local,
                                                velocityOrRemoved(handle),
                                                subLevel.getMassTracker().getMass(),
                                                subLevel.getLevel()
                                                        .getBlockState(subLevel.getPlot().getCenterBlock()),
                                                VanillaTntImpactAudit.lastSpawnPosition(),
                                                ChainReactionCoordinator.INSTANCE.pendingCount(helper.getLevel())));
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "bootstrap", timeoutTicks = 100)
    public static void directVanillaTntSurvivesBelowThresholdCollision(GameTestHelper helper) {
        ServerSubLevelContainer container = requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = requirePhysics(container);
        addWall(helper, 3);
        VanillaTntImpactAudit.clearWithin(helper.getBounds());

        ServerSubLevel subLevel = spawnSingleBlockSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 2.25D)),
                Blocks.TNT.defaultBlockState());
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        BlockPos payloadPosition = subLevel.getPlot().getCenterBlock();

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> maintainVelocity(
                        handle,
                        subLevel,
                        absoluteDirection(helper, new Vector3d(0.0D, 0.0D, 3.0D))))
                .thenIdle(12)
                .thenExecute(() -> {
                    var belowThreshold = VanillaTntImpactAudit.belowThresholdEventsWithin(helper.getBounds());
                    if (belowThreshold.isEmpty()
                            || belowThreshold.stream().anyMatch(event -> event.envelopeDepth() != 0)
                            || VanillaTntImpactAudit.spawnsWithin(helper.getBounds()) != 0) {
                        helper.fail("Direct TNT did not record only below-threshold collisions: "
                                + belowThreshold);
                    }
                    if (!subLevel.getLevel().getBlockState(payloadPosition).is(Blocks.TNT)) {
                        helper.fail("Below-threshold direct TNT collision consumed the payload");
                    }
                })
                .thenSucceed();
    }

    static ServerSubLevelContainer requireContainer(GameTestHelper helper) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container == null) {
            throw new IllegalStateException("Sable sublevel container is unavailable");
        }
        return container;
    }

    static SubLevelPhysicsSystem requirePhysics(ServerSubLevelContainer container) {
        SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        if (physicsSystem == null) {
            throw new IllegalStateException("Sable physics system is unavailable");
        }
        return physicsSystem;
    }

    static void addWall(GameTestHelper helper, int z) {
        for (int x = 0; x < 20; x++) {
            for (int floorZ = 0; floorZ < 20; floorZ++) {
                helper.setBlock(new BlockPos(x, 0, floorZ), Blocks.OBSIDIAN);
            }
            for (int y = 1; y < 64; y++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.OBSIDIAN);
            }
        }
    }

    static void maintainVelocity(
            RigidBodyHandle handle, ServerSubLevel subLevel, Vector3d targetVelocity) {
        if (!handle.isValid()) {
            return;
        }
        Vector3d currentVelocity = handle.getLinearVelocity(new Vector3d());
        Vector3d correctingImpulse = new Vector3d(targetVelocity)
                .sub(currentVelocity)
                .mul(subLevel.getMassTracker().getMass());
        handle.applyLinearImpulse(correctingImpulse);
    }

    static void launch(
            GameTestHelper helper, RigidBodyHandle handle, Vector3d localImpulse) {
        if (!handle.isValid()) {
            return;
        }
        handle.applyLinearImpulse(absoluteDirection(helper, localImpulse));
    }

    static String velocityOrRemoved(RigidBodyHandle handle) {
        return handle.isValid() ? handle.getLinearVelocity(new Vector3d()).toString() : "removed";
    }
}
