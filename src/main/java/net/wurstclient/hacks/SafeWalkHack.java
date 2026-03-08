/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * 安全行走（SafeWalk）：防止玩家从方块边缘掉落，靠近边缘时自动潜行，
 * 常用于高速搭路（SpeedBridge）、边缘行走等场景，提升移动安全性。
 */
@SearchTags({"边缘潜行", "SneakSafety", "sneak safety", "SpeedBridgeHelper",
	"speed bridge helper"})
public final class SafeWalkHack extends Hack
{
	// 边缘潜行开关：控制是否在边缘处显示潜行状态（视觉上的潜行动作）
	private final CheckboxSetting sneak =
		new CheckboxSetting("边缘自动潜行", "靠近边缘时自动触发潜行动作。", false);
	
	// 边缘触发距离：控制距离边缘多近时触发潜行（仅边缘自动潜行开启时生效）
	private final SliderSetting edgeDistance = new SliderSetting(
		"边缘触发距离",
		"安全行走功能会在你距离边缘该距离时触发潜行。\n\n"
			+ "此设置仅在「边缘自动潜行」启用时生效。",
		0.05, 0.05, 0.25, 0.001, ValueDisplay.DECIMAL.withSuffix("米"));
	
	// 标记当前是否处于自动潜行状态
	private boolean sneaking;
	
	public SafeWalkHack()
	{
		super("边缘潜行"); // 功能显示名称
		setCategory(Category.MOVEMENT); // 归类到“移动”类别
		addSetting(sneak); // 添加“边缘自动潜行”设置项
		addSetting(edgeDistance); // 添加“边缘触发距离”设置项
	}
	
	@Override
	protected void onEnable()
	{
		// 启用时关闭冲突的跑酷功能，避免逻辑冲突
		WURST.getHax().parkourHack.setEnabled(false);
		// 初始化自动潜行状态为未潜行
		sneaking = false;
	}
	
	@Override
	protected void onDisable()
	{
		// 禁用时如果处于自动潜行状态，恢复为非潜行
		if(sneaking)
			setSneaking(false);
	}
	
	/**
	 * 边缘碰撞检测回调：判断玩家是否处于边缘，触发自动潜行
	 * @param clipping 是否触发边缘碰撞（是否需要潜行）
	 */
	public void onClipAtLedge(boolean clipping)
	{
		ClientPlayerEntity player = MC.player;
		
		// 过滤无效场景：功能未启用/边缘潜行未开启/玩家不在地面 → 取消潜行
		if(!isEnabled() || !sneak.isChecked() || !player.isOnGround())
		{
			if(sneaking)
				setSneaking(false);
			
			return;
		}
		
		// 计算玩家碰撞箱：调整为“边缘检测箱”（缩小范围，精准判断是否靠近边缘）
		Box box = player.getBoundingBox();
		Box adjustedBox = box.stretch(0, -player.stepHeight, 0)
			.expand(-edgeDistance.getValue(), 0, -edgeDistance.getValue());
		
		// 检测调整后的碰撞箱下方是否为空（即玩家站在边缘）→ 触发潜行
		if(MC.world.isSpaceEmpty(player, adjustedBox))
			clipping = true;
		
		// 设置最终潜行状态
		setSneaking(clipping);
	}
	
	/**
	 * 控制玩家潜行状态（模拟按下/松开潜行键）
	 * @param sneaking 是否开启潜行
	 */
	private void setSneaking(boolean sneaking)
	{
		IKeyBinding sneakKey = IKeyBinding.get(MC.options.sneakKey);
		
		// 按下潜行键 / 重置潜行键状态
		if(sneaking)
			sneakKey.setPressed(true);
		else
			sneakKey.resetPressedState();
		
		// 更新当前潜行状态标记
		this.sneaking = sneaking;
	}
	
	// 注：核心边缘检测逻辑在 ClientPlayerEntityMixin 中实现
}