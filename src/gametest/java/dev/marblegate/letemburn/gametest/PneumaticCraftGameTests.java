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
import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.integration.pneumaticcraft.PneumaticImpactCollisionCallback;
import dev.marblegate.letemburn.integration.pneumaticcraft.PneumaticImpactModel;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import me.desht.pneumaticcraft.common.block.entity.AbstractAirHandlingBlockEntity;
import me.desht.pneumaticcraft.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@PrefixGameTestTemplate(false)
public final class PneumaticCraftGameTests {
    private static final double VELOCITY_EPSILON = 1.0E-8D;

    private PneumaticCraftGameTests() {}

    @GameTest(batch = "letemburn_pnc_positive_leak", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void positiveLeakMovesAndRotatesOffCentreBody(GameTestHelper helper) {
        MachineBody body = spawnMachineBody(helper, new Vector3d(4.0D, 5.0D, 4.0D), true);
        AtomicInteger airBefore = new AtomicInteger();
        AtomicReference<Vector3d> linearBefore = new AtomicReference<>();
        AtomicReference<Vector3d> angularBefore = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    body.handler().setPressure(2.0F);
                    body.handler().setSideLeaking(Direction.EAST);
                    airBefore.set(body.handler().getAir());
                    linearBefore.set(body.handle().getLinearVelocity(new Vector3d()));
                    angularBefore.set(body.handle().getAngularVelocity(new Vector3d()));
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    Vector3d linearAfter = body.handle().getLinearVelocity(new Vector3d());
                    Vector3d angularAfter = body.handle().getAngularVelocity(new Vector3d());
                    if (body.handler().getAir() >= airBefore.get()) {
                        helper.fail("PNC native leak did not reduce the positive-pressure air amount");
                    }
                    if (linearAfter.x >= linearBefore.get().x - VELOCITY_EPSILON) {
                        helper.fail(("Positive-pressure east leak did not push the Sable body west; before=%s, after=%s")
                                .formatted(linearBefore.get(), linearAfter));
                    }
                    if (new Vector3d(angularAfter).sub(angularBefore.get()).lengthSquared() <= VELOCITY_EPSILON * VELOCITY_EPSILON) {
                        helper.fail(("Off-centre PNC leak did not rotate the Sable body; before=%s, after=%s")
                                .formatted(angularBefore.get(), angularAfter));
                    }
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_pnc_negative_leak", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void negativePressureReversesLeakThrust(GameTestHelper helper) {
        MachineBody body = spawnMachineBody(helper, new Vector3d(4.0D, 5.0D, 4.0D), false);
        AtomicInteger absoluteAirBefore = new AtomicInteger();
        AtomicReference<Vector3d> linearBefore = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    body.handler().setPressure(-2.0F);
                    body.handler().setSideLeaking(Direction.EAST);
                    absoluteAirBefore.set(Math.abs(body.handler().getAir()));
                    linearBefore.set(body.handle().getLinearVelocity(new Vector3d()));
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    Vector3d linearAfter = body.handle().getLinearVelocity(new Vector3d());
                    if (Math.abs(body.handler().getAir()) >= absoluteAirBefore.get()) {
                        helper.fail("PNC native leak did not move negative pressure toward ambient pressure");
                    }
                    if (linearAfter.x <= linearBefore.get().x + VELOCITY_EPSILON) {
                        helper.fail(("Negative-pressure east leak did not push the Sable body east; before=%s, after=%s")
                                .formatted(linearBefore.get(), linearAfter));
                    }
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_pnc_impact_thresholds", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void impactThresholdsLeakAndRuptureTransactionally(GameTestHelper helper) {
        if (!BlockWithSubLevelCollisionCallback.hasCallback(
                ModBlocks.AIR_COMPRESSOR.get().defaultBlockState())) {
            helper.fail("Air-handling PNC block did not expose its Sable collision callback");
        }
        if (BlockWithSubLevelCollisionCallback.hasCallback(
                ModBlocks.HEAT_SINK.get().defaultBlockState())) {
            helper.fail("PNC block without an IAirHandlerMachine incorrectly exposed a collision callback");
        }

        Block impactMachine = ModBlocks.AIR_COMPRESSOR.get();
        MachineBody below = spawnMachineBody(
                helper, new Vector3d(3.0D, 5.0D, 3.0D), true, impactMachine);
        MachineBody leaking = spawnMachineBody(
                helper, new Vector3d(7.0D, 5.0D, 3.0D), true, impactMachine);
        MachineBody rupturing = spawnMachineBody(
                helper, new Vector3d(11.0D, 5.0D, 3.0D), true, impactMachine);
        MachineBody critical = spawnMachineBody(
                helper, new Vector3d(15.0D, 5.0D, 3.0D), true, impactMachine);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    below.handler().setPressure(2.0F);
                    leaking.handler().setPressure(2.0F);
                    rupturing.handler().setPressure(2.0F);
                    critical.handler().setPressure(critical.handler().getCriticalPressure());

                    double ratio = Math.clamp(
                            Math.abs(leaking.handler().getPressure())
                                    / leaking.handler().getCriticalPressure(),
                            0.0D,
                            2.0D);
                    double belowSpeed = speedForSeverity(0.99D, ratio);
                    double leakSpeed = speedForSeverity(1.01D, ratio);
                    double ruptureSpeed = speedForSeverity(2.26D, ratio);

                    assertAction(helper, below, belowSpeed, PneumaticImpactModel.Action.NONE);
                    assertAction(helper, leaking, leakSpeed, PneumaticImpactModel.Action.LEAK);
                    assertAction(helper, rupturing, ruptureSpeed, PneumaticImpactModel.Action.RUPTURE);
                    assertAction(helper, critical, 4.0D, PneumaticImpactModel.Action.RUPTURE);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    if (below.subLevel().getLevel().getBlockState(below.machinePosition()).isAir()) {
                        helper.fail("Below-threshold PNC impact removed the machine");
                    }
                    if (leaking.handler().getSideLeaking() != leaking.impactFace()) {
                        helper.fail("Mid-severity PNC impact did not set the native collision-face leak");
                    }
                    assertRuptured(helper, rupturing);
                    assertRuptured(helper, critical);
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_pnc_physical_collision", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 120)
    public static void physicalSableCollisionDispatchesMachineImpact(GameTestHelper helper) {
        if (!BlockWithSubLevelCollisionCallback.hasCallback(
                ModBlocks.AIR_COMPRESSOR.get().defaultBlockState())) {
            helper.fail("PNC air compressor did not expose its Sable collision callback");
        }

        LetEmBurnGameTests.addWall(helper, 19);
        Block impactMachine = ModBlocks.AIR_COMPRESSOR.get();
        MachineBody below = spawnMachineBody(
                helper, new Vector3d(2.5D, 1.6D, 5.5D), false, impactMachine);
        MachineBody leaking = spawnMachineBody(
                helper, new Vector3d(6.5D, 1.6D, 5.5D), false, impactMachine);
        MachineBody rupturing = spawnMachineBody(
                helper, new Vector3d(10.5D, 1.6D, 5.5D), false, impactMachine);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    below.handler().setPressure(2.0F);
                    leaking.handler().setPressure(2.0F);
                    rupturing.handler().setPressure(2.0F);
                    dropAtSpeed(helper, below, 3.0D);
                    dropAtSpeed(helper, leaking, 6.0D);
                    dropAtSpeed(helper, rupturing, 8.0D);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    if (below.subLevel().getLevel().getBlockState(below.machinePosition()).isAir()) {
                        helper.fail("Physical below-threshold PNC collision removed the machine");
                    }
                    if (leaking.handler().getSideLeaking() == null) {
                        helper.fail(("Physical mid-severity PNC collision did not start a native leak; "
                                + "body=%s, velocity=%s, pressure=%s")
                                        .formatted(
                                                dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.localPosition(
                                                        helper, leaking.subLevel().logicalPose().position()),
                                                LetEmBurnGameTests.velocityOrRemoved(leaking.handle()),
                                                leaking.handler().getPressure()));
                    }
                    if (!rupturing.subLevel()
                            .getLevel()
                            .getBlockState(rupturing.machinePosition())
                            .isAir()) {
                        helper.fail(("Physical high-severity PNC collision did not rupture the machine; "
                                + "body=%s, velocity=%s, pressure=%s, air=%s, leakFace=%s")
                                        .formatted(
                                                dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.localPosition(
                                                        helper, rupturing.subLevel().logicalPose().position()),
                                                LetEmBurnGameTests.velocityOrRemoved(rupturing.handle()),
                                                rupturing.handler().getPressure(),
                                                rupturing.handler().getAir(),
                                                rupturing.handler().getSideLeaking()));
                    }
                })
                .thenSucceed();
    }

    private static MachineBody spawnMachineBody(
            GameTestHelper helper, Vector3d position, boolean offsetMass) {
        return spawnMachineBody(helper, position, offsetMass, ModBlocks.PRESSURE_TUBE.get());
    }

    private static MachineBody spawnMachineBody(
            GameTestHelper helper, Vector3d position, boolean offsetMass, Block machineBlock) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, position),
                accessor -> {
                    accessor.setBlock(
                            BlockPos.ZERO,
                            machineBlock.defaultBlockState(),
                            3);
                    if (offsetMass) {
                        accessor.setBlock(new BlockPos(0, 0, 1), Blocks.IRON_BLOCK.defaultBlockState(), 3);
                    }
                });
        BlockPos machinePosition = subLevel.getPlot().getCenterBlock();
        if (!(subLevel.getLevel().getBlockEntity(machinePosition) instanceof AbstractAirHandlingBlockEntity blockEntity)) {
            throw new IllegalStateException("PNC machine did not create an air-handling block entity");
        }
        Direction impactFace = exposedFace(blockEntity);
        IAirHandlerMachine handler = blockEntity.getAirHandler(impactFace);
        if (handler == null) {
            throw new IllegalStateException("PNC machine did not expose a machine air handler");
        }
        return new MachineBody(
                subLevel,
                machinePosition,
                handler,
                impactFace,
                physicsSystem.getPhysicsHandle(subLevel));
    }

    private static Direction exposedFace(AbstractAirHandlingBlockEntity blockEntity) {
        if (blockEntity.getAirHandler(Direction.EAST) != null) {
            return Direction.EAST;
        }
        for (Direction direction : Direction.values()) {
            if (blockEntity.getAirHandler(direction) != null) {
                return direction;
            }
        }
        throw new IllegalStateException("PNC machine has no exposed machine air handler");
    }

    private static void assertAction(
            GameTestHelper helper,
            MachineBody body,
            double impactVelocity,
            PneumaticImpactModel.Action expected) {
        PneumaticImpactModel.Action actual = PneumaticImpactCollisionCallback.INSTANCE.handle(
                impactContext(helper, body, impactVelocity));
        if (actual != expected) {
            helper.fail("Expected PNC impact action %s but got %s".formatted(expected, actual));
        }
    }

    private static ProjectedEffectContext impactContext(
            GameTestHelper helper, MachineBody body, double impactVelocity) {
        BlockPos position = body.machinePosition();
        Direction face = body.impactFace();
        Vec3 localImpact = Vec3.atCenterOf(position).add(
                face.getStepX() * 0.5D,
                face.getStepY() * 0.5D,
                face.getStepZ() * 0.5D);
        Vector3d global = body.subLevel().logicalPose().position();
        return new ProjectedEffectContext(
                helper.getLevel(),
                body.subLevel(),
                position,
                null,
                localImpact,
                new Vec3(global.x, global.y, global.z),
                Vec3.atLowerCornerOf(face.getNormal()),
                impactVelocity,
                null);
    }

    private static double speedForSeverity(double severity, double pressureRatio) {
        return Math.nextUp(Math.sqrt(severity * 16.0D / (0.5D + 0.5D * pressureRatio)));
    }

    private static void dropAtSpeed(GameTestHelper helper, MachineBody body, double speed) {
        LetEmBurnGameTests.launch(
                helper,
                body.handle(),
                new Vector3d(0.0D, -body.subLevel().getMassTracker().getMass() * speed, 0.0D));
    }

    private static void assertRuptured(GameTestHelper helper, MachineBody body) {
        if (!body.subLevel().getLevel().getBlockState(body.machinePosition()).isAir()) {
            helper.fail("High-severity PNC impact did not remove the machine");
        }
        if (body.handler().getAir() != 0) {
            helper.fail("PNC rupture removed its machine before draining the air handler");
        }
        if (!body.subLevel()
                .getLevel()
                .getBlockState(body.machinePosition().offset(0, 0, 1))
                .is(Blocks.IRON_BLOCK)) {
            helper.fail("PNC rupture removed unrelated structure blocks");
        }
    }

    private record MachineBody(
            ServerSubLevel subLevel,
            BlockPos machinePosition,
            IAirHandlerMachine handler,
            Direction impactFace,
            RigidBodyHandle handle) {}
}
