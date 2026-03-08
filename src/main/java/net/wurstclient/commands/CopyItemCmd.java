/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.CmdUtils;

public final class CopyItemCmd extends Command
{
	public CopyItemCmd()
	{
		super("copyitem",
			"允许你复制其他人手持或穿戴的物品\n" + "需要创造模式。",
			".copyitem <玩家> <槽位>",
			"有效的槽位: hand, head, chest, legs, feet");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length != 2)
			throw new CmdSyntaxError();
		
		if(!MC.player.getAbilities().creativeMode)
			throw new CmdError("仅创造模式可用。");
		
		AbstractClientPlayerEntity player = getPlayer(args[0]);
		ItemStack item = getItem(player, args[1]);
		CmdUtils.giveItem(item);
		
		ChatUtils.message("物品已复制。");
	}
	
	private AbstractClientPlayerEntity getPlayer(String name) throws CmdError
	{
		for(AbstractClientPlayerEntity player : MC.world.getPlayers())
		{
			if(!player.getEntityName().equalsIgnoreCase(name))
				continue;
			
			return player;
		}
		
		throw new CmdError("找不到玩家 \"" + name + "\"。");
	}
	
	private ItemStack getItem(AbstractClientPlayerEntity player, String slot)
		throws CmdSyntaxError
	{
		switch(slot.toLowerCase())
		{
			case "hand":
			return player.getMainHandStack();
			
			case "head":
			return player.getEquippedStack(EquipmentSlot.HEAD);
			
			case "chest":
			return player.getEquippedStack(EquipmentSlot.CHEST);
			
			case "legs":
			return player.getEquippedStack(EquipmentSlot.LEGS);
			
			case "feet":
			return player.getEquippedStack(EquipmentSlot.FEET);
			
			default:
			throw new CmdSyntaxError();
		}
	}
}
