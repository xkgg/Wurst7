/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"Wurst选项", "WurstOptions", "settings"})
@DontBlock
public final class WurstOptionsOtf extends OtherFeature
{
	private final EnumSetting<Location> location = new EnumSetting<>("显示位置",
		"设置Wurst选项按钮在暂停菜单或统计屏幕中的位置。", Location.values(), Location.GAME_MENU);
	
	public WurstOptionsOtf()
	{
		super("Wurst选项", "控制暂停菜单中Wurst选项按钮的位置。");
		addSetting(location);
	}
	
	public boolean isVisibleInGameMenu()
	{
		return WURST.isEnabled()
			&& location.getSelected() == Location.GAME_MENU;
	}
	
	public boolean isVisibleInStatistics()
	{
		return WURST.isEnabled()
			&& location.getSelected() == Location.STATISTICS;
	}
	
	private enum Location
	{
		GAME_MENU("暂停菜单"),
		STATISTICS("统计信息");
		
		private final String name;
		
		private Location(String name)
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
