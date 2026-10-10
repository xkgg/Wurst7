/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin.freecam;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.FreecamHack;
import net.wurstclient.hacks.RemoteViewHack;

@Mixin(GameRenderer.class)
public abstract class FreecamGameRendererMixin
{
	@WrapOperation(method = "updateTargetedEntity(F)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/MinecraftClient;getCameraEntity()Lnet/minecraft/entity/Entity;",
			ordinal = 0))
	private Entity wurst$chooseInteractionSource(MinecraftClient client,
		Operation<Entity> original)
	{
		RemoteViewHack remoteView =
			WurstClient.INSTANCE.getHax().remoteViewHack;
		if(remoteView.isClickingFromPlayer())
			return WurstClient.MC.player;
		
		return original.call(client);
	}
	
	@WrapOperation(method = "updateTargetedEntity(F)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;"))
	private HitResult wurst$raycastFromCamera(Entity entity, double maxDistance,
		float tickDelta, boolean includeFluids, Operation<HitResult> original)
	{
		FreecamHack freecam = WurstClient.INSTANCE.getHax().freecamHack;
		if(freecam.isClickingFromCamera())
		{
			Vec3d start = freecam.getCamPos(tickDelta);
			Vec3d end = start.add(freecam.getScaledCamDir(maxDistance));
			RaycastContext.FluidHandling fluidHandling =
				includeFluids ? RaycastContext.FluidHandling.ANY
					: RaycastContext.FluidHandling.NONE;
			return entity.getWorld().raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.OUTLINE, fluidHandling, entity));
		}
		
		return original.call(entity, maxDistance, tickDelta, includeFluids);
	}
	
	@WrapOperation(method = "updateTargetedEntity(F)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;getCameraPosVec(F)Lnet/minecraft/util/math/Vec3d;"))
	private Vec3d wurst$getCameraPosVec(Entity entity, float tickDelta,
		Operation<Vec3d> original)
	{
		FreecamHack freecam = WurstClient.INSTANCE.getHax().freecamHack;
		if(freecam.isClickingFromCamera())
			return freecam.getCamPos(tickDelta);
		
		return original.call(entity, tickDelta);
	}
	
	@WrapOperation(method = "updateTargetedEntity(F)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;getRotationVec(F)Lnet/minecraft/util/math/Vec3d;"))
	private Vec3d wurst$getRotationVec(Entity entity, float tickDelta,
		Operation<Vec3d> original)
	{
		FreecamHack freecam = WurstClient.INSTANCE.getHax().freecamHack;
		if(freecam.isClickingFromCamera())
			return freecam.getScaledCamDir(1);
		
		return original.call(entity, tickDelta);
	}
	
	@WrapOperation(method = "updateTargetedEntity(F)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/projectile/ProjectileUtil;raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;"))
	private EntityHitResult wurst$raycastEntitiesFromCamera(Entity entity,
		Vec3d start, Vec3d end, Box bounds, Predicate<Entity> predicate,
		double maxDistanceSq, Operation<EntityHitResult> original)
	{
		FreecamHack freecam = WurstClient.INSTANCE.getHax().freecamHack;
		if(freecam.isClickingFromCamera())
		{
			Vec3d entityCameraPos = entity.getCameraPosVec(1);
			Box cameraBounds = bounds.offset(start.subtract(entityCameraPos));
			return original.call(entity, start, end, cameraBounds, predicate,
				maxDistanceSq);
		}
		
		RemoteViewHack remoteView =
			WurstClient.INSTANCE.getHax().remoteViewHack;
		if(remoteView.isViewingEntity() && !remoteView.isClickingFromPlayer())
			predicate =
				predicate.and(candidate -> candidate != WurstClient.MC.player);
		
		return original.call(entity, start, end, bounds, predicate,
			maxDistanceSq);
	}
}
