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

@DontBlock
@SearchTags({"隐私", "数据", "跟踪", "窥探", "间谍软件", "privacy", "data", "tracking", "snooper", "spyware"})
public final class NoTelemetryOtf extends OtherFeature
{
	private final CheckboxSetting disableTelemetry =
		new CheckboxSetting("禁用遥测", true);
	
	public NoTelemetryOtf()
	{
		super("无遥测",
			"禁用Mojang在22w46a中引入的\"必需\"遥测。事实证明，它根本不是必需的。");
		addSetting(disableTelemetry);
	}
	
	@Override
	public boolean isEnabled()
	{
		return disableTelemetry.isChecked();
	}
	
	@Override
	public String getPrimaryAction()
	{
		return isEnabled() ? "重新启用遥测" : "禁用遥测";
	}
	
	@Override
	public void doPrimaryAction()
	{
		disableTelemetry.setChecked(!disableTelemetry.isChecked());
	}
	
	// See TelemetrySenderMixin
}
