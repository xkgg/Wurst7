/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"自动登出","auto leave", "AutoDisconnect", "auto disconnect", "AutoQuit",
	"auto quit"})
public final class AutoLeaveHack extends Hack implements UpdateListener
{
	// 触发生命值（生命值低于/等于该值时登出服务器）
	private final SliderSetting health = new SliderSetting("触发生命值",
		"当你的生命值降至该数值或以下时，自动退出服务器。",
		4, 0.5, 9.5, 0.5, ValueDisplay.DECIMAL.withSuffix(" 颗心"));
	
	// 退出模式（不同的登出方式，适配不同反作弊）
	public final EnumSetting<Mode> mode = new EnumSetting<>("退出模式",
		"\u00a7l正常退出\u00a7r模式会正常退出游戏。\n"
			+ "可绕过NoCheat+，但无法绕过CombatLog。\n\n"
			+ "\u00a7l特殊字符\u00a7r模式会发送特殊聊天消息，触发服务器将你踢出。\n"
			+ "可绕过NoCheat+和部分版本的CombatLog。\n\n"
			+ "\u00a7l自伤发包\u00a7r模式会发送攻击数据包，将自己同时设为攻击者和目标，"
			+ "触发服务器踢人机制。\n"
			+ "可同时绕过CombatLog和NoCheat+。",
		Mode.values(), Mode.QUIT);
	
	// 禁用自动重连（登出时自动关闭AutoReconnect功能）
	private final CheckboxSetting disableAutoReconnect = new CheckboxSetting(
		"禁用自动重连", "当自动登出触发时，自动关闭自动重连功能。",
		true);
	
	// 图腾数量阈值（图腾数量高于该值时不触发登出）
	private final SliderSetting totems = new SliderSetting("图腾数量阈值",
		"仅当你的不死图腾数量降至该数值或以下时，才会触发登出。\n\n"
			+ "设为11 = 忽略图腾数量，始终可触发登出",
		11, 0, 11, 1, ValueDisplay.INTEGER.withSuffix(" 个图腾")
			.withLabel(1, "1个图腾").withLabel(11, "忽略"));
	
	public AutoLeaveHack()
	{
		super("自动登出");
		setCategory(Category.COMBAT);
		addSetting(health);
		addSetting(mode);
		addSetting(disableAutoReconnect);
		addSetting(totems);
	}
	
	@Override
	public String getRenderName()
	{
		// 创造模式下标注"已暂停"
		if(MC.player.getAbilities().creativeMode)
			return getName() + " (已暂停)";
		
		// 显示当前选中的退出模式
		return getName() + " [" + mode.getSelected() + "]";
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
		// 创造模式下不触发登出
		if(MC.player.getAbilities().creativeMode)
			return;
		
		// 检查当前生命值：生命值为0（已死亡）或高于触发值 → 不触发
		float currentHealth = MC.player.getHealth();
		if(currentHealth <= 0F || currentHealth > health.getValueF() * 2F)
			return;
		
		// 检查图腾数量：阈值<11 且 背包图腾数>阈值 → 不触发
		if(totems.getValueI() < 11 && InventoryUtils
			.count(Items.TOTEM_OF_UNDYING, 40, true) > totems.getValueI())
			return;
		
		// 执行登出操作（按选中的退出模式）
		mode.getSelected().leave.run();
		
		// 关闭自动登出功能
		setEnabled(false);
		
		// 若开启"禁用自动重连"，则关闭AutoReconnect
		if(disableAutoReconnect.isChecked())
			WURST.getHax().autoReconnectHack.setEnabled(false);
	}
	
	/**
	 * 退出模式枚举
	 */
	public static enum Mode
	{
		QUIT("正常退出", () -> MC.world.disconnect()),
		
		CHARS("特殊字符", () -> MC.getNetworkHandler().sendChatMessage("\u00a7")),
		
		SELFHURT("自伤发包",
			() -> MC.getNetworkHandler()
				.sendPacket(PlayerInteractEntityC2SPacket.attack(MC.player,
					MC.player.isSneaking())));
		
		private final String name;	// 显示名称
		private final Runnable leave; // 登出执行逻辑
		
		private Mode(String name, Runnable leave)
		{
			this.name = name;
			this.leave = leave;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}