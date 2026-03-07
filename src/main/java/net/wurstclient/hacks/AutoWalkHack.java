/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;

@SearchTags({"自动行走", "auto walk", "AutoWalk"})
public final class AutoWalkHack extends Hack implements UpdateListener
{
	public AutoWalkHack()
	{
		super("自动行走");
		setCategory(Category.MOVEMENT);
	}
	
	@Override
	protected void onEnable()
	{
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销事件监听器
		EVENTS.remove(UpdateListener.class, this);
		// 重置前进键状态
		IKeyBinding.get(MC.options.forwardKey).resetPressedState();
	}
	
	@Override
	public void onUpdate()
	{
		// 持续按下前进键
		MC.options.forwardKey.setPressed(true);
	}
}
