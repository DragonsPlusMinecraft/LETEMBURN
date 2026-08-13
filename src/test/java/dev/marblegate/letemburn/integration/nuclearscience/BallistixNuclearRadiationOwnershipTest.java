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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class BallistixNuclearRadiationOwnershipTest {
    private static final String NUCLEAR_BLAST = "ballistix/common/blast/tier3/BlastNuclear";
    private static final String RADIATION_SYSTEM = "voltaic/api/radiation/RadiationSystem";
    private static final String RADIATION_SOURCE = "voltaic/api/radiation/SimpleRadiationSource";
    private static final String NATIVE_NS_HANDLER = "ballistix/compatibility/nuclearscience/RadiationHandler";

    @Test
    void nativeBlastOwnsTheOnlyRadiationSourceAndIrradiationCallSites() throws IOException {
        MethodNode doExplode = method(readClass(NUCLEAR_BLAST), "doExplode", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.INT_TYPE));

        assertEquals(1, countMethodCalls(doExplode, RADIATION_SYSTEM, "addRadiationSource"));
        assertEquals(1, countAllocations(doExplode, RADIATION_SOURCE));
        assertEquals(1, countMethodCalls(doExplode, NATIVE_NS_HANDLER, "addNuclearExplosiveIrradidatedBlock"));
    }

    @Test
    void letEmBurnBridgeDoesNotReferenceRadiationCreationApis() throws IOException {
        List<String> bridgeClasses = List.of(
                "dev/marblegate/letemburn/integration/ballistix/BallistixCompatibilityHooks",
                "dev/marblegate/letemburn/integration/ballistix/BallistixImpactPayloadAdapter",
                "dev/marblegate/letemburn/mixin/ballistix/BlastMixin");

        for (String className : bridgeClasses) {
            ClassNode classNode = readClass(className);
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        assertFalse(
                                call.owner.equals(RADIATION_SYSTEM) || call.owner.equals(NATIVE_NS_HANDLER),
                                () -> className + " must leave radiation ownership to the native Ballistix/NS chain");
                    }
                    if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                        assertFalse(
                                type.desc.equals(RADIATION_SOURCE),
                                () -> className + " must not allocate a second Voltaic radiation source");
                    }
                }
            }
        }
    }

    private static ClassNode readClass(String internalName) throws IOException {
        String resourceName = internalName + ".class";
        try (InputStream stream = BallistixNuclearRadiationOwnershipTest.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {
            assertNotNull(stream, () -> "Missing class resource " + resourceName);
            ClassNode classNode = new ClassNode();
            new ClassReader(stream).accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return classNode;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + owner.name + '.' + name + descriptor));
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
