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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.RemoteViewHack;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin
{
	private static final String RENDER_METHOD =
		"render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V";
	
	@WrapOperation(method = RENDER_METHOD,
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"))
	private boolean wurst$keepLocalPlayerInFrustum(
		EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum,
		double x, double y, double z, Operation<Boolean> original)
	{
		RemoteViewHack remoteView =
			WurstClient.INSTANCE.getHax().remoteViewHack;
		if(WurstClient.MC.player != null && entity == WurstClient.MC.player
			&& remoteView.isViewingEntity()
			&& !WurstClient.MC.player.isSpectator())
			return true;
		
		return original.call(dispatcher, entity, frustum, x, y, z);
	}
	
	@WrapOperation(method = RENDER_METHOD,
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;",
			ordinal = 3))
	private Entity wurst$keepLocalPlayerVisible(Camera camera,
		Operation<Entity> original)
	{
		RemoteViewHack remoteView =
			WurstClient.INSTANCE.getHax().remoteViewHack;
		if(remoteView.isViewingEntity() && WurstClient.MC.player != null
			&& !WurstClient.MC.player.isSpectator())
			return WurstClient.MC.player;
		
		return original.call(camera);
	}
	
	@Inject(at = @At("HEAD"),
		method = "hasBlindnessOrDarkness(Lnet/minecraft/client/render/Camera;)Z",
		cancellable = true)
	private void onHasBlindnessOrDarkness(Camera camera,
		CallbackInfoReturnable<Boolean> ci)
	{
		if(WurstClient.INSTANCE.getHax().antiBlindHack.isEnabled())
			ci.setReturnValue(false);
	}
}
