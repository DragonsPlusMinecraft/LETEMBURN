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

package dev.marblegate.letemburn.mixin.moretnt;

import io.github.discusser.moretnt.explosions.BaseExplosion;
import java.util.Arrays;
import java.util.HashSet;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Restriction(require = @Condition("moretnt"))
@Mixin(value = BaseExplosion.class, remap = false)
public abstract class BaseExplosionMixin {
    /**
     * More Fun TNTs 1.1.3 calls a Commons Compress utility but does not package or declare that library. The
     * invocation is only used to create this initially empty set, so the JDK implementation is equivalent and
     * avoids making an archive library a runtime dependency. A future MoreTNT release may remove the bad call.
     */
    @Redirect(method = "explode", at = @At(value = "INVOKE", target = "Lorg/apache/commons/compress/utils/Sets;newHashSet([Ljava/lang/Object;)Ljava/util/HashSet;"), require = 0, expect = 1, remap = false)
    private HashSet<Object> letemburn$replaceMissingSetFactory(Object[] elements) {
        return new HashSet<>(Arrays.asList(elements));
    }
}
