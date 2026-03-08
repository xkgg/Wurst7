/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"恼人药水", "troll potion", "TrollingPotion", "trolling potion"})
public final class TrollPotionHack extends Hack
{
	private final EnumSetting<PotionType> potionType =
		new EnumSetting<>("药水类型", "要生成的药水类型。",
			PotionType.values(), PotionType.SPLASH);
	
	public TrollPotionHack()
	{
		super("恼人药水");
		setCategory(Category.ITEMS);
		addSetting(potionType);
	}
	
	@Override
	protected void onEnable()
	{
		// check gamemode
		if(!MC.player.getAbilities().creativeMode)
		{
			ChatUtils.error("仅限创造模式.");
			setEnabled(false);
			return;
		}
		
		// generate potion
		ItemStack stack = potionType.getSelected().createPotionStack();
		
		// give potion
		PlayerInventory inventory = MC.player.getInventory();
		int slot = inventory.getEmptySlot();
		if(slot < 0)
			ChatUtils.error("不能给药水, 您的背包已满！");
		else
		{
			InventoryUtils.setCreativeStack(slot, stack);
			ChatUtils.message("已创建药水");
		}
		
		setEnabled(false);
	}
	
	private enum PotionType
	{
		NORMAL("普通", "药水", Items.POTION),
		
		SPLASH("喷溅", "喷溅药水", Items.SPLASH_POTION),
		
		LINGERING("滞留", "滞留药水", Items.LINGERING_POTION),
		
		ARROW("箭", "箭", Items.TIPPED_ARROW);
		
		private final String name;
		private final String itemName;
		private final Item item;
		
		private PotionType(String name, String itemName, Item item)
		{
			this.name = name;
			this.itemName = itemName;
			this.item = item;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
		
		public ItemStack createPotionStack()
		{
			ItemStack stack = new ItemStack(item);
			
			NbtList effects = new NbtList();
			for(int i = 1; i <= 23; i++)
			{
				NbtCompound effect = new NbtCompound();
				effect.putInt("Amplifier", Integer.MAX_VALUE);
				effect.putInt("Duration", Integer.MAX_VALUE);
				effect.putInt("Id", i);
				effects.add(effect);
			}
			
			NbtCompound nbt = new NbtCompound();
			nbt.put("CustomPotionEffects", effects);
			stack.setNbt(nbt);
			
			String name = "\u00a7f" + itemName + " 恼人药水";
			stack.setCustomName(Text.literal(name));
			
			return stack;
		}
	}
}
