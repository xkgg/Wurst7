/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filters;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.PufferfishEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.wurstclient.util.MobDisposition;

public final class FilterNeutralSetting extends AttackDetectingEntityFilter
{
	private FilterNeutralSetting(String description, Mode selected,
		boolean checked)
	{
		super("过滤中立生物", description, selected, checked);
	}
	
	public FilterNeutralSetting(String description, Mode selected)
	{
		this(description, selected, false);
	}
	
	@Override
	public boolean onTest(Entity e)
	{
		return !(e instanceof MobEntity mob
			&& MobDisposition.of(mob) == MobDisposition.NEUTRAL);
	}
	
	@Override
	public boolean ifCalmTest(Entity e)
	{
		if(!(e instanceof MobEntity mob)
			|| MobDisposition.of(mob) != MobDisposition.NEUTRAL)
			return true;
		
		if(e instanceof PufferfishEntity pfe)
			return pfe.getPuffState() > 0;
		
		if(e instanceof Angerable angerable && angerable.hasAngerTime())
			return true;
		
		if(e instanceof WolfEntity wolf && wolf.isAttacking())
			return true;
		
		if(e instanceof BeeEntity bee && bee.isAttacking())
			return true;
		
		if(e instanceof EndermanEntity enderman
			&& (enderman.isAngry() || enderman.isAttacking()))
			return true;
		
		return mob.isAttacking();
	}
	
	public static FilterNeutralSetting genericCombat(Mode selected)
	{
		return new FilterNeutralSetting(
			"description.wurst.setting.generic.filter_neutral_combat",
			selected);
	}
	
	public static FilterNeutralSetting genericVision(Mode selected)
	{
		return new FilterNeutralSetting(
			"description.wurst.setting.generic.filter_neutral_vision",
			selected);
	}
	
	public static FilterNeutralSetting onOffOnly(String description,
		boolean onByDefault)
	{
		return new FilterNeutralSetting(description, null, onByDefault);
	}
}
