/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.navigator.NavigatorMainScreen;

/**
 * 功能导航器（Navigator）：Wurst客户端的功能菜单快捷入口，
 * 启用后会打开功能管理主界面，用于浏览、搜索、配置所有客户端功能。
 */
@DontSaveState // 禁用状态保存（重启客户端后默认关闭）
@DontBlock // 标记为无需屏蔽的功能（客户端内置机制）
@SearchTags({"功能菜单", "ClickGUI", "click gui", "SearchGUI", "search gui", "HackMenu",
	"hack menu"}) // 搜索标签（兼容中英文关键词）
public final class NavigatorHack extends Hack
{
	public NavigatorHack()
	{
		super("功能菜单"); // 功能显示名称
	}
	
	@Override
	protected void onEnable()
	{
		// 检查当前是否未打开功能导航主界面，避免重复打开
		if(!(MC.currentScreen instanceof NavigatorMainScreen))
			// 打开功能导航主界面
			MC.setScreen(new NavigatorMainScreen());
		
		// 自动禁用自身（仅作为打开界面的快捷方式，无需保持启用）
		setEnabled(false);
	}
}