/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"属性切换", "attribute swap", "weapon swap", "auto swap",
	"auto shield break", "disable shield", "item saver", "durability saver"})
public final class AttributeSwapHack extends Hack
	implements PlayerAttacksEntityListener, UpdateListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("模式",
		"固定模式切换到指定栏位；智能模式根据目标选择斧头或不消耗耐久的物品。", Mode.values(), Mode.SIMPLE);
	
	private final SliderSetting targetSlot = new SliderSetting("目标栏位",
		"固定模式下切换到的快捷栏位置。", 1, 1, 9, 1, ValueDisplay.INTEGER);
	
	private final CheckboxSetting swapBack =
		new CheckboxSetting("切换回", "延迟后切回原来的物品栏位。", true);
	
	private final SliderSetting swapBackDelay =
		new SliderSetting("切回延迟", "等待多少游戏刻后切回。", 2, 0, 20, 1,
			ValueDisplay.INTEGER.withSuffix(" 刻").withLabel(0, "立即"));
	
	private final CheckboxSetting shieldBreaker =
		new CheckboxSetting("破盾斧", "智能模式下，目标正在使用盾牌时切换到斧头。", true);
	
	private final CheckboxSetting itemSaver =
		new CheckboxSetting("保护耐久", "智能模式下，切换到不消耗耐久的物品以保护当前物品。", true);
	
	private final CheckboxSetting onlyWithKillAura =
		new CheckboxSetting("仅限 Killaura", "仅在 Killaura 启用时生效。", false);
	
	private int backTimer;
	private boolean awaitingBack;
	private int originalSlot;
	
	public AttributeSwapHack()
	{
		super("属性切换");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(targetSlot);
		addSetting(swapBack);
		addSetting(swapBackDelay);
		addSetting(shieldBreaker);
		addSetting(itemSaver);
		addSetting(onlyWithKillAura);
	}
	
	@Override
	protected void onEnable()
	{
		backTimer = 0;
		awaitingBack = false;
		originalSlot = -1;
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		
		if(awaitingBack)
			doSwapBack();
		
		awaitingBack = false;
		backTimer = 0;
		originalSlot = -1;
	}
	
	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(onlyWithKillAura.isChecked()
			&& !WURST.getHax().killauraHack.isEnabled())
			return;
		
		if(awaitingBack)
			return;
		
		if(mode.getSelected() == Mode.SIMPLE)
			doSwap(targetSlot.getValueI() - 1);
		else
			doSwap(getSmartSlot(target));
	}
	
	@Override
	public void onUpdate()
	{
		if(!awaitingBack)
			return;
		if(backTimer-- > 0)
			return;
		
		doSwapBack();
		awaitingBack = false;
	}
	
	private void doSwap(int slotIndex)
	{
		if(slotIndex < 0 || slotIndex > 8 || MC.player == null)
			return;
		
		int current = MC.player.getInventory().selectedSlot;
		if(slotIndex == current)
			return;
		
		originalSlot = current;
		MC.player.getInventory().selectedSlot = slotIndex;
		
		if(swapBack.isChecked())
		{
			awaitingBack = true;
			backTimer = swapBackDelay.getValueI();
		}
	}
	
	private void doSwapBack()
	{
		if(MC.player != null && originalSlot >= 0 && originalSlot <= 8)
			MC.player.getInventory().selectedSlot = originalSlot;
		
		originalSlot = -1;
	}
	
	private int getSmartSlot(Entity target)
	{
		ItemStack current = MC.player.getMainHandStack();
		
		if(target instanceof LivingEntity living && shieldBreaker.isChecked()
			&& living.isBlocking())
		{
			if(current.getItem() instanceof AxeItem)
				return -1;
			
			int axeSlot =
				InventoryUtils.indexOf(s -> s.getItem() instanceof AxeItem, 9);
			if(axeSlot != -1)
				return axeSlot;
		}
		
		int bestSlot = -1;
		int bestScore = getDurabilityScore(current);
		
		for(int slot = 0; slot < 9; slot++)
		{
			if(slot == MC.player.getInventory().selectedSlot)
				continue;
			
			ItemStack stack = MC.player.getInventory().getStack(slot);
			if(stack.isEmpty())
				continue;
			
			int score = getDurabilityScore(stack);
			if(score > bestScore)
			{
				bestScore = score;
				bestSlot = slot;
			}
		}
		
		return bestSlot;
	}
	
	private int getDurabilityScore(ItemStack stack)
	{
		if(!itemSaver.isChecked())
			return 0;
		
		return stack.isDamageable() ? 0 : 4;
	}
	
	private enum Mode
	{
		SIMPLE("固定"),
		SMART("智能");
		
		private final String name;
		
		Mode(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
