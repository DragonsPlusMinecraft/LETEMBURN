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

import ballistix.common.blast.tier3.BlastNuclear;
import ballistix.common.blast.util.Blast;
import ballistix.common.block.subtype.SubtypeBlast;
import ballistix.compatibility.nuclearscience.RadiationHandler;
import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.impulse.ExplosionImpulseBridge.ApplicationResult;
import dev.marblegate.letemburn.integration.ballistix.BallistixCompatibilityHooks;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import nuclearscience.registers.NuclearScienceBlocks;
import org.joml.Vector3d;
import voltaic.api.radiation.RadiationSystem;

@PrefixGameTestTemplate(false)
public final class BallistixNuclearScienceGameTests {
    private BallistixNuclearScienceGameTests() {}

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 40)
    public static void nativeIrradiationHandlerUsesNuclearScienceBlocks(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());

        RadiationHandler.addNuclearExplosiveIrradidatedBlock(position, helper.getLevel());

        if (!helper.getLevel().getBlockState(position).is(NuclearScienceBlocks.BLOCK_RADIOACTIVEAIR.get())) {
            helper.fail("Ballistix did not delegate irradiated air creation to Nuclear Science");
        }
        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    @GameTest(batch = "letemburn_ballistix_ns_bridge", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 60)
    public static void nuclearBridgeAddsImpulseWithoutRadiation(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        ServerSubLevel subLevel = spawnSingleBlockSubLevel(
                container,
                absolutePosition(helper, new Vector3d(5.5D, 3.0D, 5.5D)),
                Blocks.IRON_BLOCK.defaultBlockState());
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        BlockPos projectedOrigin = helper.absolutePos(new BlockPos(1, 2, 1));
        Set<BlockPos> radiationBefore = new HashSet<>(RadiationSystem.getRadiationSources(helper.getLevel()));

        Blast blast = SubtypeBlast.nuclear.createBlast(helper.getLevel(), projectedOrigin, null, null);
        if (!(blast instanceof BlastNuclear) || !blast.position.equals(projectedOrigin)) {
            helper.fail("Ballistix nuclear blast was not constructed at the projected parent-world origin");
        }

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    ApplicationResult first = BallistixCompatibilityHooks.onNativeBlastStarted(blast);
                    ApplicationResult second = BallistixCompatibilityHooks.onNativeBlastStarted(blast);
                    if (first.deduplicated() || first.affectedBodies() < 1 || !second.deduplicated()) {
                        helper.fail("Projected nuclear blast impulse was not applied exactly once: first="
                                + first + ", second=" + second);
                    }
                    if (!radiationBefore.equals(
                            new HashSet<>(RadiationSystem.getRadiationSources(helper.getLevel())))) {
                        helper.fail("LET!EM!BURN! created a second radiation source while bridging nuclear impulse");
                    }
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    Vector3d velocity = handle.getLinearVelocity(new Vector3d());
                    if (velocity.lengthSquared() <= 1.0E-12D) {
                        helper.fail("Projected Ballistix nuclear impulse did not move the nearby Sable body");
                    }
                })
                .thenSucceed();
    }
}
