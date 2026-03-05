/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filters;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PiglinEntity;

public final class FilterPiglinsSetting extends AttackDetectingEntityFilter
{
	private static final String EXCEPTIONS_TEXT =
		"\n\nThis filter does not affect piglin brutes.";
	
	private FilterPiglinsSetting(String description, Mode selected,
		boolean checked)
	{
		super("过滤猪灵", description + EXCEPTIONS_TEXT, selected,
			checked);
	}
	
	public FilterPiglinsSetting(String description, Mode selected)
	{
		this(description, selected, false);
	}
	
	@Override
	public boolean onTest(Entity e)
	{
		return !(e instanceof PiglinEntity);
	}
	
	@Override
	public boolean ifCalmTest(Entity e)
	{
		return !(e instanceof PiglinEntity pe) || pe.isAttacking();
	}
	
	public static FilterPiglinsSetting genericCombat(Mode selected)
	{
		return new FilterPiglinsSetting("当设置为 \u00a7lOn\u00a7r,"
			+ " 猪灵根本不会受到攻击。\n\n"
			+ "当设置为 \u00a7lIf calm\u00a7r, 猪灵不会被攻击"
			+ " 直到他们先发起攻击。请注意，这个过滤器无法"
			+ " 检测猪灵是否正在攻击你或其他人。\n\n"
			+ "当设置为\u00a7lOff\u00a7r, 这个过滤器没有任何作用"
			+ " 猪灵可以被攻击。", selected);
	}
	
	public static FilterPiglinsSetting genericVision(Mode selected)
	{
		return new FilterPiglinsSetting("When set to \u00a7lOn\u00a7r,"
			+ " piglins won't be shown at all.\n\n"
			+ "When set to \u00a7lIf calm\u00a7r, piglins won't be shown until"
			+ " they attack something.\n\n"
			+ "When set to \u00a7lOff\u00a7r, this filter does nothing and"
			+ " piglins can be shown.", selected);
	}
	
	public static FilterPiglinsSetting onOffOnly(String description,
		boolean onByDefault)
	{
		return new FilterPiglinsSetting(description, null, onByDefault);
	}
}

