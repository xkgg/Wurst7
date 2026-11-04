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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.AirStrafingSpeedListener.AirStrafingSpeedEvent;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin
{
	@ModifyReturnValue(at = @At("RETURN"), method = "getOffGroundSpeed()F")
	private float modifyOffGroundSpeed(float original)
	{
		if((Object)this != WurstClient.MC.player)
			return original;
		
		AirStrafingSpeedEvent event = new AirStrafingSpeedEvent(original);
		EventManager.fire(event);
		return event.getSpeed();
	}
	
	@ModifyReturnValue(at = @At("RETURN"), method = "clipAtLedge()Z")
	private boolean modifyClipAtLedge(boolean original)
	{
		if((Object)this != WurstClient.MC.player)
			return original;
		
		return original
			|| WurstClient.INSTANCE.getHax().safeWalkHack.isEnabled();
	}
	
	@WrapMethod(
		method = "adjustMovementForSneaking(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/MovementType;)Lnet/minecraft/util/math/Vec3d;")
	private Vec3d wrapAdjustMovementForSneaking(Vec3d movement,
		MovementType type, Operation<Vec3d> original)
	{
		Vec3d result = original.call(movement, type);
		if((Object)this == WurstClient.MC.player && movement != null)
			WurstClient.INSTANCE.getHax().safeWalkHack
				.onClipAtLedge(!movement.equals(result));
		
		return result;
	}
}
