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

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.impact.ImpactPayloadRegistry;
import dev.marblegate.letemburn.common.impact.ImpactStatus;
import dev.marblegate.letemburn.common.impact.ProjectedEffectContext;
import dev.marblegate.letemburn.common.impact.ProjectedPayloadCollisionCallback;
import dev.marblegate.letemburn.common.payload.PayloadEnvelopeResolver;
import dev.marblegate.letemburn.gametest.audit.MoreTntImpactAudit;
import dev.marblegate.letemburn.gametest.audit.MoreTntImpactAudit.SpawnEvent;
import dev.marblegate.letemburn.integration.moretnt.MoreTntNativeFactory;
import dev.marblegate.letemburn.integration.moretnt.MoreTntNativeFactory.NativeTntSpec;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.discusser.moretnt.MoreTNT;
import io.github.discusser.moretnt.explosions.BaseExplosion;
import io.github.discusser.moretnt.explosions.CatExplosion;
import io.github.discusser.moretnt.explosions.FireExplosion;
import io.github.discusser.moretnt.explosions.KnockbackExplosion;
import io.github.discusser.moretnt.explosions.LavaExplosion;
import io.github.discusser.moretnt.explosions.NegativeExplosion;
import io.github.discusser.moretnt.explosions.ShuffleExplosion;
import io.github.discusser.moretnt.explosions.SnowExplosion;
import io.github.discusser.moretnt.explosions.SphereExplosion;
import io.github.discusser.moretnt.explosions.WaterExplosion;
import io.github.discusser.moretnt.objects.MoreTNTObject;
import io.github.discusser.moretnt.objects.blocks.BaseTNTBlock;
import io.github.discusser.moretnt.objects.entities.BasePrimedTNT;
import io.github.discusser.moretnt.objects.registration.MoreTNTBlocks;
import io.github.discusser.moretnt.objects.registration.MoreTNTObjects;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@PrefixGameTestTemplate(false)
public final class MoreTntGameTests {
    private static final Map<String, Class<? extends BaseExplosion>> EXPLOSION_TYPES = Map.ofEntries(
            Map.entry("negative_tnt", NegativeExplosion.class),
            Map.entry("negative_tnt_4x", NegativeExplosion.class),
            Map.entry("shuffle_tnt", ShuffleExplosion.class),
            Map.entry("shuffle_tnt_4x", ShuffleExplosion.class),
            Map.entry("snow_tnt", SnowExplosion.class),
            Map.entry("snow_tnt_4x", SnowExplosion.class),
            Map.entry("cat_tnt", CatExplosion.class),
            Map.entry("dome_tnt", SphereExplosion.class),
            Map.entry("fire_tnt", FireExplosion.class),
            Map.entry("knockback_tnt", KnockbackExplosion.class),
            Map.entry("water_tnt", WaterExplosion.class),
            Map.entry("lava_tnt", LavaExplosion.class),
            Map.entry("cobblestone_tnt", SphereExplosion.class),
            Map.entry("stone_tnt", SphereExplosion.class),
            Map.entry("obsidian_tnt", SphereExplosion.class));

    private MoreTntGameTests() {}

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 40)
    public static void factoryMappingsCoverEveryNativeVariant(GameTestHelper helper) {
        if (MoreTNTObjects.objects.size() != 15 || EXPLOSION_TYPES.size() != 15) {
            helper.fail("More Fun TNTs 1.1.3 variant count changed");
        }
        int fourXVariants = 0;
        Vec3 factoryPosition = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 8, 2)));
        for (MoreTNTObject object : MoreTNTObjects.objects) {
            BaseTNTBlock block = (BaseTNTBlock) object.blockItem().block().get();
            BlockState state = block.defaultBlockState().setValue(BaseTNTBlock.FACING, Direction.WEST);
            if (BlockWithSubLevelCollisionCallback.sable$getCallback(state) != ProjectedPayloadCollisionCallback.INSTANCE) {
                helper.fail("More Fun TNTs block still uses Sable's vanilla TNT callback: " + state);
            }
            NativeTntSpec spec = MoreTntNativeFactory.inspect(state);
            if (spec == null
                    || MoreTNT.blockToPrimedTNTMap.get(block) != object.primedTNTObject()
                    || MoreTNT.entityTypeToBlockMap.get(spec.entityType()) != block) {
                helper.fail("More Fun TNTs native block/entity mapping changed: " + state);
            }

            BasePrimedTNT primedTnt = MoreTntNativeFactory.create(
                    helper.getLevel(), factoryPosition, spec, Direction.SOUTH);
            BaseExplosion explosion = primedTnt.createExplosion(
                    factoryPosition.x, factoryPosition.y, factoryPosition.z);
            String path = spec.blockId().getPath();
            Class<? extends BaseExplosion> expectedExplosion = EXPLOSION_TYPES.get(path);
            if (expectedExplosion == null
                    || explosion == null
                    || explosion.getClass() != expectedExplosion
                    || primedTnt.getType() != spec.entityType()
                    || primedTnt.block != block
                    || primedTnt.facing != Direction.SOUTH
                    || Float.compare(primedTnt.size, block.size) != 0
                    || primedTnt.fire != block.fire
                    || primedTnt.getFuse() != 1) {
                helper.fail("More Fun TNTs native factory did not preserve variant data: " + path);
            }
            if (path.endsWith("_4x")) {
                fourXVariants++;
                if (Float.compare(spec.size(), 16.0F) != 0) {
                    helper.fail("More Fun TNTs 4x variant lost its native radius: " + path);
                }
            }
            primedTnt.discard();
        }
        if (fourXVariants != 3) {
            helper.fail("More Fun TNTs 4x variant mapping changed");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 120)
    public static void directWaterImpactUsesNativeNextTickExplosion(GameTestHelper helper) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        SubLevelPhysicsSystem physicsSystem = LetEmBurnGameTests.requirePhysics(container);
        LetEmBurnGameTests.addWall(helper, 3);
        Vector3d initialPosition = absolutePosition(helper, new Vector3d(2.5D, 4.0D, 1.5D));
        BlockState waterTnt = MoreTNTBlocks.WATER_TNT
                .block()
                .get()
                .defaultBlockState()
                .setValue(BaseTNTBlock.FACING, Direction.EAST);
        ServerSubLevel subLevel = spawnSingleBlockSubLevel(
                container,
                initialPosition,
                waterTnt);
        UUID subLevelId = subLevel.getUniqueId();
        MoreTntImpactAudit.clearForSubLevel(subLevelId);
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        LetEmBurnGameTests.launch(helper, handle, new Vector3d(0.0D, 100.0D, 20.0D));

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    SpawnEvent event = requireSingleEvent(helper, subLevelId, "water_tnt", 0);
                    assertNativeSnapshot(helper, event, Direction.EAST, 2.0F, false);
                    if (countBlocks(helper, event.position(), 4, state -> state.is(Blocks.WATER)) == 0) {
                        helper.fail("Projected Water TNT did not execute its native water explosion");
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void nativeKnockbackPayloadMovesEntities(GameTestHelper helper) {
        QueuedPayload payload = queueDirectPayload(
                helper, MoreTNTBlocks.KNOCKBACK_TNT.block().get(), Direction.NORTH, new Vector3d(8.5D, 8.0D, 8.5D));
        UUID subLevelId = payload.subLevel().getUniqueId();
        MoreTntImpactAudit.clearForSubLevel(subLevelId);
        ArmorStand target = new ArmorStand(helper.getLevel(),
                payload.spawnPosition().x + 1.5D,
                payload.spawnPosition().y,
                payload.spawnPosition().z);
        target.setNoGravity(true);
        target.setInvulnerable(true);
        helper.getLevel().addFreshEntity(target);

        helper.startSequence()
                .thenIdle(8)
                .thenExecute(() -> {
                    requireSingleEvent(helper, subLevelId, "knockback_tnt", 0);
                    if (!target.isAlive() || target.getDeltaMovement().lengthSqr() <= 1.0E-8D) {
                        helper.fail("Projected Knockback TNT did not execute its native entity impulse");
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void nativeFirePayloadIgnitesPreparedSurface(GameTestHelper helper) {
        QueuedPayload payload = queueDirectPayload(
                helper, MoreTNTBlocks.FIRE_TNT.block().get(), Direction.SOUTH, new Vector3d(8.5D, 8.0D, 8.5D));
        UUID subLevelId = payload.subLevel().getUniqueId();
        MoreTntImpactAudit.clearForSubLevel(subLevelId);
        BlockPos floorCenter = BlockPos.containing(payload.spawnPosition()).below();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                helper.getLevel().setBlock(
                        floorCenter.offset(x, 0, z), Blocks.OBSIDIAN.defaultBlockState(), 3);
            }
        }

        helper.startSequence()
                .thenIdle(8)
                .thenExecute(() -> {
                    SpawnEvent event = requireSingleEvent(helper, subLevelId, "fire_tnt", 0);
                    if (countBlocks(helper, event.position(), 6, state -> state.is(Blocks.FIRE)) == 0) {
                        helper.fail("Projected Fire TNT did not execute its native ignition effect");
                    }
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 80)
    public static void nativeStonePayloadCreatesStoneSphere(GameTestHelper helper) {
        QueuedPayload payload = queueDirectPayload(
                helper, MoreTNTBlocks.STONE_TNT.block().get(), Direction.WEST, new Vector3d(8.5D, 12.0D, 8.5D));
        UUID subLevelId = payload.subLevel().getUniqueId();
        MoreTntImpactAudit.clearForSubLevel(subLevelId);

        helper.startSequence()
                .thenIdle(8)
                .thenExecute(() -> {
                    SpawnEvent event = requireSingleEvent(helper, subLevelId, "stone_tnt", 0);
                    if (countBlocks(helper, payload.spawnPosition(), 6, state -> state.is(Blocks.STONE)) == 0) {
                        helper.fail("Projected Stone TNT did not execute its native sphere effect");
                    }
                    assertNativeSnapshot(helper, event, Direction.WEST, 4.0F, false);
                })
                .thenSucceed();
    }

    @GameTest(batch = "letemburn_moretnt_snow_4x", templateNamespace = LetEmBurn.MOD_ID, template = "bootstrap", timeoutTicks = 240)
    public static void nativeSnowFourXPayloadUsesFullVariant(GameTestHelper helper) {
        QueuedPayload payload = queueDirectPayload(
                helper,
                MoreTNTBlocks.SNOW_TNT_4X.block().get(),
                Direction.NORTH,
                new Vector3d(8.5D, 8.0D, 8.5D));
        UUID subLevelId = payload.subLevel().getUniqueId();
        MoreTntImpactAudit.clearForSubLevel(subLevelId);
        BlockPos floorCenter = BlockPos.containing(payload.spawnPosition()).below();
        for (int x = -18; x <= 18; x++) {
            for (int z = -18; z <= 18; z++) {
                helper.getLevel().setBlock(
                        floorCenter.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    SpawnEvent event = requireSingleEvent(helper, subLevelId, "snow_tnt_4x", 0);
                    assertNativeSnapshot(helper, event, Direction.NORTH, 16.0F, false);
                    if (countBlocks(helper, event.position(), 18, state -> state.is(Blocks.SNOW)) == 0) {
                        helper.fail(("Projected Snow TNT 4x did not execute its native snow-layer effect; "
                                + "event=%s, floor=%s, remainingStone=%d")
                                        .formatted(
                                                event,
                                                floorCenter,
                                                countBlocks(
                                                        helper,
                                                        event.position(),
                                                        18,
                                                        state -> state.is(Blocks.STONE))));
                    }
                })
                .thenSucceed();
    }

    static QueuedPayload queueDirectPayload(
            GameTestHelper helper,
            BaseTNTBlock block,
            Direction facing,
            Vector3d position) {
        ServerSubLevelContainer container = LetEmBurnGameTests.requireContainer(helper);
        BlockState state = block.defaultBlockState().setValue(BaseTNTBlock.FACING, facing);
        ServerSubLevel subLevel = spawnSingleBlockSubLevel(
                container, absolutePosition(helper, position), state);
        BlockPos sourcePosition = subLevel.getPlot().getCenterBlock();
        Vec3 localSpawn = new Vec3(
                sourcePosition.getX() + 0.5D,
                sourcePosition.getY(),
                sourcePosition.getZ() + 0.5D);
        ProjectedEffectContext context = new ProjectedEffectContext(
                helper.getLevel(),
                subLevel,
                sourcePosition,
                null,
                localSpawn,
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 1.0D),
                5.0D,
                null);
        PayloadEnvelopeResolver.Resolution resolution = PayloadEnvelopeResolver.INSTANCE.resolve(
                helper.getLevel(), sourcePosition);
        if (!resolution.valid()
                || ImpactPayloadRegistry.INSTANCE.dispatch(context, resolution.snapshot()) != ImpactStatus.QUEUED) {
            helper.fail("More Fun TNTs direct payload was not queued for post-physics execution");
        }
        return new QueuedPayload(subLevel, sourcePosition, context.projectLocalPosition(localSpawn));
    }

    static SpawnEvent requireSingleEvent(
            GameTestHelper helper, UUID subLevelId, String expectedPath, int expectedEnvelopeDepth) {
        var events = MoreTntImpactAudit.eventsForSubLevel(subLevelId);
        if (events.size() != 1) {
            helper.fail("Expected one More Fun TNTs native spawn, got " + events.size());
        }
        SpawnEvent event = events.getFirst();
        if (!event.blockId().equals(ResourceLocation.fromNamespaceAndPath("moretnt", expectedPath))
                || !event.entityTypeId().equals(ResourceLocation.fromNamespaceAndPath("moretnt", expectedPath))
                || event.envelopeDepth() != expectedEnvelopeDepth) {
            helper.fail("More Fun TNTs payload identity or envelope depth changed: " + event);
        }
        return event;
    }

    static void assertNativeSnapshot(
            GameTestHelper helper,
            SpawnEvent event,
            Direction expectedFacing,
            float expectedSize,
            boolean expectedFire) {
        if (event.localFacing() != expectedFacing
                || event.projectedFacing() != expectedFacing
                || Float.compare(event.size(), expectedSize) != 0
                || event.fire() != expectedFire
                || event.initialFuse() != 1
                || event.parentContainedSourceBlock()) {
            helper.fail("More Fun TNTs native payload properties were not preserved: " + event);
        }
    }

    static int countBlocks(
            GameTestHelper helper, Vec3 center, int radius, Predicate<BlockState> predicate) {
        BlockPos origin = BlockPos.containing(center);
        int count = 0;
        for (BlockPos position : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (predicate.test(helper.getLevel().getBlockState(position))) {
                count++;
            }
        }
        return count;
    }

    record QueuedPayload(ServerSubLevel subLevel, BlockPos sourcePosition, Vec3 spawnPosition) {}
}
