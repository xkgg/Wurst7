/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filters;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.wurstclient.util.MobDisposition;

public final class FilterHostileSetting extends EntityFilterCheckbox
{
	private static final String EXCEPTIONS_TEXT =
		"\n\n此过滤器不影响中立生物，如末影人、猪灵和僵尸猪灵。";
	
	public FilterHostileSetting(String description, boolean checked)
	{
		super("过滤敌意生物", description + EXCEPTIONS_TEXT, checked);
	}
	
	@Override
	public boolean test(Entity e)
	{
		return !(e instanceof MobEntity mob
			&& MobDisposition.of(mob) == MobDisposition.HOSTILE);
	}
	
	public static FilterHostileSetting genericCombat(boolean checked)
	{
		return new FilterHostileSetting("不会攻击像僵尸和爬行者这样的敌对生物.", checked);
	}
	
	public static FilterHostileSetting genericVision(boolean checked)
	{
		return new FilterHostileSetting("不会显示敌意生物如僵尸和苦力怕.", checked);
	}
}
