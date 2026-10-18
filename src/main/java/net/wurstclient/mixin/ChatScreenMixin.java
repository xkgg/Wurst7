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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ChatOutputListener.ChatOutputEvent;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen
{
	private ChatScreenMixin(WurstClient wurst, Text title)
	{
		super(title);
	}
	
	@ModifyArg(method = "init()V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;setMaxLength(I)V"),
		index = 0)
	private int modifyChatMaxLength(int original)
	{
		return WurstClient.INSTANCE.getHax().infiniChatHack.isEnabled()
			? Integer.MAX_VALUE : original;
	}
	
	@WrapOperation(method = "normalize(Ljava/lang/String;)Ljava/lang/String;",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/StringHelper;truncateChat(Ljava/lang/String;)Ljava/lang/String;"))
	private String wrapTruncateChat(String message, Operation<String> original)
	{
		if(WurstClient.INSTANCE.getHax().infiniChatHack.isEnabled()
			&& (message.startsWith("/") || message.startsWith(".")))
			return message;
		
		return original.call(message);
	}
	
	@Inject(
		at = @At(value = "INVOKE",
			target = "Ljava/lang/String;isEmpty()Z",
			shift = At.Shift.AFTER),
		method = "sendMessage(Ljava/lang/String;Z)Z",
		cancellable = true)
	private void onSendMessage(String messageDontUse, boolean addToHistory,
		CallbackInfoReturnable<Boolean> cir,
		@Local(argsOnly = true) LocalRef<String> message)
	{
		if(message.get().isEmpty())
			return;
		
		ChatOutputEvent event = new ChatOutputEvent(message.get());
		EventManager.fire(event);
		
		if(event.isCancelled())
		{
			if(addToHistory)
				client.inGameHud.getChatHud()
					.addToMessageHistory(event.getMessage());
			
			cir.setReturnValue(true);
			return;
		}
		
		message.set(event.getMessage());
	}
}
