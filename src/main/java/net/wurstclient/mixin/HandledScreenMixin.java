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

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.AutoStealHack;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen
{
	@Shadow
	protected int backgroundWidth;
	@Shadow
	protected int x;
	@Shadow
	protected int y;
	
	private HandledScreenMixin(WurstClient wurst, Text title)
	{
		super(title);
	}
	
	@Inject(at = @At("TAIL"), method = "init()V")
	private void onInit(CallbackInfo ci)
	{
		HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
		int rows;
		if(screen instanceof GenericContainerScreen chest)
			rows = chest.getScreenHandler().getRows();
		else if(screen instanceof ShulkerBoxScreen)
			rows = 3;
		else
			return;
		
		if(!WurstClient.INSTANCE.isEnabled())
			return;
		
		AutoStealHack autoSteal = WurstClient.INSTANCE.getHax().autoStealHack;
		if(autoSteal.areButtonsVisible())
		{
			addDrawableChild(ButtonWidget
				.builder(Text.literal("Steal"),
					b -> autoSteal.steal(screen, rows))
				.dimensions(x + backgroundWidth - 108, y + 4, 50, 12).build());
			
			addDrawableChild(ButtonWidget
				.builder(Text.literal("Store"),
					b -> autoSteal.store(screen, rows))
				.dimensions(x + backgroundWidth - 56, y + 4, 50, 12).build());
		}
		
		if(autoSteal.isEnabled())
			autoSteal.steal(screen, rows);
	}
}
