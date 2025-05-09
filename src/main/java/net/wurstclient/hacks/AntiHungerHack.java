/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.PacketUtils;

@DontSaveState
@SearchTags({"anti hunger"})
public final class AntiHungerHack extends Hack implements PacketOutputListener
{
	public AntiHungerHack()
	{
		super("减少饥饿");
		setCategory(Category.MOVEMENT);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().noFallHack.setEnabled(false);
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!(event.getPacket() instanceof PlayerMoveC2SPacket packet))
			return;
		
		if(!MC.player.isOnGround() || MC.player.fallDistance > 0.5)
			return;
		
		if(MC.interactionManager.isBreakingBlock())
			return;
		
		event.setPacket(PacketUtils.modifyOnGround(packet, false));
	}
}
