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
import dev.marblegate.letemburn.integration.nuclearscience.NuclearFissionProjectionAudit;
import dev.marblegate.letemburn.integration.nuclearscience.NuclearFissionProjectionAudit.Event;
import dev.marblegate.letemburn.integration.nuclearscience.NuclearFissionProjectionAudit.Kind;
import dev.marblegate.letemburn.integration.nuclearscience.NuclearFissionReactorAccess;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import nuclearscience.common.block.subtype.SubtypeNuclearMachine;
import nuclearscience.common.tile.reactor.fission.TileFissionReactorCore;
import nuclearscience.common.tile.reactor.fission.TileMeltedReactor;
import nuclearscience.registers.NuclearScienceBlocks;
import nuclearscience.registers.NuclearScienceItems;
import org.joml.Vector3d;
import voltaic.api.radiation.RadiationSystem;
import voltaic.prefab.tile.components.IComponentType;
import voltaic.prefab.tile.components.type.ComponentInventory;
import voltaic.prefab.tile.components.type.ComponentTickable;

@PrefixGameTestTemplate(false)
public final class NuclearScienceFissionGameTests {
    private static final double OVERHEAT_TEMPERATURE = 6000.0D;

    private NuclearScienceFissionGameTests() {}

    @GameTest(batch = "letemburn_ns_fission_ongoing", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void ongoingEffectsUseTheCurrentProjectedPosition(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(5.5D, 48.0D, 5.5D)),
                NuclearScienceFissionGameTests::placeFissionCore);
        BlockPos localCorePosition = subLevel.getPlot().getCenterBlock();
        TileFissionReactorCore core = requireFissionCore(helper, localCorePosition);
        installMinimumFuel(helper, core);
        Vec3 projectedBlockCenter = Sable.HELPER.projectOutOfSubLevel(helper.getLevel(), Vec3.atCenterOf(localCorePosition));
        Vec3 projectedHeatCenter = Sable.HELPER.projectOutOfSubLevel(
                helper.getLevel(), Vec3.atLowerCornerOf(localCorePosition));
        BlockPos globalOrigin = BlockPos.containing(projectedBlockCenter);

        Pig target = new Pig(EntityType.PIG, helper.getLevel());
        target.setPos(projectedHeatCenter.x, projectedHeatCenter.y, projectedHeatCenter.z);
        target.setNoAi(true);
        target.setNoGravity(true);
        helper.getLevel().addFreshEntity(target);
        helper.getLevel().setBlock(target.getOnPos(), Blocks.WATER.defaultBlockState(), 3);
        float initialHealth = target.getHealth();

        NuclearFissionProjectionAudit.beginCapture();
        core.temperature.setValue(1000.0D);
        ComponentTickable manualTick = new ComponentTickable(core);
        helper.getLevel().random.setSeed(findSoundSeed());
        ServerLevelData levelData = (ServerLevelData) core.getLevel().getLevelData();
        long originalGameTime = levelData.getGameTime();
        levelData.setGameTime(originalGameTime + Math.floorMod(-originalGameTime, 10L));
        ((NuclearFissionReactorAccess) core).letemburn$invokeTickServer(manualTick);
        levelData.setGameTime(originalGameTime);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    if (!RadiationSystem.getRadiationSources(helper.getLevel()).contains(globalOrigin)) {
                        helper.fail("Native fission radiation was not projected to the parent-world position");
                    }
                    if (RadiationSystem.getRadiationSources(helper.getLevel()).contains(localCorePosition)) {
                        helper.fail("Native fission radiation remained at the hidden plot-grid position");
                    }
                    assertProjectedEvent(helper, Kind.RADIATION, localCorePosition, projectedBlockCenter);
                    assertProjectedEvent(helper, Kind.SOUND, localCorePosition, projectedBlockCenter);
                    assertProjectedEvent(helper, Kind.HEAT_QUERY, localCorePosition, projectedHeatCenter);
                    if (!target.isAlive() || target.getHealth() >= initialHealth) {
                        helper.fail("Projected fission heat query did not damage a water-cooled external entity");
                    }
                    if (!subLevel.getLevel().getBlockState(localCorePosition).is(fissionCoreBlock())) {
                        helper.fail("Projecting ongoing fission effects moved the internal reactor core");
                    }
                    NuclearFissionProjectionAudit.endCapture();
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_ns_fission_meltdown", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 280)
    public static void minimumFuelMeltdownRunsOnceAtTheFrozenGlobalOrigin(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        ServerSubLevel reactorSubLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(5.5D, 192.0D, 5.5D)),
                NuclearScienceFissionGameTests::placeFissionCore);
        RigidBodyHandle reactorHandle = physicsSystem.getPhysicsHandle(reactorSubLevel);
        BlockPos localCorePosition = reactorSubLevel.getPlot().getCenterBlock();
        TileFissionReactorCore core = requireFissionCore(helper, localCorePosition);
        installMinimumFuel(helper, core);

        NuclearFissionProjectionAudit.beginCapture();
        keepOverheated(core);
        ComponentTickable manualTick = new ComponentTickable(core);
        AtomicReference<RigidBodyHandle> nearbyBody = new AtomicReference<>();
        AtomicReference<Vec3> expectedTriggerCenter = new AtomicReference<>();
        AtomicReference<BlockPos> preparedWater = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(() -> advanceToPreMeltdownTick(
                        helper, core, manualTick, reactorHandle, reactorSubLevel))
                .thenExecute(() -> {
                    int internalStoneBlocks = countInternalStoneBlocks(container);
                    if (internalStoneBlocks != 8) {
                        helper.fail("Native fission processing changed the internal reactor structure before "
                                + "meltdown: found "
                                + internalStoneBlocks
                                + " of 8 stone blocks");
                    }
                    Vec3 triggerCenter = Sable.HELPER.projectOutOfSubLevel(
                            helper.getLevel(), Vec3.atCenterOf(localCorePosition));
                    expectedTriggerCenter.set(triggerCenter);
                    BlockPos triggerOrigin = BlockPos.containing(triggerCenter);
                    helper.getLevel().setBlock(triggerOrigin.below(), Blocks.BEDROCK.defaultBlockState(), 3);
                    BlockPos waterPosition = triggerOrigin.east();
                    preparedWater.set(waterPosition);
                    helper.getLevel().setBlock(waterPosition, Blocks.WATER.defaultBlockState(), 3);
                    ServerSubLevel impulseTarget = spawnSubLevel(
                            container,
                            new Vector3d(
                                    triggerCenter.x,
                                    triggerCenter.y,
                                    triggerCenter.z)
                                            .add(7.0D, 0.0D, 0.0D),
                            NuclearScienceFissionGameTests::placeObsidianTarget);
                    nearbyBody.set(physicsSystem.getPhysicsHandle(impulseTarget));
                    keepOverheatedAndSuspended(core, manualTick, reactorHandle, reactorSubLevel);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    Event queued = assertSingleMeltdownTrace(helper, localCorePosition);
                    Vec3 actualGlobalCenter = queued.globalPosition();
                    BlockPos actualGlobalOrigin = BlockPos.containing(actualGlobalCenter);
                    Vec3 expectedCenter = expectedTriggerCenter.get();
                    if (actualGlobalCenter.y < 80.0D
                            || expectedCenter == null
                            || actualGlobalCenter.distanceToSqr(expectedCenter) > 1.0E-12D) {
                        helper.fail("Fission meltdown did not freeze its sampled high-altitude origin: expected="
                                + expectedCenter
                                + ", actual="
                                + actualGlobalCenter);
                    }
                    if (!reactorSubLevel.getLevel().getBlockState(localCorePosition).isAir()) {
                        helper.fail("Committed fission reactor core remained in the Sable sublevel");
                    }
                    if (!helper.getLevel()
                            .getBlockState(actualGlobalOrigin)
                            .is(NuclearScienceBlocks.BLOCK_MELTEDREACTOR.get())) {
                        helper.fail("Native molten reactor core was not placed at the frozen parent-world origin");
                    }
                    if (!(helper.getLevel().getBlockEntity(actualGlobalOrigin) instanceof TileMeltedReactor melted)
                            || melted.radiation <= 0) {
                        helper.fail("Native molten reactor pollution did not continue in the parent world");
                    }
                    BlockPos waterPosition = preparedWater.get();
                    if (waterPosition == null
                            || !helper.getLevel().getBlockState(waterPosition).isAir()) {
                        helper.fail("Native fission meltdown did not clear nearby parent-world water");
                    }
                    if (!RadiationSystem.getRadiationSources(helper.getLevel()).contains(actualGlobalOrigin)) {
                        helper.fail("Native post-meltdown radiation is not owned by the projected molten core");
                    }
                    RigidBodyHandle handle = nearbyBody.get();
                    if (handle == null
                            || !handle.isValid()
                            || handle.getLinearVelocity(new Vector3d()).lengthSquared() <= 1.0E-12D) {
                        helper.fail("Native fission explosion did not impart force to a nearby Sable structure");
                    }
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    if (NuclearFissionProjectionAudit.count(Kind.NATIVE_EXPLOSION) != 1L
                            || NuclearFissionProjectionAudit.count(Kind.MELTDOWN_COMPLETE) != 1L) {
                        helper.fail("Projected fission meltdown retried after its native effects started");
                    }
                    NuclearFissionProjectionAudit.endCapture();
                })
                .thenSucceed();
    }

    private static void placeFissionCore(CommonLevelAccessor accessor) {
        accessor.setBlock(BlockPos.ZERO, fissionCoreBlock().defaultBlockState(), 3);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    accessor.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void placeObsidianTarget(CommonLevelAccessor accessor) {
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                accessor.setBlock(new BlockPos(0, y, z), Blocks.OBSIDIAN.defaultBlockState(), 3);
            }
        }
    }

    private static int countInternalStoneBlocks(ServerSubLevelContainer container) {
        int count = 0;
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            CommonLevelAccessor accessor = subLevel.getPlot().getEmbeddedLevelAccessor();
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 4; y++) {
                    for (int z = -4; z <= 4; z++) {
                        if (accessor.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static Block fissionCoreBlock() {
        return NuclearScienceBlocks.BLOCKS_NUCLEARMACHINE
                .getValue(SubtypeNuclearMachine.fissionreactorcore);
    }

    private static TileFissionReactorCore requireFissionCore(
            GameTestHelper helper, BlockPos localCorePosition) {
        if (helper.getLevel().getBlockEntity(localCorePosition) instanceof TileFissionReactorCore core) {
            return core;
        }
        helper.fail("Nuclear Science fission reactor core block entity was not created");
        throw new IllegalStateException("Missing fission reactor core");
    }

    private static void keepOverheated(TileFissionReactorCore core) {
        core.temperature.setValue(OVERHEAT_TEMPERATURE);
    }

    private static void installMinimumFuel(GameTestHelper helper, TileFissionReactorCore core) {
        ComponentInventory inventory = (ComponentInventory) core.getComponent(IComponentType.Inventory);
        inventory.setItem(0, new ItemStack(NuclearScienceItems.ITEM_FUELLEUO2.get()));
        if (core.fuelCount.getValue() <= 0) {
            helper.fail("Native Nuclear Science fuel inventory did not arm the fission reactor");
        }
    }

    private static void keepOverheatedAndSuspended(
            TileFissionReactorCore core,
            ComponentTickable manualTick,
            RigidBodyHandle handle,
            ServerSubLevel subLevel) {
        if (core.isRemoved()) {
            return;
        }
        keepOverheated(core);
        LetEmBurnGameTests.maintainVelocity(handle, subLevel, new Vector3d());
        ((NuclearFissionReactorAccess) core).letemburn$invokeTickServer(manualTick);
    }

    private static void advanceToPreMeltdownTick(
            GameTestHelper helper,
            TileFissionReactorCore core,
            ComponentTickable manualTick,
            RigidBodyHandle handle,
            ServerSubLevel subLevel) {
        NuclearFissionReactorAccess access = (NuclearFissionReactorAccess) core;
        int before = access.letemburn$getTicksOverheating();
        if (before < 200) {
            keepOverheatedAndSuspended(core, manualTick, handle, subLevel);
        }
        int after = access.letemburn$getTicksOverheating();
        if (after > 200 || core.isRemoved()) {
            helper.fail("Native fission fixture passed the 200-tick pre-meltdown boundary before setup: "
                    + after);
        }
        if (after < 200) {
            helper.fail("Waiting for the native fission reactor to reach 200 continuous overheat ticks");
        }
    }

    private static void assertProjectedEvent(
            GameTestHelper helper, Kind kind, BlockPos localPosition, Vec3 globalPosition) {
        boolean found = NuclearFissionProjectionAudit.events().stream()
                .filter(event -> event.kind() == kind)
                .anyMatch(event -> event.localPosition().equals(localPosition)
                        && event.globalPosition().distanceToSqr(globalPosition) <= 1.0E-12D);
        if (!found) {
            helper.fail("Missing projected Nuclear Science event " + kind);
        }
    }

    private static Event assertSingleMeltdownTrace(
            GameTestHelper helper, BlockPos localPosition) {
        Kind[] expected = {
                Kind.MELTDOWN_QUEUED,
                Kind.INITIAL_PARENT_CORE_WRITE_SKIPPED,
                Kind.NATIVE_EFFECT_STARTED,
                Kind.NATIVE_EXPLOSION,
                Kind.MELTDOWN_COMPLETE
        };
        for (Kind kind : expected) {
            if (NuclearFissionProjectionAudit.count(kind) != 1L) {
                helper.fail("Expected exactly one " + kind + " event during native fission meltdown");
            }
        }
        Event queued = NuclearFissionProjectionAudit.events().stream()
                .filter(event -> event.kind() == Kind.MELTDOWN_QUEUED)
                .findFirst()
                .orElseThrow();
        if (queued.overheatingTicks() != 201
                || !queued.localPosition().equals(localPosition)) {
            helper.fail("Fission meltdown did not preserve the native 201st-overheat trigger and frozen origin: "
                    + queued);
        }
        for (Event event : NuclearFissionProjectionAudit.events()) {
            if (event.kind() != Kind.RADIATION
                    && event.kind() != Kind.SOUND
                    && event.kind() != Kind.HEAT_QUERY
                    && event.globalPosition().distanceToSqr(queued.globalPosition()) > 1.0E-12D) {
                helper.fail("Fission meltdown effect moved away from its frozen origin: " + event);
            }
        }
        return queued;
    }

    private static long findSoundSeed() {
        for (long seed = 0L; seed < 100_000L; seed++) {
            if (RandomSource.create(seed).nextFloat() < 0.01F) {
                return seed;
            }
        }
        throw new IllegalStateException("Could not find deterministic Nuclear Science sound seed");
    }
}
