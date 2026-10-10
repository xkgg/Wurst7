/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.PacketOutputListener.PacketOutputEvent;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.hack.Hack;

@SearchTags({"雪上行走", "snow shoe", "SnowJesus", "snow jesus", "NoSnowSink",
	"no snow sink", "AntiSnowSink", "anti snow sink"})
public final class SnowShoeHack extends Hack
	implements PreMotionListener, PostMotionListener, PacketOutputListener
{
	private boolean sentMovement;
	
	public SnowShoeHack()
	{
		super("雪上行走");
		setCategory(Category.MOVEMENT);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PreMotionListener.class, this);
		EVENTS.add(PostMotionListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(PostMotionListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		sentMovement = false;
	}
	
	@Override
	public void onPreMotion()
	{
		sentMovement = false;
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof PlayerMoveC2SPacket)
			sentMovement = true;
	}
	
	@Override
	public void onPostMotion()
	{
		if(sentMovement)
			return;
		
		ClientPlayerEntity player = MC.player;
		if(!player.isOnGround()
			|| !player.getBlockStateAtPos().isOf(Blocks.POWDER_SNOW))
			return;
		
		player.networkHandler
			.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
	}
	
	// See PowderSnowBlockMixin
}
