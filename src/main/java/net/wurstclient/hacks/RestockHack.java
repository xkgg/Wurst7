/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

/**
 * 自动补货（Restock）：当快捷栏/副手的指定物品数量低于阈值时，自动从背包补充；
 * 支持工具耐久低时自动替换，避免工具损坏。
 */
@SearchTags({"自动补货", "AutoRestock", "auto-restock", "auto restock"})
public final class RestockHack extends Hack implements UpdateListener
{
	// 副手槽位常量（对应原版PlayerInventory的副手槽ID）
	public static final int OFFHAND_ID = PlayerInventory.OFF_HAND_SLOT;
	// 副手槽位的网络包ID（用于Inventory操作数据包）
	public static final int OFFHAND_PKT_ID = 45;
	
	// 补货搜索范围：0-35格（背包+快捷栏） + 副手槽位
	private static final List<Integer> SEARCH_SLOTS =
		Stream.concat(IntStream.range(0, 36).boxed(), Stream.of(OFFHAND_ID))
			.collect(Collectors.toCollection(ArrayList::new));
	
	// 需补货的物品列表：指定要自动补充的物品（默认示例：矿车）
	private ItemListSetting items = new ItemListSetting("补货物品",
		"需要自动补货的物品。", "minecraft:minecart");
	
	// 补货目标槽位：指定补充到哪个槽位（-1=当前选中槽位，9=副手，0-8=快捷栏对应槽位）
	private final SliderSetting restockSlot = new SliderSetting("补货槽位",
		"将物品补充到哪个槽位。", 0, -1, 9, 1,
		ValueDisplay.INTEGER.withLabel(9, "副手").withLabel(-1, "当前选中"));
	
	// 最低数量阈值：物品数量低于该值时触发补货
	private final SliderSetting restockAmount = new SliderSetting(
		"最低数量阈值",
		"快捷栏中物品数量低于该值时，触发新一轮补货。",
		1, 1, 64, 1, ValueDisplay.INTEGER);
	
	// 工具修复模式：耐久低于阈值时自动替换工具（0=关闭，1-100=剩余耐久阈值）
	private final SliderSetting repairMode = new SliderSetting(
		"工具修复模式",
		"当工具耐久剩余量达到设定阈值时自动替换，方便你在工具损坏前修复。\n"
			+ "可设置0（关闭）到100（剩余使用次数）。",
		0, 0, 100, 1, ValueDisplay.INTEGER.withLabel(0, "关闭"));
	
	public RestockHack()
	{
		super("自动补货"); // 功能显示名称
		setCategory(Category.ITEMS); // 归类到“物品”类别
		addSetting(items); // 添加“补货物品”设置项
		addSetting(restockSlot); // 添加“补货槽位”设置项
		addSetting(restockAmount); // 添加“最低数量阈值”设置项
		addSetting(repairMode); // 添加“工具修复模式”设置项
	}
	
	@Override
	protected void onEnable()
	{
		// 注册帧更新监听器，每帧执行补货逻辑
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 移除监听器，停止补货逻辑
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		// 打开物品栏界面时不执行补货（避免干扰手动操作）
		if(MC.currentScreen instanceof HandledScreen)
			return;
		
		PlayerInventory inv = MC.player.getInventory();
		IClientPlayerInteractionManager im = IMC.getInteractionManager();
		
		// 解析补货目标槽位：-1=当前选中槽位，9=副手，其他=快捷栏对应槽位
		int hotbarSlot = restockSlot.getValueI();
		if(hotbarSlot == -1)
			hotbarSlot = inv.selectedSlot;
		else if(hotbarSlot == 9)
			hotbarSlot = OFFHAND_ID;
		
		// 遍历所有需补货的物品，执行补货逻辑
		for(String itemName : items.getItemNames())
		{
			ItemStack hotbarStack = inv.getStack(hotbarSlot);
			
			// 判断是否需要补货：物品为空/物品不匹配 → 需要补货；
			// 物品数量≥阈值 → 无需补货，直接返回
			boolean wrongItem =
				hotbarStack.isEmpty() || !itemEqual(itemName, hotbarStack);
			if(!wrongItem && hotbarStack.getCount() >= Math
				.min(restockAmount.getValueI(), hotbarStack.getMaxCount()))
				return;
			
			// 搜索背包中可用于补货的物品槽位
			List<Integer> searchResult =
				searchSlotsWithItem(itemName, hotbarSlot);
			// 执行补货操作：将找到的物品移动到目标槽位
			for(int itemIndex : searchResult)
			{
				int pickupIndex = InventoryUtils.toNetworkSlot(itemIndex);
				
				// 模拟点击操作：拾取物品 → 放入目标槽位 → 若有剩余则放回（避免物品卡在光标）
				im.windowClick_PICKUP(pickupIndex);
				im.windowClick_PICKUP(InventoryUtils.toNetworkSlot(hotbarSlot));
				if(!MC.player.playerScreenHandler.getCursorStack().isEmpty())
					im.windowClick_PICKUP(pickupIndex);
				
				// 目标槽位物品已满时停止补货
				if(hotbarStack.getCount() >= hotbarStack.getMaxCount())
					break;
			}
			
			// 物品不匹配且无补货来源 → 跳过当前物品，继续下一个
			if(wrongItem && searchResult.isEmpty())
				continue;
			
			// 补货完成 → 退出循环
			break;
		}
		
		// 工具修复逻辑：耐久低于阈值时自动替换工具
		ItemStack restockStack = inv.getStack(hotbarSlot);
		if(repairMode.getValueI() > 0 && restockStack.isDamageable()
			&& isTooDamaged(restockStack))
			for(int i : SEARCH_SLOTS)
			{
				// 跳过目标槽位和副手槽位
				if(i == hotbarSlot || i == OFFHAND_ID)
					continue;
				
				ItemStack stack = inv.getStack(i);
				// 找到空槽位/非耐久物品槽位 → 交换工具（将损坏工具移走）
				if(stack.isEmpty() || !stack.isDamageable())
				{
					IMC.getInteractionManager().windowClick_SWAP(i,
						InventoryUtils.toNetworkSlot(hotbarSlot));
					break;
				}
			}
	}
	
	/**
	 * 判断物品是否损坏过度（耐久剩余≤设定阈值）
	 */
	private boolean isTooDamaged(ItemStack stack)
	{
		return stack.getMaxDamage() - stack.getDamage() <= repairMode
			.getValueI();
	}
	
	/**
	 * 搜索背包中包含指定物品的槽位（跳过目标槽位）
	 * @param itemName 目标物品ID
	 * @param slotToSkip 需跳过的槽位（避免重复操作）
	 * @return 包含目标物品的槽位列表
	 */
	private List<Integer> searchSlotsWithItem(String itemName, int slotToSkip)
	{
		List<Integer> slots = new ArrayList<>();
		
		for(int i : SEARCH_SLOTS)
		{
			// 跳过目标槽位
			if(i == slotToSkip)
				continue;
			
			ItemStack stack = MC.player.getInventory().getStack(i);
			// 跳过空槽位
			if(stack.isEmpty())
				continue;
			
			// 匹配到目标物品 → 加入列表
			if(itemEqual(itemName, stack))
				slots.add(i);
		}
		
		return slots;
	}
	
	/**
	 * 判断物品是否匹配（包含耐久检测）
	 * @param itemName 目标物品ID
	 * @param stack 待检测物品栈
	 * @return 是否匹配
	 */
	private boolean itemEqual(String itemName, ItemStack stack)
	{
		// 工具修复模式开启时，损坏过度的工具不参与匹配
		if(repairMode.getValueI() > 0 && stack.isDamageable()
			&& isTooDamaged(stack))
			return false;
		
		// 对比物品ID是否一致
		return Registries.ITEM.getId(stack.getItem()).toString()
			.equals(itemName);
	}
}