/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import java.awt.Color;
import java.util.function.BooleanSupplier;

import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"wurst logo", "top left corner"})
@DontBlock
public final class WurstLogoOtf extends OtherFeature
{
	private final ColorSetting bgColor = new ColorSetting("背景颜色",
		"背景颜色。\n" + "仅在\u00a76彩虹界面\u00a7r禁用时可见。", Color.WHITE);
	
	private final ColorSetting txtColor =
		new ColorSetting("文字颜色", "文字颜色。", Color.BLACK);
	
	private final EnumSetting<Visibility> visibility =
		new EnumSetting<>("可见性", Visibility.values(), Visibility.ALWAYS);
	
	public WurstLogoOtf()
	{
		super("Wurst标志", "在屏幕上显示Wurst标志和版本。");
		addSetting(bgColor);
		addSetting(txtColor);
		addSetting(visibility);
	}
	
	public boolean isVisible()
	{
		return visibility.getSelected().isVisible();
	}
	
	public int getBackgroundColor()
	{
		return bgColor.getColorI(128);
	}
	
	public int getTextColor()
	{
		return txtColor.getColorI();
	}
	
	public static enum Visibility
	{
		ALWAYS("始终", () -> true),
		
		ONLY_OUTDATED("仅过时显示", () -> WURST.getUpdater().isOutdated());
		
		private final String name;
		private final BooleanSupplier visible;
		
		private Visibility(String name, BooleanSupplier visible)
		{
			this.name = name;
			this.visible = visible;
		}
		
		public boolean isVisible()
		{
			return visible.getAsBoolean();
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
