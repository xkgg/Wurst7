/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.wurstclient.WurstClient;

@Mixin(Camera.class)
public abstract class CameraMixin
{
	@Shadow
	private boolean thirdPerson;
	
	@ModifyVariable(at = @At("HEAD"),
		method = "clipToSpace(D)D",
		argsOnly = true)
	private double changeClipToSpaceDistance(double desiredCameraDistance)
	{
		return WurstClient.INSTANCE.getHax().cameraDistanceHack
			.getDistance(desiredCameraDistance);
	}
	
	@Inject(at = @At("HEAD"), method = "clipToSpace(D)D", cancellable = true)
	private void onClipToSpace(double desiredCameraDistance,
		CallbackInfoReturnable<Double> cir)
	{
		if(WurstClient.INSTANCE.getHax().cameraNoClipHack.isEnabled())
			cir.setReturnValue(desiredCameraDistance);
	}
	
	@Inject(at = @At("HEAD"),
		method = "getSubmersionType()Lnet/minecraft/client/render/CameraSubmersionType;",
		cancellable = true)
	private void onGetSubmersionType(
		CallbackInfoReturnable<CameraSubmersionType> cir)
	{
		if(WurstClient.INSTANCE.getHax().noOverlayHack.isEnabled())
			cir.setReturnValue(CameraSubmersionType.NONE);
	}
	
	@Inject(at = @At("RETURN"),
		method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V")
	private void onUpdate(BlockView area, Entity focusedEntity,
		boolean thirdPerson, boolean inverseView, float tickDelta,
		CallbackInfo ci)
	{
		var freecam = WurstClient.INSTANCE.getHax().freecamHack;
		if(!freecam.isEnabled())
			return;
		
		this.thirdPerson = true;
		setPos(freecam.getCamPos(tickDelta));
		setRotation(freecam.getCamYaw(), freecam.getCamPitch());
	}
	
	@Shadow
	protected abstract void setPos(Vec3d pos);
	
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);
}
