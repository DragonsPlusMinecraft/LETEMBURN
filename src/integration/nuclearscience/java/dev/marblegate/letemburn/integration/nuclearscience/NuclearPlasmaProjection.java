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

package dev.marblegate.letemburn.integration.nuclearscience;

import dev.marblegate.letemburn.common.effect.ChainReactionCoordinator;
import dev.marblegate.letemburn.common.effect.EffectKey;
import dev.marblegate.letemburn.common.effect.TransactionalEffect;
import dev.marblegate.letemburn.common.impact.ImpactStatus;
import dev.marblegate.letemburn.integration.nuclearscience.NuclearPlasmaProjectionAudit.Kind;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nuclearscience.common.block.subtype.SubtypeNuclearMachine;
import nuclearscience.common.tags.NuclearScienceTags;
import nuclearscience.common.tile.reactor.fusion.TilePlasma;
import nuclearscience.registers.NuclearScienceBlocks;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NuclearPlasmaProjection {
    private static final int MAX_NATIVE_SPREAD = 6;
    private static final String EFFECT_FINGERPRINT = "nuclearscience:escaped-plasma";
    private static final Map<TilePlasma, PlasmaCloud> CLOUDS = Collections.synchronizedMap(new WeakHashMap<>());

    private NuclearPlasmaProjection() {}

    public static void observeNativeTickStart(TilePlasma plasma) {
        Projection projection = projection(plasma);
        if (projection == null
                || plasma.ticksExisted.getValue() != 0
                || plasma.spread.getValue() <= 0) {
            return;
        }
        synchronized (CLOUDS) {
            if (CLOUDS.containsKey(plasma)) {
                return;
            }
        }
        long gameTime = projection.level().getGameTime();
        PlasmaCloud cloud = new PlasmaCloud(
                plasma.getBlockPos(),
                projection.subLevel().getPlot().getBoundingBox(),
                gameTime);
        synchronized (CLOUDS) {
            PlasmaCloud inherited = CLOUDS.putIfAbsent(plasma, cloud);
            if (inherited != null) {
                return;
            }
        }
    }

    public static void observeNativeBlockWrite(
            TilePlasma source, Level targetLevel, BlockPos position, BlockState writtenState) {
        if (!writtenState.is(NuclearScienceBlocks.BLOCK_PLASMA.get())
                || !targetLevel.getBlockState(position).is(NuclearScienceBlocks.BLOCK_PLASMA.get())) {
            return;
        }
        Projection projection = projection(source);
        if (projection == null || targetLevel != projection.level()) {
            return;
        }
        PlasmaCloud cloud;
        TilePlasma child = null;
        synchronized (CLOUDS) {
            cloud = CLOUDS.get(source);
            BlockEntity childBlockEntity = targetLevel.getBlockEntity(position);
            if (childBlockEntity instanceof TilePlasma childPlasma) {
                child = childPlasma;
            }
            if (cloud != null && child != null) {
                CLOUDS.putIfAbsent(child, cloud);
            }
        }
        if (cloud == null
                || child == null
                || cloud.hasEscaped()
                || outside(cloud.initialBounds(), source.getBlockPos())
                || !outside(cloud.initialBounds(), position)) {
            return;
        }

        long gameTime = projection.level().getGameTime();
        Vec3 globalPosition = project(projection.level(), position);
        BlockPos globalBlockPosition = BlockPos.containing(globalPosition);
        EscapeCandidate candidate = new EscapeCandidate(
                cloud.rootPosition(),
                position,
                Math.clamp(source.spread.getValue() - 1, 0, MAX_NATIVE_SPREAD),
                cloud.registeredGameTime());
        NuclearPlasmaProjectionAudit.record(
                Kind.CANDIDATE_REGISTERED,
                projection.subLevel().getUniqueId(),
                candidate.rootPosition(),
                position,
                globalPosition,
                candidate.remainingSpread(),
                gameTime);
        EffectKey effectKey = new EffectKey(
                projection.level().dimension(),
                projection.subLevel().getUniqueId(),
                position,
                gameTime,
                EFFECT_FINGERPRINT
                        + ':'
                        + candidate.rootPosition().asLong()
                        + ':'
                        + candidate.registeredGameTime());
        ImpactStatus status = ChainReactionCoordinator.INSTANCE.reserve(
                projection.level(),
                effectKey,
                marker -> createNativeParentSeed(
                        projection.level(),
                        projection.subLevel().getUniqueId(),
                        cloud,
                        candidate,
                        position,
                        globalBlockPosition,
                        globalPosition,
                        gameTime,
                        marker),
                () -> {});
        NuclearPlasmaProjectionAudit.record(
                status == ImpactStatus.QUEUED ? Kind.ESCAPE_QUEUED : Kind.DUPLICATE_SUPPRESSED,
                projection.subLevel().getUniqueId(),
                candidate.rootPosition(),
                position,
                globalPosition,
                candidate.remainingSpread(),
                gameTime);
    }

    public static void observeNativeSteamDelivery(
            TilePlasma plasma, int requestedAmount, int temperature, int acceptedAmount) {
        if (!(plasma.getLevel() instanceof ServerLevel serverLevel)
                || Sable.HELPER.getContaining(plasma) instanceof ServerSubLevel) {
            return;
        }
        NuclearPlasmaProjectionAudit.recordNativeSteamDelivery(
                plasma.getBlockPos(), requestedAmount, temperature, acceptedAmount, serverLevel.getGameTime());
    }

    private static void createNativeParentSeed(
            ServerLevel level,
            UUID subLevelId,
            PlasmaCloud cloud,
            EscapeCandidate candidate,
            BlockPos localExitPosition,
            BlockPos globalBlockPosition,
            Vec3 globalPosition,
            long gameTime,
            TransactionalEffect.CommitMarker marker) {
        synchronized (cloud) {
            if (cloud.escaped) {
                NuclearPlasmaProjectionAudit.record(
                        Kind.DUPLICATE_SUPPRESSED,
                        subLevelId,
                        candidate.rootPosition(),
                        localExitPosition,
                        globalPosition,
                        candidate.remainingSpread(),
                        gameTime);
                return;
            }
            BlockState existingState = level.getBlockState(globalBlockPosition);
            if (existingState.is(NuclearScienceBlocks.BLOCK_PLASMA.get())) {
                cloud.escaped = true;
                NuclearPlasmaProjectionAudit.record(
                        Kind.DUPLICATE_SUPPRESSED,
                        subLevelId,
                        candidate.rootPosition(),
                        localExitPosition,
                        globalPosition,
                        candidate.remainingSpread(),
                        gameTime);
                return;
            }
            if (!canNativePlasmaOccupy(level, globalBlockPosition, existingState)) {
                NuclearPlasmaProjectionAudit.record(
                        Kind.PARENT_TARGET_PROTECTED,
                        subLevelId,
                        candidate.rootPosition(),
                        localExitPosition,
                        globalPosition,
                        candidate.remainingSpread(),
                        gameTime);
                return;
            }

            BlockState plasmaState = NuclearScienceBlocks.BLOCK_PLASMA.get().defaultBlockState();
            if (!level.setBlockAndUpdate(globalBlockPosition, plasmaState)
                    && !level.getBlockState(globalBlockPosition).is(NuclearScienceBlocks.BLOCK_PLASMA.get())) {
                throw new IllegalStateException("Failed to create projected Nuclear Science plasma seed");
            }
            marker.markNativeEffectStarted();
            cloud.escaped = true;
            BlockEntity blockEntity = level.getBlockEntity(globalBlockPosition);
            if (!(blockEntity instanceof TilePlasma plasma)) {
                throw new IllegalStateException("Projected Nuclear Science plasma seed has no native block entity");
            }
            plasma.ticksExisted.setValue(0);
            plasma.spread.setValue(candidate.remainingSpread());
            plasma.setChanged();
            NuclearPlasmaProjectionAudit.record(
                    Kind.PARENT_SEED_CREATED,
                    subLevelId,
                    candidate.rootPosition(),
                    localExitPosition,
                    globalPosition,
                    candidate.remainingSpread(),
                    gameTime);
        }
    }

    private static boolean canNativePlasmaOccupy(Level level, BlockPos position, BlockState state) {
        if (state.is(NuclearScienceBlocks.BLOCK_PLASMA.get())) {
            return true;
        }
        return state.getDestroySpeed(level, position) != -1.0F
                && !state.is(NuclearScienceTags.Blocks.FUSION_CONTAINMENT)
                && state.getBlock() != NuclearScienceBlocks.BLOCKS_NUCLEARMACHINE.getValue(
                        SubtypeNuclearMachine.fusionreactorcore);
    }

    private static boolean outside(BoundingBox3ic bounds, BlockPos position) {
        return position.getX() < bounds.minX()
                || position.getX() > bounds.maxX()
                || position.getY() < bounds.minY()
                || position.getY() > bounds.maxY()
                || position.getZ() < bounds.minZ()
                || position.getZ() > bounds.maxZ();
    }

    private static Vec3 project(ServerLevel level, BlockPos localPosition) {
        return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(localPosition));
    }

    private static @Nullable Projection projection(TilePlasma plasma) {
        if (!(plasma.getLevel() instanceof ServerLevel serverLevel)
                || !(Sable.HELPER.getContaining(plasma) instanceof ServerSubLevel subLevel)) {
            return null;
        }
        return new Projection(serverLevel, subLevel);
    }

    private record Projection(ServerLevel level, ServerSubLevel subLevel) {}

    private static final class PlasmaCloud {
        private final BlockPos rootPosition;
        private final BoundingBox3ic initialBounds;
        private final long registeredGameTime;
        private boolean escaped;

        private PlasmaCloud(
                BlockPos rootPosition, BoundingBox3ic initialBounds, long registeredGameTime) {
            this.rootPosition = rootPosition.immutable();
            this.initialBounds = new BoundingBox3i(initialBounds);
            this.registeredGameTime = registeredGameTime;
        }

        private synchronized boolean hasEscaped() {
            return escaped;
        }

        private BlockPos rootPosition() {
            return rootPosition;
        }

        private BoundingBox3ic initialBounds() {
            return initialBounds;
        }

        private long registeredGameTime() {
            return registeredGameTime;
        }
    }

    private record EscapeCandidate(
            BlockPos rootPosition, BlockPos exitPosition, int remainingSpread, long registeredGameTime) {
        private EscapeCandidate {
            rootPosition = rootPosition.immutable();
            exitPosition = exitPosition.immutable();
        }
    }
}
