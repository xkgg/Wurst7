/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"关闭", "隐藏Wurst标志", "幽灵模式", "隐身模式",
	"原版Minecraft"})
@DontBlock
public final class DisableOtf extends OtherFeature
{
	private final CheckboxSetting hideEnableButton = new CheckboxSetting(
		"隐藏启用按钮",
		"当你关闭统计信息屏幕后，移除\"启用Wurst\"按钮。"
			+ " 你将需要重新启动游戏来重新启用Wurst。",
		false);
	
	public DisableOtf()
	{
		super("关闭Wurst",
			"要关闭Wurst，请前往统计信息屏幕并按\"关闭Wurst\"按钮。\n"
				+ "按下后，它将变成\"启用Wurst\"按钮。");
		addSetting(hideEnableButton);
	}
	
	public boolean shouldHideEnableButton()
	{
		return !WURST.isEnabled() && hideEnableButton.isChecked();
	}
}
