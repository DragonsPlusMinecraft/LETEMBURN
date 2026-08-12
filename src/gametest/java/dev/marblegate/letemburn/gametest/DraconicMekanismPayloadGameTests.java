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
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.localPosition;
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.spawnSubLevel;

import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.brandon3055.draconicevolution.init.DEContent;
import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.compat.core.ChainReactionCoordinator;
import dev.marblegate.letemburn.compat.draconic.DraconicExplosionAudit;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import mekanism.common.attachments.BlockData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@PrefixGameTestTemplate(false)
public final class DraconicMekanismPayloadGameTests {
    private DraconicMekanismPayloadGameTests() {}

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 160)
    public static void cardboardReactorImpactQueuesExactlyOnceWithoutDetonation(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        DraconicExplosionAudit.clearWithin(helper.getBounds());

        TileReactorCore reactor = new TileReactorCore(
                BlockPos.ZERO, DEContent.REACTOR_CORE.get().defaultBlockState());
        DraconicPayloadGameTests.configureFailedReactor(reactor);
        BlockData reactorPayload = new BlockData(
                DEContent.REACTOR_CORE.get().defaultBlockState(),
                reactor.saveWithFullMetadata(helper.getLevel().registryAccess()));
        BlockData nestedPayload = MekanismPayloadGameTests.nestedContent(
                helper.getLevel().registryAccess(), 2, reactorPayload);
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D)),
                accessor -> MekanismPayloadGameTests.placeCardboardBox(accessor, nestedPayload));
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        BlockPos payloadPosition = subLevel.getPlot().getCenterBlock();
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

        helper.startSequence()
                .thenExecuteFor(20, () -> {
                    if (DraconicExplosionAudit.suppressedDetonationsWithin(helper.getBounds()) > 1) {
                        helper.fail("One cardboard reactor impact queued more than one native explosion");
                    }
                })
                .thenExecute(() -> {
                    if (DraconicExplosionAudit.suppressedDetonationsWithin(helper.getBounds()) != 1) {
                        helper.fail(("Cardboard reactor impact did not construct exactly one native explosion; "
                                + "body=%s, velocity=%s, mass=%s, payload=%s, pending=%d")
                                        .formatted(
                                                localPosition(helper, subLevel.logicalPose().position()),
                                                LetEmBurnGameTests.velocityOrRemoved(handle),
                                                subLevel.getMassTracker().getMass(),
                                                subLevel.getLevel().getBlockState(payloadPosition),
                                                ChainReactionCoordinator.INSTANCE.pendingCount(helper.getLevel())));
                    }
                    if (!subLevel.getLevel().getBlockState(payloadPosition).isAir()) {
                        helper.fail("Committed cardboard reactor remained in the Sable sublevel");
                    }
                })
                .thenSucceed();
    }
}
