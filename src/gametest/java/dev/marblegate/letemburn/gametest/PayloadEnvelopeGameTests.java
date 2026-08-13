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

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.payload.PayloadEnvelopeDecoder;
import dev.marblegate.letemburn.common.payload.PayloadEnvelopeResolver;
import dev.marblegate.letemburn.common.payload.PayloadSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

@GameTestHolder(LetEmBurn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PayloadEnvelopeGameTests {
    private PayloadEnvelopeGameTests() {}

    @GameTest(template = "bootstrap", timeoutTicks = 20)
    public static void directPayloadSnapshotIsImmutable(GameTestHelper helper) {
        CompoundTag source = new CompoundTag();
        source.putString("name", "payload");
        PayloadEnvelopeResolver.Resolution first = PayloadEnvelopeResolver.INSTANCE.resolve(
                Blocks.TNT.defaultBlockState(), source, helper.getLevel().registryAccess(), 8, 4_096);
        source.putString("name", "mutated source");

        require(first.valid(), helper, "Direct payload resolution failed");
        PayloadSnapshot snapshot = first.snapshot();
        require(snapshot.envelopeDepth() == 0, helper, "Direct payload unexpectedly gained an envelope");
        require(snapshot.payloadState().is(Blocks.TNT), helper, "Direct payload block state changed");
        require(
                "payload".equals(snapshot.payloadBlockEntityTag().getString("name")),
                helper,
                "Payload snapshot retained a mutable source tag");

        CompoundTag returned = snapshot.payloadBlockEntityTag();
        returned.putString("name", "mutated getter");
        require(
                "payload".equals(snapshot.payloadBlockEntityTag().getString("name")),
                helper,
                "Payload snapshot exposed mutable tag data");

        PayloadEnvelopeResolver.Resolution second = PayloadEnvelopeResolver.INSTANCE.resolve(
                snapshot.outerState(),
                snapshot.outerBlockEntityTag(),
                helper.getLevel().registryAccess(),
                8,
                4_096);
        require(second.valid(), helper, "Repeated direct payload resolution failed");
        require(
                snapshot.fingerprint().equals(second.snapshot().fingerprint()),
                helper,
                "Equivalent payload snapshots received different fingerprints");
        helper.succeed();
    }

    @GameTest(template = "bootstrap", timeoutTicks = 20)
    public static void envelopeDepthAndByteLimitsAreFailClosed(GameTestHelper helper) {
        PayloadEnvelopeResolver.INSTANCE.register(CountingEnvelopeDecoder.INSTANCE);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        PayloadEnvelopeResolver.Resolution eight = PayloadEnvelopeResolver.INSTANCE.resolve(
                Blocks.BARREL.defaultBlockState(), countingTag(7), registries, 8, 4_096);
        PayloadEnvelopeResolver.Resolution nine = PayloadEnvelopeResolver.INSTANCE.resolve(
                Blocks.BARREL.defaultBlockState(), countingTag(8), registries, 8, 4_096);
        PayloadEnvelopeResolver.Resolution malformed = PayloadEnvelopeResolver.INSTANCE.resolve(
                Blocks.BARREL.defaultBlockState(), new CompoundTag(), registries, 8, 4_096);
        CompoundTag oversizedTag = new CompoundTag();
        oversizedTag.putByteArray("data", new byte[8_192]);
        PayloadEnvelopeResolver.Resolution oversized = PayloadEnvelopeResolver.INSTANCE.resolve(
                Blocks.TNT.defaultBlockState(), oversizedTag, registries, 8, 1_024);

        require(eight.valid(), helper, "Eight envelope layers were rejected");
        require(eight.snapshot().envelopeDepth() == 8, helper, "Eight envelope layers were miscounted");
        require(eight.snapshot().payloadState().is(Blocks.TNT), helper, "Nested payload state was lost");
        require(
                !nine.valid() && nine.failure() == PayloadEnvelopeResolver.Failure.TOO_DEEP,
                helper,
                "Nine envelope layers did not fail closed as TOO_DEEP");
        require(
                !malformed.valid()
                        && malformed.failure() == PayloadEnvelopeResolver.Failure.MALFORMED_ENVELOPE,
                helper,
                "Malformed envelope did not fail closed");
        require(
                !oversized.valid() && oversized.failure() == PayloadEnvelopeResolver.Failure.TOO_LARGE,
                helper,
                "Oversized payload did not fail closed");
        helper.succeed();
    }

    @GameTest(template = "bootstrap", timeoutTicks = 20)
    public static void streamingFingerprintMatchesLegacyEncoding(GameTestHelper helper) {
        CompoundTag source = new CompoundTag();
        source.putString("name", "streamed payload");
        source.putByteArray("data", new byte[2_048]);
        LegacyEncoding legacy = legacyEncode(Blocks.TNT.defaultBlockState(), source);

        PayloadEnvelopeResolver.Resolution streamed = PayloadEnvelopeResolver.INSTANCE.resolve(
                Blocks.TNT.defaultBlockState(),
                source,
                helper.getLevel().registryAccess(),
                8,
                legacy.size());
        PayloadEnvelopeResolver.Resolution oneByteTooSmall = PayloadEnvelopeResolver.INSTANCE.resolve(
                Blocks.TNT.defaultBlockState(),
                source,
                helper.getLevel().registryAccess(),
                8,
                legacy.size() - 1);

        require(streamed.valid(), helper, "Payload exactly at the byte limit was rejected");
        require(
                streamed.snapshot().serializedBytes() == legacy.size(),
                helper,
                "Streaming payload size differs from the legacy encoding");
        require(
                streamed.snapshot().fingerprint().equals(legacy.fingerprint()),
                helper,
                "Streaming payload fingerprint differs from the legacy encoding");
        require(
                !oneByteTooSmall.valid()
                        && oneByteTooSmall.failure() == PayloadEnvelopeResolver.Failure.TOO_LARGE,
                helper,
                "Payload exceeding the byte limit by one did not fail closed");
        helper.succeed();
    }

    private static CompoundTag countingTag(int remaining) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("remaining", remaining);
        return tag;
    }

    private static void require(boolean condition, GameTestHelper helper, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    private static LegacyEncoding legacyEncode(
            BlockState state, @Nullable CompoundTag blockEntityTag) {
        CompoundTag root = new CompoundTag();
        root.put("state", NbtUtils.writeBlockState(state));
        if (blockEntityTag != null) {
            root.put("block_entity", blockEntityTag);
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(root, output);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("letemburn-payload-v1".getBytes(StandardCharsets.UTF_8));
            byte[] serialized = bytes.toByteArray();
            return new LegacyEncoding(
                    serialized.length,
                    HexFormat.of().formatHex(digest.digest(serialized)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to produce legacy payload encoding", exception);
        }
    }

    private record LegacyEncoding(int size, String fingerprint) {}

    private enum CountingEnvelopeDecoder implements PayloadEnvelopeDecoder {
        INSTANCE;

        @Override
        public boolean supports(BlockState state) {
            return state.is(Blocks.BARREL);
        }

        @Override
        public DecodedEnvelope decode(
                BlockState envelopeState,
                @Nullable CompoundTag envelopeBlockEntityTag,
                HolderLookup.Provider registries) {
            if (envelopeBlockEntityTag == null || !envelopeBlockEntityTag.contains("remaining")) {
                return DecodedEnvelope.invalid();
            }
            int remaining = envelopeBlockEntityTag.getInt("remaining");
            if (remaining < 0) {
                return DecodedEnvelope.invalid();
            }
            return remaining == 0
                    ? DecodedEnvelope.payload(Blocks.TNT.defaultBlockState(), null)
                    : DecodedEnvelope.payload(
                            Blocks.BARREL.defaultBlockState(), countingTag(remaining - 1));
        }
    }
}
