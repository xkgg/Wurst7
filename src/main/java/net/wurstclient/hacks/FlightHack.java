/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.AirStrafingSpeedListener;
import net.wurstclient.events.IsPlayerInWaterListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"飞行", "Flight", "fly hack", "flying"})
public final class FlightHack extends Hack
	implements UpdateListener, IsPlayerInWaterListener, AirStrafingSpeedListener
{
	public final SliderSetting horizontalSpeed = new SliderSetting(
		"水平速度", 1, 0.05, 10, 0.05, ValueDisplay.DECIMAL);
	
	public final SliderSetting verticalSpeed = new SliderSetting(
		"垂直速度",
		"\u00a7c\u00a7l警告:\u00a7r 设置过高可能会导致掉落伤害，即使启用了NoFall。",
		1, 0.05, 5, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting slowSneaking = new CheckboxSetting(
		"缓慢潜行",
		"在潜行时降低水平速度，防止出现卡顿。",
		true);
	
	private final CheckboxSetting antiKick = new CheckboxSetting("防踢",
		"时不时让你下落一点，防止被服务器踢出。",
		false);
	
	private final SliderSetting antiKickInterval = 
		new SliderSetting("防踢间隔",
			"防踢功能防止你被踢出的频率。\n"
				+ "大多数服务器会在80刻后踢出你。",
			30, 5, 80, 1,
			ValueDisplay.INTEGER.withSuffix(" 刻").withLabel(1, "1 刻"));
	
	private final SliderSetting antiKickDistance = new SliderSetting(
		"防踢距离",
		"防踢功能让你下落的距离。\n"
			+ "大多数服务器要求至少下落0.032米才能防止被踢出。",
		0.07, 0.01, 0.2, 0.001, ValueDisplay.DECIMAL.withSuffix("米"));
	
	private int tickCounter = 0;
	
	public FlightHack()
	{
		super("飞行");
		setCategory(Category.MOVEMENT);
		addSetting(horizontalSpeed);
		addSetting(verticalSpeed);
		addSetting(slowSneaking);
		addSetting(antiKick);
		addSetting(antiKickInterval);
		addSetting(antiKickDistance);
	}
	
	@Override
	protected void onEnable()
	{
		tickCounter = 0;
		
		// 禁用冲突的飞行类功能
		WURST.getHax().creativeFlightHack.setEnabled(false);
		WURST.getHax().jetpackHack.setEnabled(false);
		
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(IsPlayerInWaterListener.class, this);
		EVENTS.add(AirStrafingSpeedListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(IsPlayerInWaterListener.class, this);
		EVENTS.remove(AirStrafingSpeedListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		ClientPlayerEntity player = MC.player;
		
		// 禁用原版飞行
		player.getAbilities().flying = false;
		
		// 重置速度
		player.setVelocity(0, 0, 0);
		Vec3d velocity = player.getVelocity();
		
		// 处理上升
		if(MC.options.jumpKey.isPressed())
			player.setVelocity(velocity.x, verticalSpeed.getValue(),
				velocity.z);
		
		// 处理下降
		if(MC.options.sneakKey.isPressed())
			player.setVelocity(velocity.x, -verticalSpeed.getValue(),
				velocity.z);
		
		// 执行防踢逻辑
		if(antiKick.isChecked())
			doAntiKick(velocity);
	}
	
	@Override
	public void onGetAirStrafingSpeed(AirStrafingSpeedEvent event)
	{
		// 设置水平速度
		float speed = horizontalSpeed.getValueF();
		
		// 潜行时降低速度
		if(MC.options.sneakKey.isPressed() && slowSneaking.isChecked())
			speed = Math.min(speed, 0.85F);
		
		event.setSpeed(speed);
	}
	
	private void doAntiKick(Vec3d velocity)
	{
		// 重置计数器
		if(tickCounter > antiKickInterval.getValueI() + 1)
			tickCounter = 0;
		
		// 防踢逻辑
		switch(tickCounter)
		{
			case 0 ->
			{
				if(MC.options.sneakKey.isPressed())
					tickCounter = 2;
				else
					MC.player.setVelocity(velocity.x,
						-antiKickDistance.getValue(), velocity.z);
			}
			
			case 1 -> MC.player.setVelocity(velocity.x,
				antiKickDistance.getValue(), velocity.z);
		}
		
		tickCounter++;
	}
	
	@Override
	public void onIsPlayerInWater(IsPlayerInWaterEvent event)
	{
		// 防止水影响飞行
		event.setInWater(false);
	}
}
