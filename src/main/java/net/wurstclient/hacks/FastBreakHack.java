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

@SearchTags({"快速破坏", "FastMine", "SpeedMine", "SpeedyGonzales", "fast break",
	"fast mine", "speed mine", "speedy gonzales", "NoBreakDelay",
	"no break delay"})
public final class FastBreakHack extends Hack
	implements UpdateListener, BlockBreakingProgressListener
{
	private final SliderSetting activationChance = new SliderSetting(
		"触发概率",
		"仅以设定概率快速破坏你所挖掘的部分方块，以此增加反作弊插件的检测难度。\n\n"
			+ "启用合规模式时，此设置无效。",
		1, 0, 1, 0.01, ValueDisplay.PERCENTAGE);
	
	private final CheckboxSetting legitMode = new CheckboxSetting("合规模式",
		"仅移除方块破坏之间的延迟，不会加速破坏过程本身。\n\n"
		+ "该模式速度较慢，但能有效规避反作弊插件检测。"
		+ "如果常规快速破坏无效且调整触发概率滑块也无帮助，请使用此模式。",
		false);
	
	private final Random random = new Random();
	private BlockPos lastBlockPos;
	private boolean fastBreakBlock;
	
	public FastBreakHack()
	{
		super("快速破坏");
		setCategory(Category.BLOCKS);
		addSetting(activationChance);
		addSetting(legitMode);
	}
	
	@Override
	public String getRenderName()
	{
		if(legitMode.isChecked())
			return getName() + "Legit";
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
		
		// 忽略不可破坏的方块，避免出现卡顿问题
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