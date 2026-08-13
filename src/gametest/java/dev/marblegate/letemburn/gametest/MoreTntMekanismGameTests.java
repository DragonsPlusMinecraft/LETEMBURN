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
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.spawnSubLevel;

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.gametest.audit.MoreTntImpactAudit;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.discusser.moretnt.objects.blocks.BaseTNTBlock;
import io.github.discusser.moretnt.objects.registration.MoreTNTBlocks;
import mekanism.common.attachments.BlockData;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@PrefixGameTestTemplate(false)
public final class MoreTntMekanismGameTests {
    private MoreTntMekanismGameTests() {}

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 140)
    public static void cardboardDomeImpactUsesNativeNextTickExplosion(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        Vector3d initialPosition = absolutePosition(helper, new Vector3d(2.5D, 8.0D, 1.5D));
        BlockState domeTnt = MoreTNTBlocks.DOME_TNT
                .block()
                .get()
                .defaultBlockState()
                .setValue(BaseTNTBlock.FACING, Direction.SOUTH);
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                initialPosition,
                accessor -> MekanismPayloadGameTests.placeCardboardBox(
                        accessor, new BlockData(domeTnt, null)));
        var subLevelId = subLevel.getUniqueId();
        MoreTntImpactAudit.clearForSubLevel(subLevelId);
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

        helper.startSequence()
                .thenIdle(24)
                .thenExecute(() -> {
                    var events = MoreTntImpactAudit.eventsForSubLevel(subLevelId);
                    if (events.size() != 1) {
                        helper.fail("Cardboard Dome TNT did not create exactly one native primed entity");
                    }
                    var event = events.getFirst();
                    if (!event.blockId().getPath().equals("dome_tnt")
                            || event.envelopeDepth() != 1
                            || event.localFacing() != Direction.SOUTH
                            || event.projectedFacing() != Direction.SOUTH
                            || Float.compare(event.size(), 5.0F) != 0
                            || event.fire()
                            || event.initialFuse() != 1
                            || event.parentContainedSourceBlock()) {
                        helper.fail("Cardboard Dome TNT lost native payload properties: " + event);
                    }
                    if (MoreTntGameTests.countBlocks(
                            helper, event.position(), 7, state -> state.is(Blocks.GLASS)) == 0) {
                        helper.fail("Cardboard Dome TNT did not execute its native glass-dome effect");
                    }
                })
                .thenSucceed();
    }
}
