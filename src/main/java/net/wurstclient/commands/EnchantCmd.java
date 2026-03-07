/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.ItemUtils;

public final class EnchantCmd extends Command
{
	public EnchantCmd()
	{
		super("enchant", "给物品附魔所有效果，\n" + "除了精准采集和诅咒。", ".enchant");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(!MC.player.getAbilities().creativeMode)
			throw new CmdError("仅创造模式可用。");
		
		if(args.length > 1)
			throw new CmdSyntaxError();
		
		enchant(getHeldItem(), 127);
		ChatUtils.message("物品已附魔。");
	}
	
	private ItemStack getHeldItem() throws CmdError
	{
		ItemStack stack = MC.player.getMainHandStack();
		
		if(stack.isEmpty())
			stack = MC.player.getOffHandStack();
		
		if(stack.isEmpty())
			throw new CmdError("你手中没有物品。");
		
		return stack;
	}
	
	private void enchant(ItemStack stack, int level)
	{
		for(Enchantment enchantment : Registries.ENCHANTMENT)
		{
			// Skip curses
			if(enchantment.isCursed())
				continue;
			
			// Skip Silk Touch so it doesn't remove Fortune
			if(enchantment == Enchantments.SILK_TOUCH)
				continue;
			
			// Limit Quick Charge to level 5 so it doesn't break
			if(enchantment == Enchantments.QUICK_CHARGE)
			{
				stack.addEnchantment(enchantment, Math.min(level, 5));
				continue;
			}
			
			ItemUtils.addEnchantment(stack, enchantment, level);
		}
	}
	
	@Override
	public String getPrimaryAction()
	{
		return "附魔手持物品";
	}
	
	@Override
	public void doPrimaryAction()
	{
		WURST.getCmdProcessor().process("enchant");
	}
}
