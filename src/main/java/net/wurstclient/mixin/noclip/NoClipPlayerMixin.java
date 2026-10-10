/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin.noclip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.network.ClientPlayerEntity;
import net.wurstclient.WurstClient;

@Mixin(ClientPlayerEntity.class)
public abstract class NoClipPlayerMixin
{
	/**
	 * Controls whether vanilla sends a player position packet. NoClip sends
	 * the packet itself after finding a valid destination.
	 */
	@ModifyExpressionValue(method = "sendMovementPackets()V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/ClientPlayerEntity;isCamera()Z"))
	private boolean wurst$shouldSendPosition(boolean original)
	{
		return original
			&& WurstClient.INSTANCE.getHax().noClipHack.shouldSendPosition();
	}
	
	@Inject(method = "pushOutOfBlocks(DD)V",
		at = @At("HEAD"),
		cancellable = true)
	private void wurst$preventPushOutOfBlocks(double x, double z,
		CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax().noClipHack.isEnabled())
			ci.cancel();
	}
}
