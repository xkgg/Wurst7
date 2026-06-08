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
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"turn off", "hide wurst logo", "ghost mode", "stealth mode",
	"vanilla Minecraft"})
@DontBlock
public final class DisableOtf extends OtherFeature
{
	private final CheckboxSetting hideEnableButton = new CheckboxSetting(
		"隐藏启用按钮", "关闭统计界面后自动移除\"启用Wurst\"按钮。" + "您需要重新启动游戏才能重新启用Wurst。", false);
	
	public DisableOtf()
	{
		super("禁用Wurst",
			"要禁用Wurst，请前往统计界面并按\"禁用Wurst\"按钮。\n" + "按下后按钮将变为\"启用Wurst\"。");
		addSetting(hideEnableButton);
	}
	
	public boolean shouldHideEnableButton()
	{
		return !WURST.isEnabled() && hideEnableButton.isChecked();
	}
}
