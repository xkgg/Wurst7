/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ItemListSetting;

@SearchTags({"自动丢弃", "auto drop", "AutoEject", "auto-eject", "auto eject",
	"InventoryCleaner", "inventory cleaner", "InvCleaner", "inv cleaner"})
public final class AutoDropHack extends Hack implements UpdateListener
{
private ItemListSetting items = new ItemListSetting("清理物品列表",
		"将会被丢弃的不需要的物品。", "minecraft:allium",
		"minecraft:azure_bluet", "minecraft:blue_orchid",
		"minecraft:cornflower", "minecraft:dandelion", "minecraft:lilac",
		"minecraft:lily_of_the_valley", "minecraft:orange_tulip",
		"minecraft:oxeye_daisy", "minecraft:peony", "minecraft:pink_tulip",
		"minecraft:poisonous_potato", "minecraft:poppy", "minecraft:red_tulip",
		"minecraft:rose_bush", "minecraft:rotten_flesh", "minecraft:sunflower",
		"minecraft:wheat_seeds", "minecraft:white_tulip");
	
	// 1%的概率将名称显示为"AutoLinus"
	private final String renderName =
		Math.random() < 0.01 ? "AutoLinus" : getName();
	
	public AutoDropHack()
	{
		super("自动丢弃");
		setCategory(Category.ITEMS);
		addSetting(items);
	}
	
	@Override
	public String getRenderName()
	{
		return renderName;
	}
	
	@Override
	protected void onEnable()
	{
		// 注册更新事件监听器
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销更新事件监听器
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		// 检查当前界面：仅在物品栏或无界面时工作
		if(MC.currentScreen instanceof HandledScreen
			&& !(MC.currentScreen instanceof InventoryScreen))
			return;
		
		// 遍历背包槽位（9-44）
		for(int slot = 9; slot < 45; slot++)
		{
			// 调整槽位索引（将36-44映射到0-8）
			int adjustedSlot = slot;
			if(adjustedSlot >= 36)
				adjustedSlot -= 36;
			
			// 获取物品栈
			ItemStack stack = MC.player.getInventory().getStack(adjustedSlot);
			
			// 跳过空物品槽
			if(stack.isEmpty())
				continue;
			
			// 获取物品ID
			Item item = stack.getItem();
			String itemName = Registries.ITEM.getId(item).toString();
			
			// 跳过不在清理列表中的物品
			if(!items.getItemNames().contains(itemName))
				continue;
			
			// 执行丢弃操作
			IMC.getInteractionManager().windowClick_THROW(slot);
		}
	}
}
