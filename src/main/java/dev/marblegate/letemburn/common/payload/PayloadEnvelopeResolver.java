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

import dev.marblegate.letemburn.config.LetEmBurnConfig;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class PayloadEnvelopeResolver {
    public static final PayloadEnvelopeResolver INSTANCE = new PayloadEnvelopeResolver();

    private final List<PayloadEnvelopeDecoder> decoders = new CopyOnWriteArrayList<>();

    private PayloadEnvelopeResolver() {}

    public void register(PayloadEnvelopeDecoder decoder) {
        if (decoders.stream().noneMatch(existing -> existing.getClass() == decoder.getClass())) {
            decoders.add(decoder);
        }
    }

    public Resolution resolve(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        BlockEntity blockEntity = level.getBlockEntity(position);
        CompoundTag blockEntityTag = blockEntity == null
                ? null
                : blockEntity.saveWithFullMetadata(level.registryAccess());
        return resolve(
                state,
                blockEntityTag,
                level.registryAccess(),
                LetEmBurnConfig.MAX_ENVELOPE_DEPTH.get(),
                LetEmBurnConfig.MAX_PAYLOAD_BYTES.get());
    }

    public Resolution resolve(
            BlockState outerState,
            @Nullable CompoundTag outerBlockEntityTag,
            HolderLookup.Provider registries,
            int maxDepth,
            int maxSerializedBytes) {
        if (outerState.isAir()) {
            return Resolution.invalid(Failure.EMPTY_BLOCK);
        }

        EncodedSnapshot encoded;
        try {
            encoded = encode(outerState, outerBlockEntityTag, maxSerializedBytes);
        } catch (SizeLimitException ignored) {
            return Resolution.invalid(Failure.TOO_LARGE);
        } catch (IOException exception) {
            return Resolution.invalid(Failure.MALFORMED_DATA);
        }

        BlockState payloadState = outerState;
        CompoundTag payloadTag = copy(outerBlockEntityTag);
        int depth = 0;
        while (true) {
            PayloadEnvelopeDecoder decoder = findDecoder(payloadState);
            if (decoder == null) {
                break;
            }
            if (depth >= maxDepth) {
                return Resolution.invalid(Failure.TOO_DEEP);
            }
            PayloadEnvelopeDecoder.DecodedEnvelope decoded;
            try {
                decoded = decoder.decode(payloadState, payloadTag, registries);
            } catch (RuntimeException ignored) {
                return Resolution.invalid(Failure.MALFORMED_ENVELOPE);
            }
            if (!decoded.valid() || decoded.state() == null || decoded.state().isAir()) {
                return Resolution.invalid(Failure.MALFORMED_ENVELOPE);
            }
            payloadState = decoded.state();
            payloadTag = decoded.blockEntityTag();
            depth++;
        }

        return Resolution.valid(new PayloadSnapshot(
                outerState,
                outerBlockEntityTag,
                payloadState,
                payloadTag,
                depth,
                encoded.size(),
                encoded.fingerprint()));
    }

    private @Nullable PayloadEnvelopeDecoder findDecoder(BlockState state) {
        for (PayloadEnvelopeDecoder decoder : decoders) {
            if (decoder.supports(state)) {
                return decoder;
            }
        }
        return null;
    }

    private static EncodedSnapshot encode(
            BlockState state, @Nullable CompoundTag blockEntityTag, int maxBytes) throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("state", NbtUtils.writeBlockState(state));
        if (blockEntityTag != null) {
            root.put("block_entity", blockEntityTag);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("letemburn-payload-v1".getBytes(StandardCharsets.UTF_8));
            LimitedDigestOutputStream bytes = new LimitedDigestOutputStream(digest, maxBytes);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(root, output);
            }
            String fingerprint = HexFormat.of().formatHex(digest.digest());
            return new EncodedSnapshot(bytes.size(), fingerprint);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static @Nullable CompoundTag copy(@Nullable CompoundTag tag) {
        return tag == null ? null : tag.copy();
    }

    public enum Failure {
        NONE,
        EMPTY_BLOCK,
        TOO_LARGE,
        TOO_DEEP,
        MALFORMED_DATA,
        MALFORMED_ENVELOPE
    }

    public record Resolution(@Nullable PayloadSnapshot snapshot, Failure failure) {
        public static Resolution valid(PayloadSnapshot snapshot) {
            return new Resolution(snapshot, Failure.NONE);
        }

        public static Resolution invalid(Failure failure) {
            return new Resolution(null, failure);
        }

        public boolean valid() {
            return snapshot != null;
        }
    }

    private record EncodedSnapshot(int size, String fingerprint) {}

    private static final class LimitedDigestOutputStream extends OutputStream {
        private final MessageDigest digest;
        private final int limit;
        private int count;

        private LimitedDigestOutputStream(MessageDigest digest, int limit) {
            this.digest = digest;
            this.limit = limit;
        }

        @Override
        public void write(int value) {
            ensureCapacityFor(1);
            digest.update((byte) value);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            ensureCapacityFor(length);
            digest.update(bytes, offset, length);
            count += length;
        }

        private int size() {
            return count;
        }

        private void ensureCapacityFor(int additionalBytes) {
            if (additionalBytes < 0 || count > limit - additionalBytes) {
                throw new SizeLimitException();
            }
        }
    }

    private static final class SizeLimitException extends RuntimeException {}
}
