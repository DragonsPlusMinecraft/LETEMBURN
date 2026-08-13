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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class NuclearFissionProjectionOwnershipTest {
    private static final String FISSION_CORE = "nuclearscience/common/tile/reactor/fission/TileFissionReactorCore";
    private static final String PROJECTION = "dev/marblegate/letemburn/integration/nuclearscience/NuclearFissionProjection";
    private static final String RADIATION_SYSTEM = "voltaic/api/radiation/RadiationSystem";
    private static final String RADIATION_SOURCE = "voltaic/api/radiation/SimpleRadiationSource";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String EXPLOSION = "net/minecraft/world/level/Explosion";

    @Test
    void nativeFissionCoreRetainsItsRadiationAndMeltdownCallSites() throws IOException {
        ClassNode nativeCore = readClass(FISSION_CORE);
        MethodNode tickServer = method(nativeCore, "tickServer");
        MethodNode meltdown = method(nativeCore, "meltdown");

        assertEquals(1, countMethodCalls(tickServer, RADIATION_SYSTEM, "addRadiationSource"));
        assertEquals(1, countAllocations(tickServer, RADIATION_SOURCE));
        assertEquals(1, countMethodCalls(tickServer, FISSION_CORE, "meltdown"));
        assertEquals(1, countMethodCalls(meltdown, LEVEL, "explode"));
        assertEquals(1, countAllocations(meltdown, EXPLOSION));
    }

    @Test
    void projectionInvokesTheNativeMethodWithoutReimplementingEffects() throws IOException {
        ClassNode projection = readClass(PROJECTION);
        long nativeMeltdownCalls = 0L;
        for (MethodNode method : projection.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(FISSION_CORE) && call.name.equals("meltdown")) {
                        nativeMeltdownCalls++;
                    }
                    assertFalse(
                            call.owner.equals(RADIATION_SYSTEM)
                                    || (call.owner.equals(LEVEL) && call.name.equals("explode")),
                            "Projection must redirect native effects instead of creating replacements");
                }
                if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                    assertFalse(
                            type.desc.equals(RADIATION_SOURCE) || type.desc.equals(EXPLOSION),
                            "Projection must not allocate replacement radiation or explosions");
                }
            }
        }
        assertEquals(1, nativeMeltdownCalls);
    }

    private static ClassNode readClass(String internalName) throws IOException {
        String resourceName = internalName + ".class";
        try (InputStream stream = NuclearFissionProjectionOwnershipTest.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {
            assertNotNull(stream, () -> "Missing class resource " + resourceName);
            ClassNode classNode = new ClassNode();
            new ClassReader(stream).accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return classNode;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + owner.name + '.' + name));
    }

    private static long countMethodCalls(MethodNode method, String owner, String name) {
        long count = 0L;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
                count++;
            }
        }
        return count;
    }

    private static long countAllocations(MethodNode method, String typeName) {
        long count = 0L;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.NEW
                    && type.desc.equals(typeName)) {
                count++;
            }
        }
        return count;
    }
}
