/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Pair;

import net.minecraft.block.Block;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.FoodComponents;
import net.minecraft.item.Item;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"自动进食", "auto eat", "AutoFood", "auto food", "AutoFeeder", "auto feeder",
	"AutoFeeding", "auto feeding", "AutoSoup", "auto soup"})
public final class AutoEatHack extends Hack implements UpdateListener
{
	// 目标饥饿值（达到该值后停止进食）
	private final SliderSetting targetHunger = new SliderSetting(
		"目标饥饿值", "description.wurst.setting.autoeat.target_hunger", 10,
		0, 10, 0.5, ValueDisplay.DECIMAL);
	
	// 最小饥饿值（低于该值时强制进食）
	private final SliderSetting minHunger = new SliderSetting("最小饥饿值",
		"description.wurst.setting.autoeat.min_hunger", 6.5, 0, 10, 0.5,
		ValueDisplay.DECIMAL);
	
	// 受伤时饥饿阈值（受伤状态下触发进食的饥饿值）
	private final SliderSetting injuredHunger = new SliderSetting(
		"受伤触发饥饿值", "description.wurst.setting.autoeat.injured_hunger",
		10, 0, 10, 0.5, ValueDisplay.DECIMAL);
	
	// 受伤阈值（判定为"受伤"的生命值差值）
	private final SliderSetting injuryThreshold =
		new SliderSetting("受伤判定阈值",
			"description.wurst.setting.autoeat.injury_threshold", 1.5, 0.5, 10,
			0.5, ValueDisplay.DECIMAL);
	
	// 物品拿取来源（仅手部/快捷栏/全背包）
	private final EnumSetting<TakeItemsFrom> takeItemsFrom = new EnumSetting<>(
		"物品拿取来源", "description.wurst.setting.autoeat.take_items_from",
		TakeItemsFrom.values(), TakeItemsFrom.HOTBAR);
	
	// 允许使用副手物品进食
	private final CheckboxSetting allowOffhand =
		new CheckboxSetting("允许副手进食", true);
	
	// 行走时是否允许进食
	private final CheckboxSetting eatWhileWalking =
		new CheckboxSetting("边走边吃",
			"description.wurst.setting.autoeat.eat_while_walking", false);
	
	// 允许食用带有饥饿效果的食物
	private final CheckboxSetting allowHunger =
		new CheckboxSetting("允许饥饿效果食物",
			"description.wurst.setting.autoeat.allow_hunger", true);
	
	// 允许食用带有中毒效果的食物
	private final CheckboxSetting allowPoison =
		new CheckboxSetting("允许中毒效果食物",
			"description.wurst.setting.autoeat.allow_poison", false);
	
	// 允许食用紫颂果
	private final CheckboxSetting allowChorus =
		new CheckboxSetting("允许食用紫颂果",
			"description.wurst.setting.autoeat.allow_chorus", false);
	
	// 记录切换进食前的原快捷栏槽位
	private int oldSlot = -1;
	
	public AutoEatHack()
	{
		super("自动进食");
		setCategory(Category.ITEMS);
		
		addSetting(targetHunger);
		addSetting(minHunger);
		addSetting(injuredHunger);
		addSetting(injuryThreshold);
		
		addSetting(takeItemsFrom);
		addSetting(allowOffhand);
		
		addSetting(eatWhileWalking);
		addSetting(allowHunger);
		addSetting(allowPoison);
		addSetting(allowChorus);
	}
	
	@Override
	protected void onEnable()
	{
		// 禁用自动喝汤功能（避免冲突）
		WURST.getHax().autoSoupHack.setEnabled(false);
		// 注册更新事件监听器
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销更新事件监听器
		EVENTS.remove(UpdateListener.class, this);
		
		// 若正在进食，停止进食并恢复原槽位
		if(isEating())
			stopEating();
	}
	
	@Override
	public void onUpdate()
	{
		ClientPlayerEntity player = MC.player;
		
		// 检查是否需要进食
		if(!shouldEat())
		{
			if(isEating())
				stopEating();
			return;
		}
		
		// 获取玩家饥饿管理器
		HungerManager hungerManager = player.getHungerManager();
		int foodLevel = hungerManager.getFoodLevel();
		// 转换为实际饥饿值（配置值×2，MC中饥饿值范围0-20）
		int targetHungerI = (int)(targetHunger.getValue() * 2);
		int minHungerI = (int)(minHunger.getValue() * 2);
		int injuredHungerI = (int)(injuredHunger.getValue() * 2);
		
		// 受伤状态且饥饿值低于受伤触发阈值：强制进食
		if(isInjured(player) && foodLevel < injuredHungerI)
		{
			eat(-1);
			return;
		}
		
		// 饥饿值低于最小阈值：强制进食
		if(foodLevel < minHungerI)
		{
			eat(-1);
			return;
		}
		
		// 饥饿值低于目标阈值：按需进食（仅补充到目标值）
		if(foodLevel < targetHungerI)
		{
			int maxPoints = targetHungerI - foodLevel;
			eat(maxPoints);
		}
	}
	
	/**
	 * 执行进食操作
	 * @param maxPoints 最大补充饥饿值（-1表示无限制）
	 */
	private void eat(int maxPoints)
	{
		PlayerInventory inventory = MC.player.getInventory();
		// 查找最优食物槽位
		int foodSlot = findBestFoodSlot(maxPoints);
		
		// 无可用食物：停止进食
		if(foodSlot == -1)
		{
			if(isEating())
				stopEating();
			return;
		}
		
		// 选择食物槽位
		if(foodSlot < 9)
		{
			// 快捷栏槽位：记录原槽位并切换
			if(!isEating())
				oldSlot = inventory.selectedSlot;
			inventory.selectedSlot = foodSlot;
			
		}else if(foodSlot == 40)
		{
			// 副手槽位：无需切换槽位，记录原槽位即可
			if(!isEating())
				oldSlot = inventory.selectedSlot;
			
		}else
		{
			// 背包槽位：自动选中该物品
			InventoryUtils.selectItem(foodSlot);
			return;
		}
		
		// 执行进食操作
		MC.options.useKey.setPressed(true);
		IMC.getInteractionManager().rightClickItem();
	}
	
	/**
	 * 查找最优食物槽位（按饱和度排序，优先选高饱和度食物）
	 * @param maxPoints 最大补充饥饿值（-1表示无限制）
	 * @return 最优食物槽位（-1表示无可用食物）
	 */
	private int findBestFoodSlot(int maxPoints)
	{
		PlayerInventory inventory = MC.player.getInventory();
		FoodComponent bestFood = null;
		int bestSlot = -1;
		
		// 获取物品拿取来源的最大槽位
		int maxInvSlot = takeItemsFrom.getSelected().maxInvSlot;
		
		// 构建待检查的槽位列表
		ArrayList<Integer> slots = new ArrayList<>();
		if(maxInvSlot == 0)
			// 仅检查当前手持槽位
			slots.add(inventory.selectedSlot);
		if(allowOffhand.isChecked())
			// 包含副手槽位
			slots.add(40);
		// 遍历指定范围的槽位
		Stream.iterate(0, i -> i < maxInvSlot, i -> i + 1)
			.forEach(i -> slots.add(i));
		
		// 按饱和度降序排序（优先选高饱和度食物）
		Comparator<FoodComponent> comparator =
			Comparator.comparingDouble(FoodComponent::getSaturationModifier);
		
		for(int slot : slots)
		{
			Item item = inventory.getStack(slot).getItem();
			
			// 过滤非食物类物品
			if(!item.isFood())
				continue;
			
			FoodComponent food = item.getFoodComponent();
			// 过滤不被允许的食物（如禁用的紫颂果、带中毒效果的食物）
			if(!isAllowedFood(food))
				continue;
			
			// 按需过滤：仅补充到目标值时，跳过超过所需饥饿值的食物
			if(maxPoints >= 0 && food.getHunger() > maxPoints)
				continue;
			
			// 对比并选择最优食物
			if(bestFood == null || comparator.compare(food, bestFood) > 0)
			{
				bestFood = food;
				bestSlot = slot;
			}
		}
		
		return bestSlot;
	}
	
	/**
	 * 判断是否满足进食条件
	 * @return 是否需要进食
	 */
	private boolean shouldEat()
	{
		// 创造模式：无需进食
		if(MC.player.getAbilities().creativeMode)
			return false;
		
		// 玩家无法进食（如正在攻击/施法）
		if(!MC.player.canConsume(false))
			return false;
		
		// 未开启边走边吃，但玩家正在移动
		if(!eatWhileWalking.isChecked()
			&& (MC.player.forwardSpeed != 0 || MC.player.sidewaysSpeed != 0))
			return false;
		
		// 准星指向可交互目标（村民/驯服生物/容器/工作台）：暂停进食
		if(isClickable(MC.crosshairTarget))
			return false;
		
		return true;
	}
	
	/**
	 * 停止进食并恢复原快捷栏槽位
	 */
	private void stopEating()
	{
		// 释放使用键
		MC.options.useKey.setPressed(false);
		// 恢复原槽位
		MC.player.getInventory().selectedSlot = oldSlot;
		// 重置原槽位标记
		oldSlot = -1;
	}
	
	/**
	 * 判断食物是否被允许食用（过滤禁用的效果/物品）
	 * @param food 食物组件
	 * @return 是否允许食用
	 */
	private boolean isAllowedFood(FoodComponent food)
	{
		// 禁用紫颂果时过滤
		if(!allowChorus.isChecked() && food == FoodComponents.CHORUS_FRUIT)
			return false;
		
		// 检查食物附带的状态效果
		for(Pair<StatusEffectInstance, Float> pair : food.getStatusEffects())
		{
			StatusEffect effect = pair.getFirst().getEffectType();
			
			// 禁用饥饿效果时过滤
			if(!allowHunger.isChecked() && effect == StatusEffects.HUNGER)
				return false;
			
			// 禁用中毒效果时过滤
			if(!allowPoison.isChecked() && effect == StatusEffects.POISON)
				return false;
		}
		
		return true;
	}
	
	/**
	 * 判断是否正在进食
	 * @return 进食状态
	 */
	public boolean isEating()
	{
		return oldSlot != -1;
	}
	
	/**
	 * 判断准星指向的目标是否可交互（避免进食时误操作）
	 * @param hitResult 准星命中结果
	 * @return 是否为可交互目标
	 */
	private boolean isClickable(HitResult hitResult)
	{
		if(hitResult == null)
			return false;
		
		// 实体目标：村民/可驯服生物（如狗、猫）
		if(hitResult instanceof EntityHitResult)
		{
			Entity entity = ((EntityHitResult)hitResult).getEntity();
			return entity instanceof VillagerEntity
				|| entity instanceof TameableEntity;
		}
		
		// 方块目标：带实体的方块（容器/熔炉）/工作台
		if(hitResult instanceof BlockHitResult)
		{
			BlockPos pos = ((BlockHitResult)hitResult).getBlockPos();
			if(pos == null)
				return false;
			
			Block block = MC.world.getBlockState(pos).getBlock();
			return block instanceof BlockWithEntity
				|| block instanceof CraftingTableBlock;
		}
		
		return false;
	}
	
	/**
	 * 判断玩家是否处于受伤状态
	 * @param player 客户端玩家实体
	 * @return 是否受伤
	 */
	private boolean isInjured(ClientPlayerEntity player)
	{
		// 转换受伤阈值为实际生命值（配置值×2，MC中生命值范围0-20）
		int injuryThresholdI = (int)(injuryThreshold.getValue() * 2);
		// 生命值低于最大生命值 - 受伤阈值 → 判定为受伤
		return player.getHealth() < player.getMaxHealth() - injuryThresholdI;
	}
	
	/**
	 * 物品拿取来源枚举
	 */
	private enum TakeItemsFrom
	{
		HANDS("仅手部", 0),			// 仅当前手持物品
		HOTBAR("快捷栏", 9),			// 快捷栏（0-8槽位）
		INVENTORY("全背包", 36);		// 全背包（0-35槽位）
		
		private final String name;			// 显示名称
		private final int maxInvSlot;		// 最大检查槽位
		
		private TakeItemsFrom(String name, int maxInvSlot)
		{
			this.name = name;
			this.maxInvSlot = maxInvSlot;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}