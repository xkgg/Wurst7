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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.wurstclient.WurstClient;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin
{
	@Inject(at = {@At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V",
		ordinal = 4)}, method = "renderFirstPersonItem")
	private void onApplyEquipOffsetBlocking(AbstractClientPlayerEntity player,
		float tickDelta, float pitch, Hand hand, float swingProgress,
		ItemStack item, float equipProgress, MatrixStack matrices,
		VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
	{
		// lower shield when blocking
		if(item.getUseAction() == UseAction.BLOCK)
			WurstClient.INSTANCE.getHax().noShieldOverlayHack
				.adjustShieldPosition(matrices, true);
	}
	
	@Inject(at = {@At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V",
		ordinal = 1)}, method = "renderFirstPersonItem")
	private void onApplySwingOffsetNotBlocking(
		AbstractClientPlayerEntity player, float tickDelta, float pitch,
		Hand hand, float swingProgress, ItemStack item, float equipProgress,
		MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
		CallbackInfo ci)
	{
		// lower shield when not blocking
		if(item.getUseAction() == UseAction.BLOCK)
			WurstClient.INSTANCE.getHax().noShieldOverlayHack
				.adjustShieldPosition(matrices, false);
	}
	
	@Inject(at = @At("HEAD"),
		method = "renderFirstPersonItem",
		cancellable = true)
	private void onRenderFirstPersonItem(AbstractClientPlayerEntity player,
		float tickDelta, float pitch, Hand hand, float swingProgress,
		ItemStack item, float equipProgress, MatrixStack matrices,
		VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
	{
		var hax = WurstClient.INSTANCE.getHax();
		if(hax.freecamHack.shouldHideHand()
			|| hax.remoteViewHack.shouldHideHand())
			ci.cancel();
	}
}
