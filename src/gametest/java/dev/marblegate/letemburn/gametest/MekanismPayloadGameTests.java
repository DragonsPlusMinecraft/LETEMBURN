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
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.spawnSubLevel;

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.effect.ChainReactionCoordinator;
import dev.marblegate.letemburn.common.payload.PayloadEnvelopeResolver;
import dev.marblegate.letemburn.gametest.audit.VanillaTntImpactAudit;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.concurrent.atomic.AtomicBoolean;
import mekanism.common.attachments.BlockData;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tile.TileEntityCardboardBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@PrefixGameTestTemplate(false)
public final class MekanismPayloadGameTests {
    private MekanismPayloadGameTests() {}

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 20)
    public static void recursiveCardboardEnvelopeHonorsDepthLimit(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BlockData tnt = new BlockData(Blocks.TNT.defaultBlockState(), null);
        CompoundTag eightTag = cardboardTag(registries, nestedContent(registries, 8, tnt));
        CompoundTag nineTag = cardboardTag(registries, nestedContent(registries, 9, tnt));

        PayloadEnvelopeResolver.Resolution eight = PayloadEnvelopeResolver.INSTANCE.resolve(
                MekanismBlocks.CARDBOARD_BOX.defaultState(), eightTag, registries, 8, 1_048_576);
        PayloadEnvelopeResolver.Resolution nine = PayloadEnvelopeResolver.INSTANCE.resolve(
                MekanismBlocks.CARDBOARD_BOX.defaultState(), nineTag, registries, 8, 1_048_576);

        if (!eight.valid()
                || eight.snapshot().envelopeDepth() != 8
                || !eight.snapshot().payloadState().is(Blocks.TNT)) {
            helper.fail("Eight real Mekanism cardboard layers did not resolve to TNT");
        }
        if (nine.valid() || nine.failure() != PayloadEnvelopeResolver.Failure.TOO_DEEP) {
            helper.fail("Nine real Mekanism cardboard layers did not fail closed");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 140)
    public static void nestedCardboardTntUsesDeferredNativeEntity(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        VanillaTntImpactAudit.clearWithin(helper.getBounds());
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BlockData content = nestedContent(
                registries, 2, new BlockData(Blocks.TNT.defaultBlockState(), null));
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D)),
                accessor -> placeCardboardBox(accessor, content));
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        AtomicBoolean observedNativeTnt = new AtomicBoolean();
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

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
                            || spawnEvents.getFirst().envelopeDepth() != 2
                            || !observedNativeTnt.get()) {
                        Vector3d local = localPosition(helper, subLevel.logicalPose().position());
                        helper.fail(("Nested cardboard TNT did not create exactly one native PrimedTnt "
                                + "with an initial 4 tick fuse; events=%s, "
                                + "observed=%s, body=%s, payload=%s, lastSpawn=%s, pending=%d")
                                        .formatted(
                                                spawnEvents,
                                                observedNativeTnt.get(),
                                                local,
                                                subLevel.getLevel()
                                                        .getBlockState(subLevel.getPlot().getCenterBlock()),
                                                VanillaTntImpactAudit.lastSpawnPosition(),
                                                ChainReactionCoordinator.INSTANCE.pendingCount(helper.getLevel())));
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 100)
    public static void cardboardTntSurvivesBelowThresholdCollision(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        VanillaTntImpactAudit.clearWithin(helper.getBounds());
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BlockData content = nestedContent(
                registries, 2, new BlockData(Blocks.TNT.defaultBlockState(), null));
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 2.25D)),
                accessor -> placeCardboardBox(accessor, content));
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        BlockPos payloadPosition = subLevel.getPlot().getCenterBlock();

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> LetEmBurnGameTests.maintainVelocity(
                        handle,
                        subLevel,
                        absoluteDirection(helper, new Vector3d(0.0D, 0.0D, 3.0D))))
                .thenIdle(12)
                .thenExecute(() -> {
                    var belowThreshold = VanillaTntImpactAudit.belowThresholdEventsWithin(helper.getBounds());
                    if (belowThreshold.isEmpty()
                            || belowThreshold.stream().anyMatch(event -> event.envelopeDepth() != 2)
                            || VanillaTntImpactAudit.spawnsWithin(helper.getBounds()) != 0) {
                        helper.fail("Cardboard TNT did not record only below-threshold collisions: "
                                + belowThreshold);
                    }
                    if (!subLevel
                            .getLevel()
                            .getBlockState(payloadPosition)
                            .is(MekanismBlocks.CARDBOARD_BOX.get())) {
                        helper.fail("Below-threshold cardboard TNT collision consumed the outer payload");
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 120)
    public static void damagedCardboardPayloadRemainsIntact(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        ServerSubLevel subLevel = spawnSubLevel(
                container,
                absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D)),
                accessor -> accessor.setBlock(
                        BlockPos.ZERO, MekanismBlocks.CARDBOARD_BOX.defaultState(), 3));
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    BlockState remaining = subLevel.getLevel()
                            .getBlockState(subLevel.getPlot().getCenterBlock());
                    if (!remaining.is(MekanismBlocks.CARDBOARD_BOX.get())) {
                        helper.fail("Damaged cardboard payload was removed instead of failing closed");
                    }
                })
                .thenSucceed();
    }

    static void placeCardboardBox(CommonLevelAccessor accessor, BlockData content) {
        accessor.setBlock(BlockPos.ZERO, MekanismBlocks.CARDBOARD_BOX.defaultState(), 3);
        if (!(accessor.getBlockEntity(BlockPos.ZERO) instanceof TileEntityCardboardBox box)) {
            throw new IllegalStateException("Mekanism cardboard box block entity was not created");
        }
        box.setComponents(DataComponentMap.builder()
                .set(MekanismDataComponents.BLOCK_DATA.value(), content)
                .build());
        box.setChanged();
    }

    static BlockData nestedContent(
            HolderLookup.Provider registries, int totalEnvelopeLayers, BlockData leaf) {
        if (totalEnvelopeLayers < 1) {
            throw new IllegalArgumentException("At least one envelope layer is required");
        }
        BlockData content = leaf;
        for (int layer = 1; layer < totalEnvelopeLayers; layer++) {
            content = new BlockData(
                    MekanismBlocks.CARDBOARD_BOX.defaultState(), cardboardTag(registries, content));
        }
        return content;
    }

    static CompoundTag cardboardTag(HolderLookup.Provider registries, BlockData content) {
        TileEntityCardboardBox box = new TileEntityCardboardBox(
                BlockPos.ZERO, MekanismBlocks.CARDBOARD_BOX.defaultState());
        box.setComponents(DataComponentMap.builder()
                .set(MekanismDataComponents.BLOCK_DATA.value(), content)
                .build());
        return box.saveWithFullMetadata(registries);
    }
}
