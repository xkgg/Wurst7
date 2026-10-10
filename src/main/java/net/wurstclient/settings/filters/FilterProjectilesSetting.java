/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filters;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.AbstractFireballEntity;

public final class FilterProjectilesSetting extends EntityFilterCheckbox
{
	public FilterProjectilesSetting(String description, boolean checked)
	{
		super("过滤可反弹火球", description, checked);
	}
	
	@Override
	public boolean test(Entity e)
	{
		return !(e instanceof AbstractFireballEntity);
	}
	
	public static FilterProjectilesSetting genericCombat(boolean checked)
	{
		return new FilterProjectilesSetting(
			"description.wurst.setting.generic.filter_projectiles_combat",
			checked);
	}
}
