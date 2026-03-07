/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Random;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.BlockBreakingProgressListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

@SearchTags({"快速挖掘", "FastMine", "SpeedMine", "SpeedyGonzales", "fast break",
	"fast mine", "speed mine", "speedy gonzales", "NoBreakDelay",
	"no break delay"})
public final class FastBreakHack extends Hack
	implements UpdateListener, BlockBreakingProgressListener
{
	private final SliderSetting activationChance = new SliderSetting(
		"触发概率",
		"只以给定的概率快速挖掘你打破的部分方块，"
			+ "这使得反作弊插件更难检测到。\n\n"
			+ "如果启用了合法模式，此设置无效。",
		1, 0, 1, 0.01, ValueDisplay.PERCENTAGE);
	
	private final CheckboxSetting legitMode = new CheckboxSetting("合法模式",
		"仅移除破坏方块之间的延迟，而不加快破坏过程本身。\n\n"
			+ "这要慢得多，但在绕过反作弊插件方面非常有效。"
			+ "如果常规快速挖掘不起作用且触发概率滑块没有帮助，请使用此选项。",
		false);
	
	private final Random random = new Random();
	private BlockPos lastBlockPos;
	private boolean fastBreakBlock;
	
	public FastBreakHack()
	{
		super("快速挖掘");
		setCategory(Category.BLOCKS);
		addSetting(activationChance);
		addSetting(legitMode);
	}
	
	@Override
	public String getRenderName()
	{
		if(legitMode.isChecked())
			return getName() + "合法";
		return getName();
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(BlockBreakingProgressListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(BlockBreakingProgressListener.class, this);
		lastBlockPos = null;
	}
	
	@Override
	public void onUpdate()
	{
		MC.interactionManager.blockBreakingCooldown = 0;
	}
	
	@Override
	public void onBlockBreakingProgress(BlockBreakingProgressEvent event)
	{
		if(legitMode.isChecked())
			return;
		
		if(MC.interactionManager.currentBreakingProgress >= 1)
			return;
		
		BlockPos blockPos = event.getBlockPos();
		if(!blockPos.equals(lastBlockPos))
		{
			lastBlockPos = blockPos;
			fastBreakBlock = random.nextDouble() <= activationChance.getValue();
		}
		
		// Ignore unbreakable blocks to avoid slowdown issue
		if(BlockUtils.isUnbreakable(blockPos))
			return;
		
		if(!fastBreakBlock)
			return;
		
		Action action = PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK;
		Direction direction = event.getDirection();
		IMC.getInteractionManager().sendPlayerActionC2SPacket(action, blockPos,
			direction);
	}
}
