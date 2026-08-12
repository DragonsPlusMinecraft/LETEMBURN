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

package dev.marblegate.letemburn.compat.mekanism;

import com.mojang.serialization.DataResult;
import dev.marblegate.letemburn.compat.core.PayloadEnvelopeDecoder;
import mekanism.common.attachments.BlockData;
import mekanism.common.block.BlockCardboardBox;
import mekanism.common.registries.MekanismDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class MekanismCardboardEnvelopeDecoder implements PayloadEnvelopeDecoder {
    public static final MekanismCardboardEnvelopeDecoder INSTANCE = new MekanismCardboardEnvelopeDecoder();

    private MekanismCardboardEnvelopeDecoder() {}

    @Override
    public boolean supports(BlockState state) {
        return state.getBlock() instanceof BlockCardboardBox;
    }

    @Override
    public DecodedEnvelope decode(
            BlockState envelopeState,
            @Nullable CompoundTag envelopeBlockEntityTag,
            HolderLookup.Provider registries) {
        if (envelopeBlockEntityTag == null || !envelopeBlockEntityTag.contains("components", Tag.TAG_COMPOUND)) {
            return DecodedEnvelope.invalid();
        }
        ResourceLocation componentId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(
                MekanismDataComponents.BLOCK_DATA.value());
        if (componentId == null) {
            return DecodedEnvelope.invalid();
        }
        CompoundTag components = envelopeBlockEntityTag.getCompound("components");
        Tag encodedBlockData = components.get(componentId.toString());
        if (encodedBlockData == null) {
            return DecodedEnvelope.invalid();
        }

        DataResult<BlockData> result = BlockData.CODEC.parse(
                registries.createSerializationContext(NbtOps.INSTANCE), encodedBlockData);
        BlockData blockData = result.result().orElse(null);
        if (blockData == null) {
            return DecodedEnvelope.invalid();
        }
        return DecodedEnvelope.payload(blockData.blockState(), blockData.blockEntityTag());
    }
}
