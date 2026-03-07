/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorItem.Type;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"自动盔甲", "auto armor"})
public final class AutoArmorHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	private final CheckboxSetting useEnchantments = new CheckboxSetting(
		"使用附魔",
		"在计算护甲强度时是否考虑保护附魔",
		true);
	
	private final CheckboxSetting swapWhileMoving = new CheckboxSetting(
		"移动时切换",
		"是否在玩家移动时切换盔甲\n\n\u00a7c\u00a7l警告: \u00a7r如果没有作弊, 这是不可能的",
		false);
	
	private final SliderSetting delay = new SliderSetting("延迟",
		"在切换下一件盔甲之前要等待的延迟.", 2,
		0, 20, 1, ValueDisplay.INTEGER);
	
	private int timer;
	
	public AutoArmorHack()
	{
		super("自动盔甲");
		setCategory(Category.COMBAT);
		addSetting(useEnchantments);
		addSetting(swapWhileMoving);
		addSetting(delay);
	}
	
	@Override
	protected void onEnable()
	{
		timer = 0;
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		// 等待延迟计时器
		if(timer > 0)
		{
			timer--;
			return;
		}
		
		// 检查当前界面：仅在物品栏或无界面时工作
		if(MC.currentScreen instanceof HandledScreen
			&& !(MC.currentScreen instanceof InventoryScreen))
			return;
		
		ClientPlayerEntity player = MC.player;
		PlayerInventory inventory = player.getInventory();
		
		// 若禁用移动时切换且玩家正在移动：跳过
		if(!swapWhileMoving.isChecked() && (player.input.movementForward != 0
			|| player.input.movementSideways != 0))
			return;
		
		// 存储最佳盔甲的槽位和价值
		int[] bestArmorSlots = new int[4];
		int[] bestArmorValues = new int[4];
		
		// 用当前装备的盔甲初始化
		for(int type = 0; type < 4; type++)
		{
			bestArmorSlots[type] = -1;
			
			ItemStack stack = inventory.getArmorStack(type);
			if(stack.isEmpty() || !(stack.getItem() instanceof ArmorItem))
				continue;
			
			ArmorItem item = (ArmorItem)stack.getItem();
			bestArmorValues[type] = getArmorValue(item, stack);
		}
		
		// 在背包中搜索更好的盔甲
		for(int slot = 0; slot < 36; slot++)
		{
			ItemStack stack = inventory.getStack(slot);
			
			if(stack.isEmpty() || !(stack.getItem() instanceof ArmorItem))
				continue;
			
			ArmorItem item = (ArmorItem)stack.getItem();
			int armorType = item.getSlotType().getEntitySlotId();
			int armorValue = getArmorValue(item, stack);
			
			if(armorValue > bestArmorValues[armorType])
			{
				bestArmorSlots[armorType] = slot;
				bestArmorValues[armorType] = armorValue;
			}
		}
		
		// 随机顺序装备更好的盔甲
		ArrayList<Integer> types = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
		Collections.shuffle(types);
		for(int type : types)
		{
			// 检查是否找到更好的盔甲
			int slot = bestArmorSlots[type];
			if(slot == -1)
				continue;
				
			// 检查是否可以切换盔甲（需要一个空槽位来放置旧盔甲）
			ItemStack oldArmor = inventory.getArmorStack(type);
			if(!oldArmor.isEmpty() && inventory.getEmptySlot() == -1)
				continue;
			
			// 快捷栏修复（将0-8映射到36-44）
			if(slot < 9)
				slot += 36;
			
			// 切换盔甲
			if(!oldArmor.isEmpty())
				IMC.getInteractionManager().windowClick_QUICK_MOVE(8 - type);
			IMC.getInteractionManager().windowClick_QUICK_MOVE(slot);
			
			break;
		}
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		// 发送点击数据包后设置延迟
		if(event.getPacket() instanceof ClickSlotC2SPacket)
			timer = delay.getValueI();
	}
	
	private int getArmorValue(ArmorItem item, ItemStack stack)
	{
		// 计算盔甲价值（护甲值、保护附魔、韧性等）
		int armorPoints = item.getProtection();
		int prtPoints = 0;
		int armorToughness = (int)item.toughness;
		int armorType = item.getMaterial().getProtection(Type.LEGGINGS);
		
		// 如果启用了附魔考虑，计算保护附魔的价值
		if(useEnchantments.isChecked())
		{
			Enchantment protection = Enchantments.PROTECTION;
			int prtLvl = EnchantmentHelper.getLevel(protection, stack);
			
			ClientPlayerEntity player = MC.player;
			DamageSource dmgSource = 
				player.getDamageSources().playerAttack(player);
			prtPoints = protection.getProtectionAmount(prtLvl, dmgSource);
		}
		
		// 综合计算盔甲价值
		return armorPoints * 5 + prtPoints * 3 + armorToughness + armorType;
	}
}
