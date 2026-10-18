/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.util.telemetry.TelemetryManager;
import net.minecraft.client.util.telemetry.TelemetrySender;
import net.wurstclient.WurstClient;

@Mixin(TelemetryManager.class)
public class TelemetryManagerMixin
{
	@ModifyReturnValue(at = @At("RETURN"),
		method = "getSender()Lnet/minecraft/client/util/telemetry/TelemetrySender;")
	private TelemetrySender onGetSender(TelemetrySender original)
	{
		return WurstClient.INSTANCE.getOtfs().noTelemetryOtf
			.wrapSender(original);
	}
}
