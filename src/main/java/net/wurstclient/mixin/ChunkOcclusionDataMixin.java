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

import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.Direction;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.VisGraphListener.VisGraphEvent;

@Mixin(ChunkOcclusionData.class)
public class ChunkOcclusionDataMixin
{
	@Inject(at = @At("HEAD"),
		method = "isVisibleThrough(Lnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/Direction;)Z",
		cancellable = true)
	private void onIsVisibleThrough(Direction from, Direction to,
		CallbackInfoReturnable<Boolean> cir)
	{
		VisGraphEvent event = new VisGraphEvent();
		EventManager.fire(event);
		
		if(event.isCancelled())
			cir.setReturnValue(true);
	}
}
