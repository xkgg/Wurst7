/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.freecam;

import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.text.WText;

public final class FreecamInteractFromSetting
	extends EnumSetting<FreecamInteractFromSetting.InteractFrom>
{
	private static final WText DESCRIPTION = buildDescription();
	
	public FreecamInteractFromSetting(InteractFrom selected)
	{
		super("交互来源", DESCRIPTION, InteractFrom.values(), selected);
	}
	
	private static WText buildDescription()
	{
		WText text =
			WText.translated("description.wurst.setting.freecam.interact_from");
		
		for(InteractFrom value : InteractFrom.values())
			text = text
				.append(WText.literal("\n\n\u00a7l" + value.name + ":\u00a7r "))
				.append(value.description);
		
		return text;
	}
	
	public enum InteractFrom
	{
		CAMERA("摄像机"),
		PLAYER("玩家");
		
		private static final String TRANSLATION_KEY_PREFIX =
			"description.wurst.setting.generic.interact_from.";
		
		private final String name;
		private final WText description;
		
		private InteractFrom(String name)
		{
			this.name = name;
			description =
				WText.translated(TRANSLATION_KEY_PREFIX + name().toLowerCase());
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
