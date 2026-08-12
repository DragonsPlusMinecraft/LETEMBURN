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

package dev.marblegate.letemburn.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class LetEmBurnConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_ENVELOPE_DEPTH;
    public static final ModConfigSpec.IntValue MAX_PAYLOAD_BYTES;
    public static final ModConfigSpec.DoubleValue DRACONIC_IMPACT_SPEED;
    public static final ModConfigSpec.DoubleValue PNEUMATIC_LEAK_SEVERITY;
    public static final ModConfigSpec.DoubleValue PNEUMATIC_RUPTURE_SEVERITY;
    public static final ModConfigSpec.DoubleValue PNEUMATIC_THRUST_SCALE;
    public static final ModConfigSpec.DoubleValue PNEUMATIC_MAX_DELTA_V_PER_SUBSTEP;
    public static final ModConfigSpec.DoubleValue BALLISTIX_IMPULSE_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue BALLISTIX_MAX_DELTA_V;
    public static final ModConfigSpec.DoubleValue BALLISTIX_OCCLUDED_FACTOR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("payload");
        MAX_ENVELOPE_DEPTH = builder
                .comment("Maximum number of nested payload envelopes that may be decoded.")
                .defineInRange("maxEnvelopeDepth", 8, 1, 64);
        MAX_PAYLOAD_BYTES = builder
                .comment("Maximum serialized payload size accepted from an envelope.")
                .defineInRange("maxSerializedBytes", 1_048_576, 1_024, 16 * 1_048_576);
        builder.pop();

        builder.push("draconicEvolution");
        DRACONIC_IMPACT_SPEED = builder
                .comment("Minimum absolute collision-normal speed needed to release a failed reactor payload.")
                .defineInRange("impactSpeedThreshold", 4.0D, 0.0D, 1_000.0D);
        builder.pop();

        builder.push("pneumaticCraft");
        PNEUMATIC_LEAK_SEVERITY = builder
                .comment("Collision severity at which a pressurized machine starts leaking.")
                .defineInRange("leakSeverityThreshold", 1.0D, 0.0D, 100.0D);
        PNEUMATIC_RUPTURE_SEVERITY = builder
                .comment("Collision severity at which a pressurized machine ruptures.")
                .defineInRange("ruptureSeverityThreshold", 2.25D, 0.0D, 100.0D);
        PNEUMATIC_THRUST_SCALE = builder
                .comment("Scale applied to real PneumaticCraft leak flow when producing Sable thrust.")
                .defineInRange("thrustScale", 5.0E-5D, 0.0D, 1.0D);
        PNEUMATIC_MAX_DELTA_V_PER_SUBSTEP = builder
                .comment("Maximum velocity change a leak may apply in one Sable physics substep.")
                .defineInRange("maxDeltaVPerSubstep", 0.5D, 0.0D, 1_000.0D);
        builder.pop();

        builder.push("ballistix");
        BALLISTIX_IMPULSE_COEFFICIENT = builder
                .comment("Base coefficient for Ballistix blast impulse on Sable bodies.")
                .defineInRange("impulseCoefficient", 1.5D, 0.0D, 1_000.0D);
        BALLISTIX_MAX_DELTA_V = builder
                .comment("Maximum velocity change from one Ballistix blast.")
                .defineInRange("maxDeltaV", 16.0D, 0.0D, 1_000.0D);
        BALLISTIX_OCCLUDED_FACTOR = builder
                .comment("Impulse multiplier when the path from a blast to a body is obstructed.")
                .defineInRange("occludedFactor", 0.35D, 0.0D, 1.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private LetEmBurnConfig() {}
}
