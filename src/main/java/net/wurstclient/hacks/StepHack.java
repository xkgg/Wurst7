/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

public final class StepHack extends Hack implements UpdateListener
{
	// 模式选择：简易模式（可自定义高度）/ 合法模式（绕过反作弊）
	private final EnumSetting<Mode> mode = new EnumSetting<>("模式",
		"\u00a7l简易\u00a7r模式可以上多格高的台阶（启用高度调节滑块）。\n"
			+ "\u00a7l合法\u00a7r模式可以绕过NoCheat+反作弊检测。",
		Mode.values(), Mode.LEGIT);
	
	// 台阶高度（仅简易模式生效）
	private final SliderSetting height =
		new SliderSetting("高度", "仅在\u00a7l简易\u00a7r模式下生效。",
			1, 1, 10, 1, ValueDisplay.INTEGER);
	
	public StepHack()
	{
		super("快速上楼");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(height);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		// 禁用时恢复原版默认台阶高度（0.5格）
		MC.player.stepHeight = 0.5F;
	}
	
	@Override
	public void onUpdate()
	{
		if(mode.getSelected() == Mode.SIMPLE)
		{
			// 简易模式：直接修改玩家上台阶高度
			MC.player.stepHeight = height.getValueF();
			return;
		}
		
		// 合法模式：模拟原生操作绕过反作弊
		ClientPlayerEntity player = MC.player;
		// 恢复原版基础台阶高度
		player.stepHeight = 0.5F;
		
		// 检测是否撞到水平方向的方块（前方有台阶）
		if(!player.horizontalCollision)
			return;
		
		// 过滤非地面场景（空中/攀爬/水中/岩浆中不生效）
		if(!player.isOnGround() || player.isClimbing()
			|| player.isTouchingWater() || player.isInLava())
			return;
		
		// 玩家无移动输入时不生效
		if(player.input.movementForward == 0
			&& player.input.movementSideways == 0)
			return;
		
		// 玩家主动跳跃时不生效
		if(player.input.jumping)
			return;
		
		// 检测上方1格空间是否为空（避免卡进方块）
		Box box = player.getBoundingBox().offset(0, 0.05, 0).expand(0.05);
		if(!MC.world.isSpaceEmpty(player, box.offset(0, 1, 0)))
			return;
		
		// 计算实际可上的台阶高度
		double stepHeight = BlockUtils.getBlockCollisions(box)
			.mapToDouble(bb -> bb.maxY).max().orElse(Double.NEGATIVE_INFINITY);
		stepHeight -= player.getY();
		
		// 过滤无效高度（负数或超过1格）
		if(stepHeight < 0 || stepHeight > 1)
			return;
		
		ClientPlayNetworkHandler netHandler = player.networkHandler;
		
		// 发送分步移动数据包（模拟原生跳跃，绕过反作弊）
		netHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
			player.getX(), player.getY() + 0.42 * stepHeight, player.getZ(),
			player.isOnGround()));
		
		netHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
			player.getX(), player.getY() + 0.753 * stepHeight, player.getZ(),
			player.isOnGround()));
		
		// 最终调整玩家位置到台阶上
		player.setPosition(player.getX(), player.getY() + stepHeight,
			player.getZ());
	}
	
	/**
	 * 判断是否允许自动跳跃
	 */
	public boolean isAutoJumpAllowed()
	{
		return !isEnabled() && !WURST.getCmds().goToCmd.isActive();
	}
	
	/**
	 * 上台阶模式枚举
	 */
	private enum Mode
	{
		SIMPLE("简易"),
		LEGIT("合法");
		
		private final String name;
		
		private Mode(String name)
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