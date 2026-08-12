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

package dev.marblegate.letemburn.mixin.gametest;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GameTestServer.class)
public abstract class GameTestServerOriginMixin {
    @ModifyArgs(method = "startTests", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;<init>(III)V"))
    private void letemburn$usePhysicsSafeOrigin(Args arguments) {
        arguments.set(0, 0);
        arguments.set(2, 0);
    }

    @Inject(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/gametest/framework/GameTestServer;halt(Z)V"))
    private void letemburn$removeDisposableSubLevels(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        GameTestServer server = (GameTestServer) (Object) this;
        for (ServerLevel level : server.getAllLevels()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            for (ServerSubLevel subLevel : List.copyOf(container.getAllSubLevels())) {
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            }
            container.getHoldingChunkMap().processChanges();
        }
    }
}
