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

package dev.marblegate.letemburn.common.payload;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface PayloadEnvelopeDecoder {
    boolean supports(BlockState state);

    DecodedEnvelope decode(
            BlockState envelopeState,
            @Nullable CompoundTag envelopeBlockEntityTag,
            HolderLookup.Provider registries);

    final class DecodedEnvelope {
        private final boolean valid;
        private final @Nullable BlockState state;
        private final @Nullable CompoundTag blockEntityTag;

        private DecodedEnvelope(
                boolean valid, @Nullable BlockState state, @Nullable CompoundTag blockEntityTag) {
            this.valid = valid;
            this.state = state;
            this.blockEntityTag = blockEntityTag == null ? null : blockEntityTag.copy();
        }

        public static DecodedEnvelope invalid() {
            return new DecodedEnvelope(false, null, null);
        }

        public static DecodedEnvelope payload(
                BlockState state, @Nullable CompoundTag blockEntityTag) {
            return new DecodedEnvelope(true, state, blockEntityTag);
        }

        public boolean valid() {
            return valid;
        }

        public @Nullable BlockState state() {
            return state;
        }

        public @Nullable CompoundTag blockEntityTag() {
            return blockEntityTag == null ? null : blockEntityTag.copy();
        }
    }
}
