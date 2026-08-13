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

import ballistix.common.block.subtype.SubtypeBlast;
import ballistix.registers.BallistixBlocks;
import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.gametest.audit.BallistixImpactAudit;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import mekanism.common.attachments.BlockData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@PrefixGameTestTemplate(false)
public final class BallistixMekanismPayloadGameTests {
    private BallistixMekanismPayloadGameTests() {}

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 120)
    public static void cardboardCondensiveImpactUsesNativeBlast(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        BallistixImpactAudit.clearWithin(helper.getBounds());
        BlockData content = new BlockData(
                BallistixBlocks.BLOCKS_EXPLOSIVE
                        .getValue(SubtypeBlast.condensive)
                        .defaultBlockState(),
                null);
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D)),
                accessor -> MekanismPayloadGameTests.placeCardboardBox(accessor, content));
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    if (BallistixImpactAudit.impactsWithin(helper.getBounds()) != 1
                            || BallistixImpactAudit.boxedImpactsWithin(helper.getBounds()) != 1) {
                        helper.fail("Cardboard Ballistix payload did not preserve its native blast type");
                    }
                })
                .thenSucceed();
    }
}
