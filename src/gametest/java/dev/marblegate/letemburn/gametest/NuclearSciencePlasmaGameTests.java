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
import dev.marblegate.letemburn.gametest.audit.NuclearPlasmaProjectionAudit;
import dev.marblegate.letemburn.gametest.audit.NuclearPlasmaProjectionAudit.Event;
import dev.marblegate.letemburn.gametest.audit.NuclearPlasmaProjectionAudit.Kind;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import nuclearscience.common.block.subtype.SubtypeElectromagent;
import nuclearscience.common.block.subtype.SubtypeNuclearMachine;
import nuclearscience.common.tile.TileSteamFunnel;
import nuclearscience.common.tile.reactor.fusion.TilePlasma;
import nuclearscience.registers.NuclearScienceBlocks;
import org.joml.Quaterniond;
import org.joml.Vector3d;

@PrefixGameTestTemplate(false)
public final class NuclearSciencePlasmaGameTests {
    private NuclearSciencePlasmaGameTests() {}

    @GameTest(batch = "letemburn_ns_plasma_sealed", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 60)
    public static void sealedContainmentDoesNotProject(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        NuclearPlasmaProjectionAudit.beginCapture();
        ServerSubLevel subLevel = spawnChamber(
                helper,
                container,
                new Vector3d(5.5D, 80.5D, 5.5D),
                1,
                false);
        BlockPos rootPosition = subLevel.getPlot().getCenterBlock();
        UUID subLevelId = subLevel.getUniqueId();

        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    if (NuclearPlasmaProjectionAudit.count(subLevelId, Kind.CANDIDATE_REGISTERED) != 0L
                            || NuclearPlasmaProjectionAudit.count(subLevelId, Kind.PARENT_SEED_CREATED) != 0L) {
                        helper.fail("A sealed fusion-containment chamber projected plasma outside the sublevel");
                    }
                    if (!subLevel.getLevel().getBlockState(rootPosition).is(NuclearScienceBlocks.BLOCK_PLASMA.get())) {
                        helper.fail("The sealed native plasma seed disappeared before its 80-tick lifetime");
                    }
                    NuclearPlasmaProjectionAudit.endCapture();
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_ns_plasma_protected", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void protectedParentTargetsAreNeverOverwritten(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        NuclearPlasmaProjectionAudit.beginCapture();
        List<ProtectedTarget> targets = new ArrayList<>();
        targets.add(spawnProtectedTarget(
                helper,
                container,
                new Vector3d(5.5D, 88.5D, 5.5D),
                Blocks.BEDROCK));
        targets.add(spawnProtectedTarget(
                helper,
                container,
                new Vector3d(17.5D, 88.5D, 5.5D),
                electromagneticGlass()));
        targets.add(spawnProtectedTarget(
                helper,
                container,
                new Vector3d(29.5D, 88.5D, 5.5D),
                fusionCore()));

        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    for (ProtectedTarget target : targets) {
                        if (!helper.getLevel().getBlockState(target.position()).is(target.block())) {
                            helper.fail("Projected plasma overwrote protected parent-world block "
                                    + target.block()
                                    + " at "
                                    + target.position());
                        }
                    }
                    long queued = targets.stream()
                            .mapToLong(target -> NuclearPlasmaProjectionAudit.count(
                                    target.subLevelId(), Kind.ESCAPE_QUEUED))
                            .sum();
                    long protectedCount = targets.stream()
                            .mapToLong(target -> NuclearPlasmaProjectionAudit.count(
                                    target.subLevelId(), Kind.PARENT_TARGET_PROTECTED))
                            .sum();
                    long created = targets.stream()
                            .mapToLong(target -> NuclearPlasmaProjectionAudit.count(
                                    target.subLevelId(), Kind.PARENT_SEED_CREATED))
                            .sum();
                    if (queued != targets.size() || protectedCount != targets.size() || created != 0L) {
                        helper.fail("Protected plasma targets were not handled exactly once by the post-physics queue");
                    }
                    NuclearPlasmaProjectionAudit.endCapture();
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_ns_plasma_motion", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 100)
    public static void escapeUsesTheCurrentMovedAndRotatedPoseOnce(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        NuclearPlasmaProjectionAudit.beginCapture();
        ServerSubLevel subLevel = spawnChamber(
                helper,
                container,
                new Vector3d(5.5D, 100.5D, 5.5D),
                1,
                true);
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        BlockPos rootPosition = subLevel.getPlot().getCenterBlock();
        BlockPos exitPosition = rootPosition.east(2);
        UUID subLevelId = subLevel.getUniqueId();
        BlockPos originalProjectedExit = BlockPos.containing(
                Sable.HELPER.projectOutOfSubLevel(helper.getLevel(), Vec3.atCenterOf(exitPosition)));
        AtomicReference<BlockPos> expectedAfterTeleport = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> handle.teleport(
                        absolutePosition(helper, new Vector3d(17.5D, 104.5D, 13.5D)),
                        new Quaterniond().rotateY(Math.PI / 2.0D)))
                .thenIdle(1)
                .thenExecute(() -> expectedAfterTeleport.set(BlockPos.containing(
                        Sable.HELPER.projectOutOfSubLevel(helper.getLevel(), Vec3.atCenterOf(exitPosition)))))
                .thenWaitUntil(() -> requireCount(helper, subLevelId, Kind.CANDIDATE_REGISTERED, 1L))
                .thenWaitUntil(() -> requireCount(helper, subLevelId, Kind.PARENT_SEED_CREATED, 1L))
                .thenIdle(2)
                .thenExecute(() -> {
                    Event created = singleEvent(helper, subLevelId, Kind.PARENT_SEED_CREATED);
                    BlockPos actualSeedPosition = BlockPos.containing(created.globalPosition());
                    if (actualSeedPosition.equals(originalProjectedExit)) {
                        helper.fail("Escaped plasma used the structure's original pose after it moved");
                    }
                    BlockPos expected = expectedAfterTeleport.get();
                    if (expected == null || !actualSeedPosition.equals(expected)) {
                        helper.fail("Escaped plasma did not use the current moved/rotated pose: expected="
                                + expected
                                + ", actual="
                                + actualSeedPosition);
                    }
                    if (!created.exitPosition().equals(exitPosition) || created.remainingSpread() != 4) {
                        helper.fail("Escaped plasma did not preserve its local exit or remaining native spread: "
                                + created);
                    }
                    if (!(helper.getLevel().getBlockEntity(actualSeedPosition) instanceof TilePlasma parentSeed)
                            || parentSeed.spread.getValue() != 4) {
                        helper.fail("Moved plasma escape did not create the correctly configured native parent seed");
                    }
                    boolean nativeChildPresent = false;
                    for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                        if (helper.getLevel()
                                .getBlockState(actualSeedPosition.relative(direction))
                                .is(NuclearScienceBlocks.BLOCK_PLASMA.get())) {
                            nativeChildPresent = true;
                            break;
                        }
                    }
                    if (!nativeChildPresent) {
                        helper.fail("Projected parent seed did not continue Nuclear Science's native spreading");
                    }
                    if (NuclearPlasmaProjectionAudit.count(subLevelId, Kind.ESCAPE_QUEUED) != 1L
                            || NuclearPlasmaProjectionAudit.count(subLevelId, Kind.PARENT_SEED_CREATED) != 1L) {
                        helper.fail("A single moving plasma escape was projected more than once");
                    }
                    NuclearPlasmaProjectionAudit.endCapture();
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_ns_plasma_first_viable", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void protectedFirstEscapeFallsThroughToNextNativeExit(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        NuclearPlasmaProjectionAudit.beginCapture();
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(5.5D, 136.5D, 5.5D)),
                NuclearSciencePlasmaGameTests::placeTwoExitChamber);
        BlockPos rootPosition = subLevel.getPlot().getCenterBlock();
        BlockPos firstExit = rootPosition.east(2);
        BlockPos secondExit = rootPosition.west(3);
        UUID subLevelId = subLevel.getUniqueId();
        BlockPos protectedParentTarget = BlockPos.containing(
                Sable.HELPER.projectOutOfSubLevel(helper.getLevel(), Vec3.atCenterOf(firstExit)));
        BlockPos viableParentTarget = BlockPos.containing(
                Sable.HELPER.projectOutOfSubLevel(helper.getLevel(), Vec3.atCenterOf(secondExit)));
        helper.getLevel().setBlock(protectedParentTarget, Blocks.BEDROCK.defaultBlockState(), 3);

        helper.startSequence()
                .thenWaitUntil(() -> requireCount(helper, subLevelId, Kind.PARENT_SEED_CREATED, 1L))
                .thenIdle(3)
                .thenExecute(() -> {
                    List<Event> candidates = NuclearPlasmaProjectionAudit.events().stream()
                            .filter(event -> event.subLevelId().equals(subLevelId)
                                    && event.kind() == Kind.CANDIDATE_REGISTERED)
                            .toList();
                    if (candidates.size() != 2
                            || !candidates.get(0).exitPosition().equals(firstExit)
                            || !candidates.get(1).exitPosition().equals(secondExit)) {
                        helper.fail("Plasma exits were not attempted in native write order: " + candidates);
                    }
                    if (!helper.getLevel().getBlockState(protectedParentTarget).is(Blocks.BEDROCK)) {
                        helper.fail("The protected first projected plasma target was overwritten");
                    }
                    Event created = singleEvent(helper, subLevelId, Kind.PARENT_SEED_CREATED);
                    if (!created.exitPosition().equals(secondExit)
                            || !BlockPos.containing(created.globalPosition()).equals(viableParentTarget)
                            || created.remainingSpread() != 3) {
                        helper.fail("Plasma did not use the first viable native exit: " + created);
                    }
                    if (NuclearPlasmaProjectionAudit.count(subLevelId, Kind.ESCAPE_QUEUED) != 2L
                            || NuclearPlasmaProjectionAudit.count(
                                    subLevelId, Kind.PARENT_TARGET_PROTECTED) != 1L) {
                        helper.fail("Protected and viable plasma exits were not each processed once");
                    }
                    NuclearPlasmaProjectionAudit.endCapture();
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_ns_plasma_lifecycle", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 140)
    public static void parentSeedKeepsNativeSpreadProtectionAndLifetime(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        NuclearPlasmaProjectionAudit.beginCapture();
        ServerSubLevel subLevel = spawnChamber(
                helper,
                container,
                new Vector3d(5.5D, 112.5D, 5.5D),
                1,
                true);
        BlockPos rootPosition = subLevel.getPlot().getCenterBlock();
        UUID subLevelId = subLevel.getUniqueId();
        BlockPos localExit = rootPosition.east(2);
        BlockPos expectedSeed = BlockPos.containing(
                Sable.HELPER.projectOutOfSubLevel(helper.getLevel(), Vec3.atCenterOf(localExit)));
        helper.getLevel().setBlock(expectedSeed.east(), Blocks.BEDROCK.defaultBlockState(), 3);
        helper.getLevel().setBlock(expectedSeed.west(), electromagneticGlass().defaultBlockState(), 3);
        helper.getLevel().setBlock(expectedSeed.above(), fusionCore().defaultBlockState(), 3);
        helper.getLevel().setBlock(expectedSeed.south(), Blocks.STONE.defaultBlockState(), 3);

        helper.startSequence()
                .thenWaitUntil(() -> requireCount(helper, subLevelId, Kind.PARENT_SEED_CREATED, 1L))
                .thenExecute(() -> {
                    Event created = singleEvent(helper, subLevelId, Kind.PARENT_SEED_CREATED);
                    BlockPos actual = BlockPos.containing(created.globalPosition());
                    if (!actual.equals(expectedSeed) || created.remainingSpread() != 4) {
                        helper.fail("Static plasma escape used an unexpected position or spread: " + created);
                    }
                    if (!(helper.getLevel().getBlockEntity(actual) instanceof TilePlasma plasma)
                            || plasma.ticksExisted.getValue() > 1
                            || plasma.spread.getValue() != 4) {
                        helper.fail("Projected parent seed did not start a fresh native 80-tick lifecycle");
                    }
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    if (!helper.getLevel().getBlockState(expectedSeed.east()).is(Blocks.BEDROCK)
                            || !helper.getLevel().getBlockState(expectedSeed.west()).is(electromagneticGlass())
                            || !helper.getLevel().getBlockState(expectedSeed.above()).is(fusionCore())) {
                        helper.fail("Native parent plasma overwrote bedrock, fusion containment, or the fusion core");
                    }
                    if (!helper.getLevel()
                            .getBlockState(expectedSeed.south())
                            .is(NuclearScienceBlocks.BLOCK_PLASMA.get())) {
                        helper.fail("Projected parent plasma did not use native block destruction and spreading");
                    }
                })
                .thenIdle(82)
                .thenExecute(() -> {
                    if (helper.getLevel().getBlockState(expectedSeed).is(NuclearScienceBlocks.BLOCK_PLASMA.get())) {
                        helper.fail("Projected parent plasma outlived Nuclear Science's native 80-tick lifecycle");
                    }
                    if (NuclearPlasmaProjectionAudit.count(subLevelId, Kind.PARENT_SEED_CREATED) != 1L) {
                        helper.fail("Native plasma lifecycle created a duplicate projected seed");
                    }
                    NuclearPlasmaProjectionAudit.endCapture();
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_ns_plasma_steam", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void parentSeedKeepsNativeSteamProduction(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        NuclearPlasmaProjectionAudit.beginCapture();
        ServerSubLevel subLevel = spawnChamber(
                helper,
                container,
                new Vector3d(5.5D, 124.5D, 5.5D),
                1,
                true);
        UUID subLevelId = subLevel.getUniqueId();
        AtomicReference<BlockPos> actualSeed = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(() -> requireCount(helper, subLevelId, Kind.PARENT_SEED_CREATED, 1L))
                .thenExecute(() -> {
                    BlockPos seedPosition = BlockPos.containing(
                            singleEvent(helper, subLevelId, Kind.PARENT_SEED_CREATED).globalPosition());
                    actualSeed.set(seedPosition);
                    helper.getLevel().setBlock(
                            seedPosition.above(), electromagneticGlass().defaultBlockState(), 3);
                    helper.getLevel().setBlock(seedPosition.above(2), Blocks.WATER.defaultBlockState(), 3);
                    boolean funnelPlaced = helper.getLevel().setBlock(
                            seedPosition.above(3), steamFunnel().defaultBlockState(), 3);
                    if (!funnelPlaced
                            || !(helper.getLevel().getBlockEntity(seedPosition.above(3)) instanceof TileSteamFunnel)) {
                        helper.fail("Native steam funnel fixture could not be placed above projected plasma: state="
                                + helper.getLevel().getBlockState(seedPosition.above(3))
                                + ", blockEntity="
                                + helper.getLevel().getBlockEntity(seedPosition.above(3)));
                    }
                })
                .thenWaitUntil(() -> {
                    BlockPos seedPosition = actualSeed.get();
                    if (seedPosition == null
                            || NuclearPlasmaProjectionAudit.nativeSteamDeliveries().stream()
                                    .noneMatch(delivery -> delivery.plasmaPosition().equals(seedPosition))) {
                        throw new GameTestAssertException("Projected parent plasma has not delivered native steam yet");
                    }
                })
                .thenExecute(() -> {
                    BlockPos seedPosition = actualSeed.get();
                    NuclearPlasmaProjectionAudit.NativeSteamDelivery delivery = NuclearPlasmaProjectionAudit
                            .nativeSteamDeliveries()
                            .stream()
                            .filter(candidate -> candidate.plasmaPosition().equals(seedPosition))
                            .findFirst()
                            .orElseThrow();
                    if (delivery.requestedAmount() != Integer.MAX_VALUE || delivery.acceptedAmount() <= 0) {
                        helper.fail("Projected parent plasma did not preserve Nuclear Science's native steam delivery");
                    }
                    if (!helper.getLevel().getFluidState(seedPosition.above(2)).is(net.minecraft.tags.FluidTags.WATER)) {
                        helper.fail("Native projected plasma unexpectedly replaced its steam-source water");
                    }
                    NuclearPlasmaProjectionAudit.endCapture();
                })
                .thenSucceed();
    }

    private static ServerSubLevel spawnChamber(
            GameTestHelper helper,
            ServerSubLevelContainer container,
            Vector3d localPosition,
            int radius,
            boolean open) {
        return spawnSubLevel(
                container,
                absolutePosition(helper, localPosition),
                accessor -> placeChamber(accessor, radius, open));
    }

    private static void placeChamber(CommonLevelAccessor accessor, int radius, boolean open) {
        Block containment = electromagneticGlass();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean boundary = Math.abs(x) == radius
                            || Math.abs(y) == radius
                            || Math.abs(z) == radius;
                    boolean opening = open && x == radius && y == 0 && z == 0;
                    if (boundary && !opening) {
                        accessor.setBlock(new BlockPos(x, y, z), containment.defaultBlockState(), 3);
                    }
                }
            }
        }
        accessor.setBlock(BlockPos.ZERO, NuclearScienceBlocks.BLOCK_PLASMA.get().defaultBlockState(), 3);
    }

    private static void placeTwoExitChamber(CommonLevelAccessor accessor) {
        Block containment = electromagneticGlass();
        for (int x = -2; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    boolean boundary = x == -2
                            || x == 1
                            || Math.abs(y) == 1
                            || Math.abs(z) == 1;
                    boolean opening = y == 0 && z == 0 && (x == -2 || x == 1);
                    if (boundary && !opening) {
                        accessor.setBlock(new BlockPos(x, y, z), containment.defaultBlockState(), 3);
                    }
                }
            }
        }
        accessor.setBlock(BlockPos.ZERO, NuclearScienceBlocks.BLOCK_PLASMA.get().defaultBlockState(), 3);
    }

    private static ProtectedTarget spawnProtectedTarget(
            GameTestHelper helper,
            ServerSubLevelContainer container,
            Vector3d localPosition,
            Block protectedBlock) {
        ServerSubLevel subLevel = spawnChamber(helper, container, localPosition, 1, true);
        BlockPos rootPosition = subLevel.getPlot().getCenterBlock();
        BlockPos localExit = rootPosition.east(2);
        BlockPos globalExit = BlockPos.containing(
                Sable.HELPER.projectOutOfSubLevel(helper.getLevel(), Vec3.atCenterOf(localExit)));
        helper.getLevel().setBlock(globalExit, protectedBlock.defaultBlockState(), 3);
        return new ProtectedTarget(subLevel.getUniqueId(), globalExit, protectedBlock);
    }

    private static Block electromagneticGlass() {
        return NuclearScienceBlocks.BLOCKS_ELECTROMAGENT.getValue(
                SubtypeElectromagent.electromagneticglass);
    }

    private static Block fusionCore() {
        return NuclearScienceBlocks.BLOCKS_NUCLEARMACHINE.getValue(
                SubtypeNuclearMachine.fusionreactorcore);
    }

    private static Block steamFunnel() {
        return NuclearScienceBlocks.BLOCKS_NUCLEARMACHINE.getValue(
                SubtypeNuclearMachine.steamfunnel);
    }

    private static void requireCount(
            GameTestHelper helper, UUID subLevelId, Kind kind, long expected) {
        long actual = NuclearPlasmaProjectionAudit.count(subLevelId, kind);
        if (actual != expected) {
            helper.fail("Waiting for " + expected + " " + kind + " event(s); found " + actual);
        }
    }

    private static Event singleEvent(GameTestHelper helper, UUID subLevelId, Kind kind) {
        List<Event> events = NuclearPlasmaProjectionAudit.events().stream()
                .filter(event -> event.subLevelId().equals(subLevelId) && event.kind() == kind)
                .toList();
        if (events.size() != 1) {
            helper.fail("Expected one " + kind + " event, found " + events.size());
        }
        return events.getFirst();
    }

    private record ProtectedTarget(UUID subLevelId, BlockPos position, Block block) {}
}
