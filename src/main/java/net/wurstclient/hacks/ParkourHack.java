/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.util.math.Box;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"简单跑酷", "parkour"})
public final class ParkourHack extends Hack implements UpdateListener
{
	private final SliderSetting minDepth = new SliderSetting("最小深度",
		"如果坑的深度不至少达到此值，不会跳过。\n"
			+ "增加以阻止跑酷从楼梯上跳下。\n"
			+ "减少以使跑酷在地毯边缘跳跃。",
		0.5, 0.05, 10, 0.05, ValueDisplay.DECIMAL.withSuffix("m"));
	
	private final SliderSetting edgeDistance =
		new SliderSetting("边缘距离",
			"跑酷会让你在跳跃前离边缘多近。",
			0.001, 0.001, 0.25, 0.001, ValueDisplay.DECIMAL.withSuffix("m"));
	
	private final CheckboxSetting sneak = new CheckboxSetting(
		"潜行时跳跃",
		"即使在潜行时也保持跑酷激活。\n"
			+ "使用此选项时，你可能需要增加\u00a7l边缘距离\u00a7r滑块。",
		false);
	
	public ParkourHack()
	{
		super("简单跑酷");
		setCategory(Category.MOVEMENT);
		addSetting(minDepth);
		addSetting(edgeDistance);
		addSetting(sneak);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().safeWalkHack.setEnabled(false);
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		if(!MC.player.isOnGround() || MC.options.jumpKey.isPressed())
			return;
		
		if(!sneak.isChecked()
			&& (MC.player.isSneaking() || MC.options.sneakKey.isPressed()))
			return;
		
		Box box = MC.player.getBoundingBox();
		Box adjustedBox = box.stretch(0, -minDepth.getValue(), 0)
			.expand(-edgeDistance.getValue(), 0, -edgeDistance.getValue());
		
		if(!MC.world.isSpaceEmpty(MC.player, adjustedBox))
			return;
		
		MC.player.jump();
	}
}
