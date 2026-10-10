/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import net.wurstclient.util.text.WText;

public final class TakeItemsFromSetting
	extends EnumSetting<TakeItemsFromSetting.TakeItemsFrom>
{
	private static final WText FULL_DESCRIPTION_SUFFIX =
		buildDescriptionSuffix(true);
	private static final WText REDUCED_DESCRIPTION_SUFFIX =
		buildDescriptionSuffix(false);
	
	private TakeItemsFromSetting(WText description, TakeItemsFrom[] values,
		TakeItemsFrom selected)
	{
		super("物品来源", description, values, selected);
	}
	
	public static TakeItemsFromSetting withHands(WText description,
		TakeItemsFrom selected)
	{
		return new TakeItemsFromSetting(
			description.append(FULL_DESCRIPTION_SUFFIX), TakeItemsFrom.values(),
			selected);
	}
	
	public static TakeItemsFromSetting withoutHands(WText description,
		TakeItemsFrom selected)
	{
		TakeItemsFrom[] values =
			{TakeItemsFrom.HOTBAR, TakeItemsFrom.INVENTORY};
		return new TakeItemsFromSetting(
			description.append(REDUCED_DESCRIPTION_SUFFIX), values, selected);
	}
	
	public int getMaxInvSlot()
	{
		return getSelected().getMaxInvSlot();
	}
	
	private static WText buildDescriptionSuffix(boolean includeHands)
	{
		WText text = WText.literal("\n\n");
		TakeItemsFrom[] values =
			includeHands ? TakeItemsFrom.values() : new TakeItemsFrom[]{
				TakeItemsFrom.HOTBAR, TakeItemsFrom.INVENTORY};
		
		for(TakeItemsFrom value : values)
			text.append("\u00a7l" + value + "\u00a7r - ")
				.append(value.description).append("\n\n");
		
		return text;
	}
	
	public enum TakeItemsFrom
	{
		HANDS("手部", 0),
		HOTBAR("快捷栏", 9),
		INVENTORY("背包", 36);
		
		private static final String TRANSLATION_KEY_PREFIX =
			"description.wurst.setting.generic.take_items_from.";
		
		private final String name;
		private final WText description;
		private final int maxInvSlot;
		
		private TakeItemsFrom(String name, int maxInvSlot)
		{
			this.name = name;
			this.maxInvSlot = maxInvSlot;
			description =
				WText.translated(TRANSLATION_KEY_PREFIX + name().toLowerCase());
		}
		
		public int getMaxInvSlot()
		{
			return maxInvSlot;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
