/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.AirStrafingSpeedListener;
import net.wurstclient.events.IsPlayerInWaterListener;
import net.wurstclient.events.MouseScrollListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * 基础飞行（Flight）：为玩家提供通用飞行功能，支持自定义水平/垂直飞行速度，
 * 内置防踢出机制（模拟小幅下落规避服务器检测）、潜行减速（防止卡模型），
 * 强制标记玩家为非水中状态以确保飞行正常生效，兼容多数服务器环境。
 */
@SearchTags({"飞行", "FlyHack", "fly hack", "flying"}) // 补充中文搜索标签
public final class FlightHack extends Hack implements UpdateListener,
	IsPlayerInWaterListener, AirStrafingSpeedListener, MouseScrollListener
{
	// 水平飞行速度：控制前后左右移动速度（0.05-10，步长0.05）
	public final SliderSetting horizontalSpeed =
		new SliderSetting("水平飞行速度", 1, 0.05, 10, 0.05, ValueDisplay.DECIMAL);
	
	// 垂直飞行速度：控制上升/下降速度（附带高速度警告）
	public final SliderSetting verticalSpeed = new SliderSetting("垂直飞行速度",
		"§c§l警告：§r 该值设置过高可能导致摔落伤害，即使启用了防摔落功能也无法避免。", 1, 0.05, 5, 0.05,
		v -> ValueDisplay.DECIMAL.getValueString(getActualVerticalSpeed()));
	
	private final CheckboxSetting allowUnsafeVerticalSpeed =
		new CheckboxSetting("允许不安全的垂直速度", "允许垂直速度超过安全上限；高速飞行可能导致摔落伤害。", false);
	
	private final CheckboxSetting scrollToChangeSpeed =
		new CheckboxSetting("滚轮调整速度", "在游戏内滚动鼠标滚轮来调整水平速度，垂直速度会随之变化。", true);
	
	private final CheckboxSetting renderSpeed = new CheckboxSetting(
		"在 HackList 显示速度", "在 HackList 中显示当前水平和垂直飞行速度。", true);
	
	// 潜行减速：潜行时降低水平速度，防止模型卡入方块
	private final CheckboxSetting slowSneaking =
		new CheckboxSetting("潜行减速", "潜行时降低水平飞行速度，防止因高速移动导致模型卡入方块出现异常。", true);
	
	// 防踢出：定期小幅下落，规避服务器对悬空飞行的检测
	private final CheckboxSetting antiKick =
		new CheckboxSetting("防踢出机制", "每隔一段时间让玩家小幅下落，避免因长时间悬空飞行被服务器踢出。", false);
	
	// 防踢出间隔：控制小幅下落的触发频率（多数服务器80tick后会踢人）
	private final SliderSetting antiKickInterval = new SliderSetting("防踢出触发间隔",
		"防踢出机制的触发频率。\n" + "多数服务器会在玩家悬空80tick后执行踢出操作。", 70, 5, 80, 1,
		ValueDisplay.INTEGER.withSuffix(" 游戏刻").withLabel(1, "1游戏刻"));
	
	// 防踢出距离：每次下落的垂直距离（需≥0.032m才会被服务器识别）
	private final SliderSetting antiKickDistance = new SliderSetting("防踢出下落距离",
		"防踢出机制触发时的下落距离。\n" + "多数服务器要求至少下落0.032米才能避免被判定为悬空飞行。", 0.035, 0.01, 0.2,
		0.001, ValueDisplay.DECIMAL.withSuffix("米"));
	
	// 防踢出计时计数器：记录距离下次触发防踢出的游戏刻
	private int tickCounter = 0;
	
	public FlightHack()
	{
		super("飞行"); // 功能显示名称
		setCategory(Category.MOVEMENT); // 归类到“移动”类别
		addSetting(horizontalSpeed); // 添加水平速度设置
		addSetting(verticalSpeed); // 添加垂直速度设置
		addSetting(allowUnsafeVerticalSpeed);
		addSetting(scrollToChangeSpeed);
		addSetting(renderSpeed);
		addSetting(slowSneaking); // 添加潜行减速设置
		addSetting(antiKick); // 添加防踢出开关
		addSetting(antiKickInterval); // 添加防踢出间隔设置
		addSetting(antiKickDistance); // 添加防踢出下落距离设置
	}
	
	@Override
	public String getRenderName()
	{
		if(!renderSpeed.isChecked())
			return getName();
		
		return getName() + " [" + horizontalSpeed.getValueString() + ", "
			+ verticalSpeed.getValueString() + "]";
	}
	
	@Override
	protected void onEnable()
	{
		// 重置防踢出计数器
		tickCounter = 0;
		
		// 禁用冲突的飞行类功能
		WURST.getHax().creativeFlightHack.setEnabled(false);
		WURST.getHax().jetpackHack.setEnabled(false);
		
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this); // 帧更新（核心飞行逻辑）
		EVENTS.add(IsPlayerInWaterListener.class, this); // 水中状态监听（强制设为非水中）
		EVENTS.add(AirStrafingSpeedListener.class, this); // 空中 Strafing
															// 速度（控制水平移动）
		EVENTS.add(MouseScrollListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 移除事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(IsPlayerInWaterListener.class, this);
		EVENTS.remove(AirStrafingSpeedListener.class, this);
		EVENTS.remove(MouseScrollListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		ClientPlayerEntity player = MC.player;
		
		// 重置玩家初始速度（避免与原版物理叠加）
		player.setVelocity(0, 0, 0);
		player.getAbilities().flying = false;
		
		// Let Freecam own movement while its camera is detached from the
		// player.
		if(WURST.getHax().freecamHack.isMovingCamera())
			return;
		
		Vec3d velocity = player.getVelocity();
		double verticalInput = 0;
		double vSpeed = getActualVerticalSpeed();
		
		// 跳跃键：上升（应用垂直飞行速度）
		if(MC.options.jumpKey.isPressed())
			verticalInput += vSpeed;
		
		// Use the physical key state so both vertical keys can cancel out.
		if(IKeyBinding.get(MC.options.sneakKey).isActuallyDown())
		{
			MC.options.sneakKey.setPressed(false);
			verticalInput -= vSpeed;
		}
		
		player.setVelocity(velocity.x, velocity.y + verticalInput, velocity.z);
		
		// 启用防踢出时执行小幅下落逻辑
		if(antiKick.isChecked())
			doAntiKick();
	}
	
	@Override
	public void onGetAirStrafingSpeed(AirStrafingSpeedEvent event)
	{
		if(WURST.getHax().freecamHack.isMovingCamera())
			return;
		
		// 获取基础水平飞行速度
		float speed = horizontalSpeed.getValueF();
		
		// 潜行且启用减速时，限制最大速度为0.85
		if(IKeyBinding.get(MC.options.sneakKey).isActuallyDown()
			&& slowSneaking.isChecked())
			speed = Math.min(speed, 0.85F);
		
		// 设置最终空中移动速度
		event.setSpeed(speed);
	}
	
	@Override
	public void onMouseScroll(double amount)
	{
		if(!isControllingScrollEvents())
			return;
		
		if(amount > 0)
			horizontalSpeed.increaseValue();
		else if(amount < 0)
			horizontalSpeed.decreaseValue();
	}
	
	public boolean isControllingScrollEvents()
	{
		return isEnabled() && scrollToChangeSpeed.isChecked()
			&& MC.currentScreen == null
			&& !WURST.getOtfs().zoomOtf.isControllingScrollEvents()
			&& !WURST.getHax().freecamHack.isMovingCamera();
	}
	
	/**
	 * 执行防踢出逻辑：定期小幅下落再复位，规避服务器检测
	 */
	private void doAntiKick()
	{
		// 计数器超过间隔+1时重置（避免无限计数）
		if(tickCounter > antiKickInterval.getValueI() + 1)
			tickCounter = 0;
		
		Vec3d velocity = MC.player.getVelocity();
		
		// 按计数器阶段执行下落/复位操作
		switch(tickCounter)
		{
			// 阶段0：已有足够下落速度时跳过本轮下落
			case 0 ->
			{
				if(velocity.y <= -antiKickDistance.getValue())
					tickCounter = 2;
				else
					MC.player.setVelocity(velocity.x,
						-antiKickDistance.getValue(), velocity.z);
			}
			
			// 阶段1：复位（回到原高度）
			case 1 -> MC.player.setVelocity(velocity.x,
				antiKickDistance.getValue(), velocity.z);
		}
		
		// 计数器递增
		tickCounter++;
	}
	
	public double getHorizontalSpeed()
	{
		return horizontalSpeed.getValue();
	}
	
	public double getActualVerticalSpeed()
	{
		boolean limitVerticalSpeed = !allowUnsafeVerticalSpeed.isChecked()
			&& !MC.player.getAbilities().invulnerable;
		
		return MathHelper.clamp(
			horizontalSpeed.getValue() * verticalSpeed.getValue(), 0.05,
			limitVerticalSpeed ? 3.95 : 10);
	}
	
	@Override
	public void onIsPlayerInWater(IsPlayerInWaterEvent event)
	{
		// 强制标记玩家为非水中状态，确保飞行功能不受水中浮力影响
		event.setInWater(false);
	}
}
