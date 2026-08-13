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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class NuclearPlasmaProjectionOwnershipTest {
    private static final String NATIVE_PLASMA = "nuclearscience/common/tile/reactor/fusion/TilePlasma";
    private static final String PROJECTION = "dev/marblegate/letemburn/integration/nuclearscience/NuclearPlasmaProjection";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String SERVER_LEVEL = "net/minecraft/server/level/ServerLevel";
    private static final String TAGS = "nuclearscience/common/tags/NuclearScienceTags$Blocks";
    private static final String STEAM_RECEIVER = "nuclearscience/api/turbine/ISteamReceiver";

    @Test
    void nativePlasmaRetainsSpreadLifetimeProtectionAndSteamSemantics() throws IOException {
        ClassNode nativePlasma = readClass(NATIVE_PLASMA);
        MethodNode constructor = method(nativePlasma, "<init>");
        MethodNode tickServer = method(nativePlasma, "tickServer");

        assertTrue(containsIntOperand(constructor, 6), "Native plasma no longer starts with six spread steps");
        assertTrue(containsIntOperand(tickServer, 80), "Native plasma no longer owns its 80-tick lifetime");
        assertEquals(2, countMethodCalls(tickServer, LEVEL, "setBlockAndUpdate"));
        assertEquals(1, countMethodCalls(tickServer, STEAM_RECEIVER, "receiveSteam"));
        assertTrue(containsFieldRead(tickServer, TAGS, "FUSION_CONTAINMENT"));
    }

    @Test
    void projectionCreatesOnlyANativeSeedAndDoesNotCopySteamLogic() throws IOException {
        ClassNode projection = readClass(PROJECTION);
        long setBlockCalls = 0L;
        for (MethodNode method : projection.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if ((call.owner.equals(LEVEL) || call.owner.equals(SERVER_LEVEL))
                            && call.name.equals("setBlockAndUpdate")) {
                        setBlockCalls++;
                    }
                    assertFalse(
                            call.owner.equals(STEAM_RECEIVER),
                            "Projection must leave steam generation to native TilePlasma ticking");
                }
            }
        }
        assertEquals(1, setBlockCalls, "Projection should only place the one native parent seed");
    }

    private static ClassNode readClass(String internalName) throws IOException {
        String resourceName = internalName + ".class";
        try (InputStream stream = NuclearPlasmaProjectionOwnershipTest.class
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

    private static boolean containsIntOperand(MethodNode method, int value) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof IntInsnNode operand
                    && (operand.getOpcode() == Opcodes.BIPUSH || operand.getOpcode() == Opcodes.SIPUSH)
                    && operand.operand == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsFieldRead(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(owner)
                    && field.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
