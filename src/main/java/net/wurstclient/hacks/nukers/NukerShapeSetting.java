/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.nukers;

import net.wurstclient.settings.EnumSetting;

public final class NukerShapeSetting
	extends EnumSetting<NukerShapeSetting.NukerShape>
{
	public NukerShapeSetting()
	{
		super("形状",
			"§l注意:§r 如果你的范围设置过高，立方体形状" + "会开始看起来像一个球体，因为你无法到达" + "角落。范围1-3最适合立方体形状。",
			NukerShape.values(), NukerShape.SPHERE);
	}
	
	public enum NukerShape
	{
		SPHERE("球体"),
		CUBE("立方体");
		
		private final String name;
		
		private NukerShape(String name)
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
