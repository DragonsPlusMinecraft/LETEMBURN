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

package dev.marblegate.letemburn.mixin.draconic;

import com.brandon3055.brandonscore.client.render.BlockEntityRendererTransparent;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = {
        @Condition("sable"),
        @Condition("draconicevolution") })
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Inject(method = "setupAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private static <T extends BlockEntity> void renderTransparent$realPos(BlockEntityRenderer<T> renderer, T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        var helper = Sable.HELPER;
        if (helper.isInPlotGrid(blockEntity)) {
            if (renderer instanceof BlockEntityRendererTransparent<T> transparentRenderer) {
                var i = LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos());
                transparentRenderer.renderTransparent(blockEntity, partialTick, poseStack, bufferSource, i, OverlayTexture.NO_OVERLAY);
            }
        }
    }
}
