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

import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.gametest.draconic.DraconicProcessExplosionOracle;
import dev.marblegate.letemburn.gametest.draconic.DraconicProcessExplosionOracle.CaptureSnapshot;
import dev.marblegate.letemburn.gametest.draconic.DraconicProcessExplosionOracle.Event;
import dev.marblegate.letemburn.gametest.draconic.DraconicProcessExplosionOracle.Kind;
import dev.marblegate.letemburn.gametest.draconic.DraconicProcessExplosionOracle.Phase;
import dev.marblegate.letemburn.gametest.draconic.ProcessExplosionState;
import dev.marblegate.letemburn.gametest.draconic.ProcessExplosionStateAccess;
import dev.marblegate.letemburn.integration.draconic.DraconicAnnulusMode;
import dev.marblegate.letemburn.integration.draconic.ProcessExplosionAlgorithmAccess;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class DraconicExplosionEquivalenceGameTests {
    private static final long RANDOM_SEED = 0x4C4554454D425552L;
    private static final int SETTLE_TICKS = 45;
    private static final int FIXTURE_HORIZONTAL_RADIUS = 6;
    private static final int SNAPSHOT_HORIZONTAL_RADIUS = 7;
    private static final int SNAPSHOT_VERTICAL_RADIUS = 105;

    private DraconicExplosionEquivalenceGameTests() {}

    @GameTest(batch = "letemburn_de_oracle_r0", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 240)
    public static void realProcessExplosionRadiusZeroIsEquivalent(GameTestHelper helper) {
        verifyRealProcessExplosion(helper, 0);
    }

    @GameTest(batch = "letemburn_de_oracle_r1", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 240)
    public static void realProcessExplosionRadiusOneIsEquivalent(GameTestHelper helper) {
        verifyRealProcessExplosion(helper, 1);
    }

    @GameTest(batch = "letemburn_de_oracle_r3", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 240)
    public static void realProcessExplosionRadiusThreeIsEquivalent(GameTestHelper helper) {
        verifyRealProcessExplosion(helper, 3);
    }

    @GameTest(batch = "letemburn_de_oracle_r5", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 240)
    public static void realProcessExplosionRadiusFiveIsEquivalent(GameTestHelper helper) {
        verifyRealProcessExplosion(helper, 5);
    }

    private static void verifyRealProcessExplosion(GameTestHelper helper, int radius) {
        BlockPos origin = helper.absolutePos(new BlockPos(6, 48, 6));
        Map<RunMode, PreparedRun> prepared = new EnumMap<>(RunMode.class);
        Map<RunMode, RunResult> results = new EnumMap<>(RunMode.class);
        AtomicReference<ActiveRun> active = new AtomicReference<>();
        GameTestSequence sequence = helper.startSequence();

        for (RunMode mode : RunMode.values()) {
            sequence.thenExecute(() -> prepared.put(mode, calculate(helper.getLevel(), origin, radius, mode)));
        }
        for (RunMode mode : RunMode.values()) {
            appendDetonation(sequence, helper, origin, mode, prepared, active, results);
        }
        sequence.thenExecute(() -> compare(helper, radius, results)).thenSucceed();
    }

    private static void appendDetonation(
            GameTestSequence sequence,
            GameTestHelper helper,
            BlockPos origin,
            RunMode mode,
            Map<RunMode, PreparedRun> prepared,
            AtomicReference<ActiveRun> active,
            Map<RunMode, RunResult> results) {
        sequence.thenExecute(() -> active.set(detonate(helper.getLevel(), origin, requirePrepared(prepared, mode))))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    ActiveRun run = active.getAndSet(null);
                    if (run == null || run.mode() != mode) {
                        helper.fail("Missing active Draconic oracle run for " + mode);
                    }
                    results.put(mode, finish(helper.getLevel(), origin, run));
                });
    }

    private static PreparedRun calculate(
            ServerLevel level, BlockPos origin, int radius, RunMode mode) {
        prepareFixture(level, origin);
        WorldSnapshot calculationInitialWorld = snapshotWorld(level, origin);

        level.random.setSeed(RANDOM_SEED ^ radius);
        ProcessExplosion explosion = new ProcessExplosion(origin, radius, level, -1);
        ProcessExplosionAlgorithmAccess algorithm = (ProcessExplosionAlgorithmAccess) explosion;
        if (mode == RunMode.PRODUCTION) {
            DraconicAnnulusMode expected = radius < 256 ? DraconicAnnulusMode.A0 : DraconicAnnulusMode.A1;
            if (algorithm.letemburn$getAnnulusMode() != expected) {
                throw new IllegalStateException("Production ProcessExplosion selected the wrong annulus mode");
            }
        } else {
            algorithm.letemburn$setAnnulusMode(switch (mode) {
                case LEGACY -> DraconicAnnulusMode.LEGACY;
                case A0 -> DraconicAnnulusMode.A0;
                case A1 -> DraconicAnnulusMode.A1;
                case PRODUCTION -> throw new IllegalStateException("Production mode is selected implicitly");
            });
        }
        explosion.enableEffect = true;
        explosion.lava = true;
        DraconicProcessExplosionOracle.begin(explosion);

        int calculationSteps = 0;
        while (!explosion.isCalculationComplete()) {
            explosion.updateCalculation();
            calculationSteps++;
            if (calculationSteps > Math.max(1, radius + 1)) {
                throw new IllegalStateException("ProcessExplosion did not complete its safe calculation radius");
            }
        }
        ProcessExplosionState calculatedState = ((ProcessExplosionStateAccess) explosion).letemburn$captureState();
        long randomSuccessor = level.random.nextLong();
        return new PreparedRun(mode, explosion, calculationInitialWorld, calculatedState, randomSuccessor);
    }

    private static ActiveRun detonate(ServerLevel level, BlockPos origin, PreparedRun prepared) {
        prepareFixture(level, origin);
        WorldSnapshot detonationInitialWorld = snapshotWorld(level, origin);
        Pig witness = Objects.requireNonNull(EntityType.PIG.create(level));
        witness.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        witness.setNoAi(true);
        witness.setNoGravity(true);
        witness.setInvulnerable(true);
        if (!level.addFreshEntity(witness)) {
            throw new IllegalStateException("Failed to add the Draconic damage witness");
        }

        ProcessExplosion explosion = prepared.explosion();
        if (!explosion.detonate()) {
            throw new IllegalStateException("Safe ProcessExplosion fixture did not detonate");
        }
        ProcessExplosionState detonatedState = ((ProcessExplosionStateAccess) explosion).letemburn$captureState();
        return new ActiveRun(
                prepared.mode(),
                explosion,
                witness,
                prepared.calculationInitialWorld(),
                detonationInitialWorld,
                prepared.calculatedState(),
                detonatedState,
                prepared.randomSuccessor());
    }

    private static RunResult finish(ServerLevel level, BlockPos origin, ActiveRun run) {
        CaptureSnapshot capture = DraconicProcessExplosionOracle.finish(run.explosion());
        WorldSnapshot world = snapshotWorld(level, origin);
        EntitySnapshot entity = new EntitySnapshot(
                run.witness().isAlive(),
                Integer.toUnsignedLong(Float.floatToRawIntBits(run.witness().getHealth())),
                Double.doubleToRawLongBits(run.witness().getX()),
                Double.doubleToRawLongBits(run.witness().getY()),
                Double.doubleToRawLongBits(run.witness().getZ()));
        run.witness().discard();
        return new RunResult(
                run.calculationInitialWorld(),
                run.detonationInitialWorld(),
                run.calculatedState(),
                run.detonatedState(),
                run.randomSuccessor(),
                capture,
                world,
                entity);
    }

    private static void compare(
            GameTestHelper helper,
            int radius,
            Map<RunMode, RunResult> results) {
        RunResult legacy = requireResult(helper, results, RunMode.LEGACY);
        RunResult production = requireResult(helper, results, RunMode.PRODUCTION);
        requireEquivalent(helper, radius, legacy, production, "production radius-selected mode");
        requireEquivalent(
                helper,
                radius,
                legacy,
                requireResult(helper, results, RunMode.A0),
                "A0");
        requireEquivalent(
                helper,
                radius,
                legacy,
                requireResult(helper, results, RunMode.A1),
                "A1");

        long calculationReads = legacy.capture().events().stream()
                .filter(event -> event.phase() == Phase.CALCULATION)
                .filter(event -> event.kind() == Kind.BLOCK_STATE_READ || event.kind() == Kind.EMPTY_BLOCK_READ)
                .count();
        if (radius >= 3 && calculationReads == 0L) {
            helper.fail("Real ProcessExplosion radius " + radius + " did not exercise world reads");
        }
        if (radius >= 3 && legacy.capture().count(Phase.CALCULATION, Kind.SET_INSERTION) == 0L) {
            helper.fail("Real ProcessExplosion radius " + radius + " did not exercise set insertions");
        }
        if (legacy.capture().count(Phase.DETONATION, Kind.EXPLOSION_PACKET) != 2L) {
            helper.fail("Real ProcessExplosion radius " + radius + " did not emit both native packet calls");
        }
        if (radius >= 3 && legacy.capture().count(Phase.DETONATION, Kind.WORLD_WRITE) == 0L) {
            helper.fail("Real ProcessExplosion radius " + radius + " did not exercise world writes");
        }
        if (radius >= 3
                && legacy.capture().count(Phase.DETONATION, Kind.ENTITY_DAMAGE_QUERY) == 0L) {
            helper.fail("Real ProcessExplosion radius " + radius + " did not exercise its entity damage query");
        }
    }

    private static void requireEquivalent(
            GameTestHelper helper,
            int radius,
            RunResult expected,
            RunResult actual,
            String candidateName) {
        List<String> observedDifferences = differences(expected, actual);
        if (!observedDifferences.isEmpty()) {
            helper.fail("Real ProcessExplosion "
                    + candidateName
                    + " changed behavior at radius "
                    + radius
                    + ": "
                    + concise(observedDifferences));
        }
    }

    private static List<String> differences(RunResult expected, RunResult actual) {
        List<String> differences = new ArrayList<>();
        addDifference(
                differences,
                "calculation fixture",
                expected.calculationInitialWorld(),
                actual.calculationInitialWorld());
        addDifference(
                differences,
                "detonation fixture",
                expected.detonationInitialWorld(),
                actual.detonationInitialWorld());
        addDifference(differences, "calculated fields", expected.calculatedState(), actual.calculatedState());
        addDifference(differences, "detonated fields", expected.detonatedState(), actual.detonatedState());
        addDifference(differences, "RNG successor", expected.randomSuccessor(), actual.randomSuccessor());
        addSequenceDifference(
                differences,
                "calculation events",
                events(expected.capture(), Phase.CALCULATION),
                events(actual.capture(), Phase.CALCULATION));
        addSequenceDifference(
                differences,
                "detonation events",
                events(expected.capture(), Phase.DETONATION),
                events(actual.capture(), Phase.DETONATION));
        addSequenceDifference(differences, "final world", expected.world().states(), actual.world().states());
        addDifference(differences, "entity state", expected.entity(), actual.entity());
        return List.copyOf(differences);
    }

    private static void addDifference(
            List<String> differences, String observable, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            differences.add(observable
                    + " (expected hash="
                    + Objects.hashCode(expected)
                    + ", actual hash="
                    + Objects.hashCode(actual)
                    + ")");
        }
    }

    private static void addSequenceDifference(
            List<String> differences, String observable, List<?> expected, List<?> actual) {
        if (expected.equals(actual)) {
            return;
        }
        int sharedSize = Math.min(expected.size(), actual.size());
        int firstDifference = 0;
        while (firstDifference < sharedSize
                && Objects.equals(expected.get(firstDifference), actual.get(firstDifference))) {
            firstDifference++;
        }
        String detail = firstDifference < sharedSize
                ? ", first="
                        + firstDifference
                        + ", expected="
                        + summarize(expected.get(firstDifference))
                        + ", actual="
                        + summarize(actual.get(firstDifference))
                : ", common prefix=" + sharedSize;
        differences.add(observable
                + " (expected size="
                + expected.size()
                + ", actual size="
                + actual.size()
                + detail
                + ")");
    }

    private static String summarize(Object value) {
        String text = String.valueOf(value);
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }

    private static String concise(List<String> differences) {
        int displayed = Math.min(4, differences.size());
        String summary = String.join("; ", differences.subList(0, displayed));
        return differences.size() == displayed
                ? summary
                : summary + "; plus " + (differences.size() - displayed) + " more";
    }

    private static RunResult requireResult(
            GameTestHelper helper,
            Map<RunMode, RunResult> results,
            RunMode mode) {
        RunResult result = results.get(mode);
        if (result == null) {
            helper.fail("Missing Draconic oracle result for " + mode);
        }
        return result;
    }

    private static PreparedRun requirePrepared(
            Map<RunMode, PreparedRun> prepared,
            RunMode mode) {
        PreparedRun run = prepared.get(mode);
        if (run == null) {
            throw new IllegalStateException("Missing prepared Draconic oracle run for " + mode);
        }
        return run;
    }

    private static List<Event> events(CaptureSnapshot capture, Phase phase) {
        return capture.events().stream().filter(event -> event.phase() == phase).toList();
    }

    private static void prepareFixture(ServerLevel level, BlockPos origin) {
        int minimumY = minimumSnapshotY(level, origin);
        int maximumY = maximumSnapshotY(level, origin);
        AABB cleanup = new AABB(
                origin.getX() - SNAPSHOT_HORIZONTAL_RADIUS,
                minimumY,
                origin.getZ() - SNAPSHOT_HORIZONTAL_RADIUS,
                origin.getX() + SNAPSHOT_HORIZONTAL_RADIUS + 1,
                maximumY + 1,
                origin.getZ() + SNAPSHOT_HORIZONTAL_RADIUS + 1);
        BoundingBox scheduledTickCleanup = new BoundingBox(
                origin.getX() - SNAPSHOT_HORIZONTAL_RADIUS,
                minimumY,
                origin.getZ() - SNAPSHOT_HORIZONTAL_RADIUS,
                origin.getX() + SNAPSHOT_HORIZONTAL_RADIUS,
                maximumY,
                origin.getZ() + SNAPSHOT_HORIZONTAL_RADIUS);
        level.getBlockTicks().clearArea(scheduledTickCleanup);
        level.getFluidTicks().clearArea(scheduledTickCleanup);
        for (Pig pig : level.getEntitiesOfClass(Pig.class, cleanup)) {
            pig.discard();
        }
        for (int x = -SNAPSHOT_HORIZONTAL_RADIUS; x <= SNAPSHOT_HORIZONTAL_RADIUS; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = -SNAPSHOT_HORIZONTAL_RADIUS; z <= SNAPSHOT_HORIZONTAL_RADIUS; z++) {
                    BlockState state = x == -SNAPSHOT_HORIZONTAL_RADIUS
                            || x == SNAPSHOT_HORIZONTAL_RADIUS
                            || y == minimumY
                            || y == maximumY
                            || z == -SNAPSHOT_HORIZONTAL_RADIUS
                            || z == SNAPSHOT_HORIZONTAL_RADIUS
                                    ? Blocks.BEDROCK.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState();
                    level.setBlock(
                            new BlockPos(origin.getX() + x, y, origin.getZ() + z),
                            state,
                            2);
                }
            }
        }
        for (int x = -FIXTURE_HORIZONTAL_RADIUS; x <= FIXTURE_HORIZONTAL_RADIUS; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -FIXTURE_HORIZONTAL_RADIUS; z <= FIXTURE_HORIZONTAL_RADIUS; z++) {
                    if (x == 0 && z == 0 && y >= 0 && y <= 2) {
                        continue;
                    }
                    Block block = fixtureBlock(x, y, z);
                    level.setBlock(origin.offset(x, y, z), block.defaultBlockState(), 2);
                }
            }
        }
    }

    private static Block fixtureBlock(int x, int y, int z) {
        return switch (Math.floorMod((x * 31) + (y * 17) + (z * 13), 7)) {
            case 0 -> Blocks.OBSIDIAN;
            case 1 -> Blocks.COBBLESTONE;
            case 2 -> Blocks.DEEPSLATE;
            case 3 -> Blocks.GLASS;
            case 4 -> Blocks.DIRT;
            case 5 -> Blocks.BEDROCK;
            default -> Blocks.STONE;
        };
    }

    private static WorldSnapshot snapshotWorld(ServerLevel level, BlockPos origin) {
        List<String> states = new ArrayList<>();
        int minimumY = minimumSnapshotY(level, origin);
        int maximumY = maximumSnapshotY(level, origin);
        for (int x = -SNAPSHOT_HORIZONTAL_RADIUS; x <= SNAPSHOT_HORIZONTAL_RADIUS; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = -SNAPSHOT_HORIZONTAL_RADIUS; z <= SNAPSHOT_HORIZONTAL_RADIUS; z++) {
                    BlockState state = level.getBlockState(
                            new BlockPos(origin.getX() + x, y, origin.getZ() + z));
                    states.add(x + "," + (y - origin.getY()) + "," + z + "=" + state);
                }
            }
        }
        return new WorldSnapshot(List.copyOf(states));
    }

    private static int minimumSnapshotY(ServerLevel level, BlockPos origin) {
        return Math.max(level.getMinBuildHeight(), origin.getY() - SNAPSHOT_VERTICAL_RADIUS);
    }

    private static int maximumSnapshotY(ServerLevel level, BlockPos origin) {
        return Math.min(level.getMaxBuildHeight() - 1, origin.getY() + SNAPSHOT_VERTICAL_RADIUS);
    }

    private record PreparedRun(
            RunMode mode,
            ProcessExplosion explosion,
            WorldSnapshot calculationInitialWorld,
            ProcessExplosionState calculatedState,
            long randomSuccessor) {}

    private record ActiveRun(
            RunMode mode,
            ProcessExplosion explosion,
            Pig witness,
            WorldSnapshot calculationInitialWorld,
            WorldSnapshot detonationInitialWorld,
            ProcessExplosionState calculatedState,
            ProcessExplosionState detonatedState,
            long randomSuccessor) {}

    private record RunResult(
            WorldSnapshot calculationInitialWorld,
            WorldSnapshot detonationInitialWorld,
            ProcessExplosionState calculatedState,
            ProcessExplosionState detonatedState,
            long randomSuccessor,
            CaptureSnapshot capture,
            WorldSnapshot world,
            EntitySnapshot entity) {}

    private record WorldSnapshot(List<String> states) {}

    private record EntitySnapshot(
            boolean alive,
            long healthBits,
            long xBits,
            long yBits,
            long zBits) {}

    private enum RunMode {
        PRODUCTION,
        LEGACY,
        A0,
        A1
    }
}
