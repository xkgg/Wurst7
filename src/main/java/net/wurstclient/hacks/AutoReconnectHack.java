/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"自动重连", "auto reconnect", "AutoRejoin", "auto rejoin"})
@DontBlock
public final class AutoReconnectHack extends Hack
{
	private final SliderSetting waitTime =
		new SliderSetting("等待时间", "重连前的等待时间（秒）。",
			5, 0, 60, 0.5, ValueDisplay.DECIMAL.withSuffix("秒"));
	
	public AutoReconnectHack()
	{
		super("自动重连");
		setCategory(Category.OTHER);
		addSetting(waitTime);
	}
	
	public int getWaitTicks()
	{
		return (int)(waitTime.getValue() * 20);
	}
	
	// See DisconnectedScreenMixin
}
