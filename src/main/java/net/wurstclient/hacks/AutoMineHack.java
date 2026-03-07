/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HandleBlockBreakingListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"自动挖掘","auto mine", "AutoBreak", "auto break"})
public final class AutoMineHack extends Hack
	implements UpdateListener, HandleBlockBreakingListener
{
	// 超高速模式（挖掘速度远超正常水平，易被反作弊检测）
	private final CheckboxSetting superFastMode =
		new CheckboxSetting("超高速模式",
			"以超出正常水平的速度破坏方块，可能会被反作弊插件检测到。",
			false);
	
	public AutoMineHack()
	{
		super("自动挖掘");
		setCategory(Category.BLOCKS);
		addSetting(superFastMode);
	}
	
	@Override
	protected void onEnable()
	{
		// 禁用冲突的挖掘类功能，避免功能叠加
		WURST.getHax().excavatorHack.setEnabled(false);
		WURST.getHax().nukerHack.setEnabled(false);
		WURST.getHax().nukerLegitHack.setEnabled(false);
		WURST.getHax().speedNukerHack.setEnabled(false);
		WURST.getHax().tunnellerHack.setEnabled(false);
		WURST.getHax().veinMinerHack.setEnabled(false);
		
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(HandleBlockBreakingListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(HandleBlockBreakingListener.class, this);
		
		// 重置攻击键状态，取消正在进行的挖掘操作
		IKeyBinding.get(MC.options.attackKey).resetPressedState();
		MC.interactionManager.cancelBlockBreaking();
	}
	
	@Override
	public void onUpdate()
	{
		ClientPlayerInteractionManager im = MC.interactionManager;
		
		// 忽略攻击冷却（打开任意界面都会将冷却设为10000刻，不影响自动挖掘）
		
		// 玩家处于骑乘状态：取消挖掘并返回
		if(MC.player.isRiding())
		{
			im.cancelBlockBreaking();
			return;
		}
		
		// 获取准星指向的目标：非方块目标则取消挖掘
		HitResult hitResult = MC.crosshairTarget;
		if(hitResult == null || hitResult.getType() != HitResult.Type.BLOCK
			|| !(hitResult instanceof BlockHitResult bHitResult))
		{
			im.cancelBlockBreaking();
			return;
		}
		
		// 获取挖掘目标的方块信息
		BlockPos pos = bHitResult.getBlockPos();
		BlockState state = MC.world.getBlockState(pos);
		Direction side = bHitResult.getSide();
		
		// 目标位置为空气方块：取消挖掘
		if(state.isAir())
		{
			im.cancelBlockBreaking();
			return;
		}
		
		// 若启用自动工具功能，为当前方块装备最优工具
		WURST.getHax().autoToolHack.equipIfEnabled(pos);
		
		// 玩家正在使用物品（如喝药水/吃东西）：不取消挖掘（与原版逻辑一致）
		if(MC.player.isUsingItem())
			return;
		
		// 未开始挖掘：启动挖掘操作
		if(!im.isBreakingBlock())
			im.attackBlock(pos, side);
		
		// 更新挖掘进度：成功则生成挖掘粒子、挥动手臂并保持攻击键按下
		if(im.updateBlockBreakingProgress(pos, side))
		{
			MC.particleManager.addBlockBreakingParticles(pos, side);
			MC.player.swingHand(Hand.MAIN_HAND);
			MC.options.attackKey.setPressed(true);
		}
	}
	
	@Override
	public void onHandleBlockBreaking(HandleBlockBreakingEvent event)
	{
		// 取消原版挖掘逻辑，避免重复发送挖掘数据包
		// 超高速模式下不取消，使用原版+自定义双重逻辑提升速度
		if(!superFastMode.isChecked())
			event.cancel();
	}
}