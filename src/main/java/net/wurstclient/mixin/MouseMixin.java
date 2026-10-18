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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Mouse;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.MouseButtonPressListener.MouseButtonPressEvent;
import net.wurstclient.events.MouseScrollListener.MouseScrollEvent;
import net.wurstclient.events.MouseUpdateListener.MouseUpdateEvent;

@Mixin(Mouse.class)
public class MouseMixin
{
	@Shadow
	private double cursorDeltaX;
	@Shadow
	private double cursorDeltaY;
	
	@ModifyExpressionValue(method = "updateMouse()V",
		at = @At(value = "INVOKE", target = "Ljava/lang/Double;doubleValue()D"))
	private double onGetSensitivity(double original)
	{
		return WurstClient.INSTANCE.getOtfs().zoomOtf
			.changeMouseSensitivityBasedOnZoom(original);
	}
	
	@Inject(at = @At("HEAD"), method = "onMouseButton(JIII)V")
	private void onOnMouseButton(long window, int button, int action, int mods,
		CallbackInfo ci)
	{
		EventManager.fire(new MouseButtonPressEvent(button, action));
	}
	
	@Inject(at = @At("RETURN"), method = "onMouseScroll(JDD)V")
	private void onOnMouseScroll(long window, double horizontal,
		double vertical, CallbackInfo ci)
	{
		EventManager.fire(new MouseScrollEvent(vertical));
	}
	
	@Inject(at = @At("HEAD"), method = "updateMouse()V")
	private void onUpdateMouse(CallbackInfo ci)
	{
		MouseUpdateEvent event =
			new MouseUpdateEvent(cursorDeltaX, cursorDeltaY);
		EventManager.fire(event);
		cursorDeltaX = event.getDeltaX();
		cursorDeltaY = event.getDeltaY();
	}
}
