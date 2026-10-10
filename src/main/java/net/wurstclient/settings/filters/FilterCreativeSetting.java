/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filters;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public final class FilterCreativeSetting extends EntityFilterCheckbox
{
	public FilterCreativeSetting(String description, boolean checked)
	{
		super("过滤创造模式玩家", description, checked);
	}
	
	@Override
	public boolean test(Entity e)
	{
		return !(e instanceof PlayerEntity player && player.isCreative());
	}
	
	public static FilterCreativeSetting genericCombat(boolean checked)
	{
		return new FilterCreativeSetting(
			"description.wurst.setting.generic.filter_creative_combat",
			checked);
	}
	
	public static FilterCreativeSetting genericVision(boolean checked)
	{
		return new FilterCreativeSetting(
			"description.wurst.setting.generic.filter_creative_vision",
			checked);
	}
}
