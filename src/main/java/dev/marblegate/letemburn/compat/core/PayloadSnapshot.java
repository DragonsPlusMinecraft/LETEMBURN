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

package dev.marblegate.letemburn.compat.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class PayloadSnapshot {
    private final BlockState outerState;
    private final @Nullable CompoundTag outerBlockEntityTag;
    private final BlockState payloadState;
    private final @Nullable CompoundTag payloadBlockEntityTag;
    private final int envelopeDepth;
    private final int serializedBytes;
    private final String fingerprint;

    PayloadSnapshot(
            BlockState outerState,
            @Nullable CompoundTag outerBlockEntityTag,
            BlockState payloadState,
            @Nullable CompoundTag payloadBlockEntityTag,
            int envelopeDepth,
            int serializedBytes,
            String fingerprint) {
        this.outerState = outerState;
        this.outerBlockEntityTag = copy(outerBlockEntityTag);
        this.payloadState = payloadState;
        this.payloadBlockEntityTag = copy(payloadBlockEntityTag);
        this.envelopeDepth = envelopeDepth;
        this.serializedBytes = serializedBytes;
        this.fingerprint = fingerprint;
    }

    public BlockState outerState() {
        return outerState;
    }

    public @Nullable CompoundTag outerBlockEntityTag() {
        return copy(outerBlockEntityTag);
    }

    public BlockState payloadState() {
        return payloadState;
    }

    public @Nullable CompoundTag payloadBlockEntityTag() {
        return copy(payloadBlockEntityTag);
    }

    public int envelopeDepth() {
        return envelopeDepth;
    }

    public int serializedBytes() {
        return serializedBytes;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public boolean removeOuter(ServerLevel level, BlockPos position) {
        if (!level.getBlockState(position).equals(outerState)) {
            return false;
        }
        return level.setBlock(position, Blocks.AIR.defaultBlockState(), 11);
    }

    public void restoreOuter(ServerLevel level, BlockPos position) {
        if (level.getBlockState(position).equals(outerState)) {
            return;
        }
        if (!level.getBlockState(position).isAir()) {
            throw new IllegalStateException("Refusing to overwrite a replacement block at " + position);
        }
        if (!level.setBlock(position, outerState, 11)) {
            throw new IllegalStateException("Failed to restore payload block at " + position);
        }
        if (outerBlockEntityTag == null) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null) {
            throw new IllegalStateException("Restored payload has no block entity at " + position);
        }
        CompoundTag restoredTag = outerBlockEntityTag.copy();
        restoredTag.putInt("x", position.getX());
        restoredTag.putInt("y", position.getY());
        restoredTag.putInt("z", position.getZ());
        blockEntity.loadWithComponents(restoredTag, level.registryAccess());
        blockEntity.setChanged();
    }

    private static @Nullable CompoundTag copy(@Nullable CompoundTag tag) {
        return tag == null ? null : tag.copy();
    }
}
