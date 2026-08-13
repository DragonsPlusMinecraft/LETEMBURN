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

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.effect.ChainReactionCoordinator;
import dev.marblegate.letemburn.common.effect.EffectKey;
import dev.marblegate.letemburn.common.effect.TransactionalEffect;
import dev.marblegate.letemburn.common.impact.ImpactStatus;
import dev.marblegate.letemburn.integration.nuclearscience.NuclearFissionProjectionAudit.Kind;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nuclearscience.common.tile.reactor.fission.TileFissionReactorCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NuclearFissionProjection {
    private static final String MELTDOWN_FINGERPRINT = "nuclearscience:fission-meltdown";
    private static final ThreadLocal<Execution> ACTIVE = new ThreadLocal<>();
    private static final Set<TileFissionReactorCore> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());

    private NuclearFissionProjection() {}

    public static boolean scheduleMeltdown(TileFissionReactorCore core, int overheatingTicks) {
        Projection projection = projection(core);
        if (projection == null) {
            return false;
        }

        synchronized (PENDING) {
            if (!PENDING.add(core)) {
                return true;
            }
        }

        CoreSnapshot snapshot;
        try {
            snapshot = CoreSnapshot.capture(core, projection.level(), projection.localPosition());
            EffectKey key = new EffectKey(
                    projection.level().dimension(),
                    projection.subLevel().getUniqueId(),
                    projection.localPosition(),
                    projection.level().getGameTime(),
                    MELTDOWN_FINGERPRINT);
            ImpactStatus status = ChainReactionCoordinator.INSTANCE.reserve(
                    projection.level(),
                    key,
                    marker -> executeNativeMeltdown(core, projection, snapshot, marker, overheatingTicks),
                    () -> rollback(core, snapshot));
            if (status == ImpactStatus.QUEUED) {
                NuclearFissionProjectionAudit.record(
                        Kind.MELTDOWN_QUEUED,
                        projection.subLevel().getUniqueId(),
                        projection.localPosition(),
                        projection.globalCenter(),
                        overheatingTicks);
            } else {
                clearPending(core);
            }
        } catch (RuntimeException exception) {
            clearPending(core);
            LetEmBurn.LOGGER.error(
                    "Failed to reserve projected Nuclear Science fission meltdown at {}",
                    projection.localPosition(),
                    exception);
        }
        return true;
    }

    public static boolean isExecuting(TileFissionReactorCore core) {
        Execution execution = ACTIVE.get();
        return execution != null && execution.core() == core;
    }

    public static BlockPos projectedWorldPosition(TileFissionReactorCore core, BlockPos original) {
        Execution execution = ACTIVE.get();
        return execution != null && execution.core() == core ? execution.projection().globalOrigin() : original;
    }

    public static boolean skipInitialParentCoreWrite(TileFissionReactorCore core) {
        Execution execution = ACTIVE.get();
        if (execution == null || execution.core() != core || execution.initialWriteSeen()) {
            return false;
        }
        execution.markInitialWriteSeen();
        Projection projection = execution.projection();
        NuclearFissionProjectionAudit.record(
                Kind.INITIAL_PARENT_CORE_WRITE_SKIPPED,
                projection.subLevel().getUniqueId(),
                projection.localPosition(),
                projection.globalCenter(),
                execution.overheatingTicks());
        return true;
    }

    public static void beginNativeEffects(TileFissionReactorCore core) {
        Execution execution = ACTIVE.get();
        if (execution == null || execution.core() != core || execution.marker().nativeEffectStarted()) {
            return;
        }
        execution.snapshot().consume(core);
        execution.marker().markNativeEffectStarted();
        Projection projection = execution.projection();
        NuclearFissionProjectionAudit.record(
                Kind.NATIVE_EFFECT_STARTED,
                projection.subLevel().getUniqueId(),
                projection.localPosition(),
                projection.globalCenter(),
                execution.overheatingTicks());
    }

    public static void recordNativeExplosion(TileFissionReactorCore core) {
        Execution execution = ACTIVE.get();
        if (execution == null || execution.core() != core) {
            return;
        }
        Projection projection = execution.projection();
        NuclearFissionProjectionAudit.record(
                Kind.NATIVE_EXPLOSION,
                projection.subLevel().getUniqueId(),
                projection.localPosition(),
                projection.globalCenter(),
                execution.overheatingTicks());
    }

    public static BlockPos projectCurrentBlockPosition(
            TileFissionReactorCore core, BlockPos localPosition, Kind kind) {
        CurrentProjection projection = currentProjection(core, Vec3.atCenterOf(localPosition));
        if (projection == null) {
            return localPosition;
        }
        NuclearFissionProjectionAudit.record(
                kind,
                projection.subLevel().getUniqueId(),
                core.getBlockPos(),
                projection.globalPosition(),
                -1);
        return BlockPos.containing(projection.globalPosition());
    }

    public static Vec3 projectCurrentPosition(TileFissionReactorCore core, Vec3 localPosition, Kind kind) {
        CurrentProjection projection = currentProjection(core, localPosition);
        if (projection == null) {
            return localPosition;
        }
        NuclearFissionProjectionAudit.record(
                kind,
                projection.subLevel().getUniqueId(),
                core.getBlockPos(),
                projection.globalPosition(),
                -1);
        return projection.globalPosition();
    }

    private static void executeNativeMeltdown(
            TileFissionReactorCore core,
            Projection projection,
            CoreSnapshot snapshot,
            TransactionalEffect.CommitMarker marker,
            int overheatingTicks) {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Nested Nuclear Science meltdown projection is not supported");
        }
        Execution execution = new Execution(core, projection, snapshot, marker, overheatingTicks);
        ACTIVE.set(execution);
        try {
            core.meltdown();
            if (!marker.nativeEffectStarted()) {
                throw new IllegalStateException("Native fission meltdown completed without starting an effect");
            }
            NuclearFissionProjectionAudit.record(
                    Kind.MELTDOWN_COMPLETE,
                    projection.subLevel().getUniqueId(),
                    projection.localPosition(),
                    projection.globalCenter(),
                    overheatingTicks);
        } finally {
            ACTIVE.remove();
            clearPending(core);
        }
    }

    private static void rollback(TileFissionReactorCore core, CoreSnapshot snapshot) {
        clearPending(core);
        snapshot.restore(core);
    }

    private static void clearPending(TileFissionReactorCore core) {
        synchronized (PENDING) {
            PENDING.remove(core);
        }
    }

    private static @Nullable Projection projection(TileFissionReactorCore core) {
        Level level = core.getLevel();
        if (!(level instanceof ServerLevel serverLevel)
                || !(Sable.HELPER.getContaining(core) instanceof ServerSubLevel subLevel)) {
            return null;
        }
        BlockPos localPosition = core.getBlockPos();
        Vec3 globalCenter = Sable.HELPER.projectOutOfSubLevel(serverLevel, Vec3.atCenterOf(localPosition));
        return new Projection(
                serverLevel,
                subLevel,
                localPosition.immutable(),
                globalCenter,
                BlockPos.containing(globalCenter));
    }

    private static @Nullable CurrentProjection currentProjection(
            TileFissionReactorCore core, Vec3 localPosition) {
        Level level = core.getLevel();
        if (!(level instanceof ServerLevel serverLevel)
                || !(Sable.HELPER.getContaining(core) instanceof ServerSubLevel subLevel)) {
            return null;
        }
        Vec3 globalPosition = Sable.HELPER.projectOutOfSubLevel(serverLevel, localPosition);
        return new CurrentProjection(subLevel, globalPosition);
    }

    private record Projection(
            ServerLevel level,
            ServerSubLevel subLevel,
            BlockPos localPosition,
            Vec3 globalCenter,
            BlockPos globalOrigin) {}

    private record CurrentProjection(ServerSubLevel subLevel, Vec3 globalPosition) {}

    private static final class Execution {
        private final TileFissionReactorCore core;
        private final Projection projection;
        private final CoreSnapshot snapshot;
        private final TransactionalEffect.CommitMarker marker;
        private final int overheatingTicks;
        private boolean initialWriteSeen;

        private Execution(
                TileFissionReactorCore core,
                Projection projection,
                CoreSnapshot snapshot,
                TransactionalEffect.CommitMarker marker,
                int overheatingTicks) {
            this.core = core;
            this.projection = projection;
            this.snapshot = snapshot;
            this.marker = marker;
            this.overheatingTicks = overheatingTicks;
        }

        private TileFissionReactorCore core() {
            return core;
        }

        private Projection projection() {
            return projection;
        }

        private CoreSnapshot snapshot() {
            return snapshot;
        }

        private TransactionalEffect.CommitMarker marker() {
            return marker;
        }

        private int overheatingTicks() {
            return overheatingTicks;
        }

        private boolean initialWriteSeen() {
            return initialWriteSeen;
        }

        private void markInitialWriteSeen() {
            initialWriteSeen = true;
        }
    }

    private record CoreSnapshot(
            ServerLevel level, BlockPos localPosition, BlockState blockState, CompoundTag blockEntityTag) {
        private static CoreSnapshot capture(
                TileFissionReactorCore core, ServerLevel level, BlockPos localPosition) {
            if (level.getBlockEntity(localPosition) != core) {
                throw new IllegalStateException("Fission reactor core is no longer present at " + localPosition);
            }
            return new CoreSnapshot(
                    level,
                    localPosition.immutable(),
                    level.getBlockState(localPosition),
                    core.saveWithFullMetadata(level.registryAccess()));
        }

        private void consume(TileFissionReactorCore core) {
            if (level.getBlockEntity(localPosition) != core
                    || !level.getBlockState(localPosition).equals(blockState)) {
                throw new IllegalStateException("Fission reactor core changed before projected meltdown commit");
            }
            if (!level.setBlock(localPosition, Blocks.AIR.defaultBlockState(), 11)
                    && !level.getBlockState(localPosition).isAir()) {
                throw new IllegalStateException("Failed to consume projected fission reactor core");
            }
        }

        private void restore(TileFissionReactorCore originalCore) {
            if (level.getBlockEntity(localPosition) == originalCore
                    && level.getBlockState(localPosition).equals(blockState)) {
                return;
            }
            if (!level.getBlockState(localPosition).isAir()) {
                throw new IllegalStateException("Refusing to overwrite a replacement block at " + localPosition);
            }
            if (!level.setBlock(localPosition, blockState, 11)) {
                throw new IllegalStateException("Failed to restore fission reactor core at " + localPosition);
            }
            BlockEntity restored = level.getBlockEntity(localPosition);
            if (restored == null) {
                throw new IllegalStateException("Restored fission reactor core has no block entity");
            }
            CompoundTag restoredTag = blockEntityTag.copy();
            restoredTag.putInt("x", localPosition.getX());
            restoredTag.putInt("y", localPosition.getY());
            restoredTag.putInt("z", localPosition.getZ());
            restored.loadWithComponents(restoredTag, level.registryAccess());
            restored.setChanged();
        }
    }
}
