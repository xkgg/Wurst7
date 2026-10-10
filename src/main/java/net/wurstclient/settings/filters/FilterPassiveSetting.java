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

public final class FilterPassiveSetting extends EntityFilterCheckbox
{
	private static final String EXCEPTIONS_TEXT = "\n\n此过滤器不影响狼、蜜蜂、北极熊和河豚。";
	
	public FilterPassiveSetting(String description, boolean checked)
	{
		super("过滤有好生物", description + EXCEPTIONS_TEXT, checked);
	}
	
	@Override
	public boolean test(Entity e)
	{
		return !(e instanceof MobEntity mob
			&& MobDisposition.of(mob) == MobDisposition.PASSIVE);
	}
	
	public static FilterPassiveSetting genericCombat(boolean checked)
	{
		return new FilterPassiveSetting(
			"不会攻击像猪这样的动物" + " 奶牛、像蝙蝠这样的环境生物，以及像鱼、乌贼这样的水生生物" + " 和海豚.", checked);
	}
	
	public static FilterPassiveSetting genericVision(boolean checked)
	{
		return new FilterPassiveSetting(
			"不会展示像猪这样的动物" + " 奶牛、像蝙蝠这样的环境生物，以及像鱼、乌贼这样的水生生物" + " 和海豚.", checked);
	}
}
