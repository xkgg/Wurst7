/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filters;

import net.minecraft.entity.Entity;
import net.minecraft.entity.Bucketable;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.CamelEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.entity.Saddleable;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.passive.TadpoleEntity;
import net.minecraft.entity.passive.TameableEntity;

public final class FilterPetsSetting extends EntityFilterCheckbox
{
	public FilterPetsSetting(String description, boolean checked)
	{
		super("过滤宠物", description, checked);
	}
	
	@Override
	public boolean test(Entity e)
	{
		// Tadpoles report that they came from a bucket even when wild.
		if(e instanceof TadpoleEntity)
			return true;
		
		if(e instanceof Bucketable bucketable && bucketable.isFromBucket())
			return false;
		
		if(e instanceof SnowGolemEntity
			|| e instanceof IronGolemEntity golem && golem.isPlayerCreated())
			return false;
		
		return !(e instanceof TameableEntity && ((TameableEntity)e).isTamed())
			&& !(e instanceof AbstractHorseEntity && !(e instanceof CamelEntity)
				&& ((AbstractHorseEntity)e).isTame())
			&& !(e instanceof Saddleable saddleable && saddleable.isSaddled()
				&& (e instanceof CamelEntity || e instanceof StriderEntity
					|| e instanceof PigEntity))
			&& !(e instanceof AllayEntity)
			&& !(e instanceof OcelotEntity ocelot && ocelot.isTrusting())
			&& !(e instanceof FoxEntity fox
				&& !fox.getTrustedUuids().isEmpty());
	}
	
	public static FilterPetsSetting genericCombat(boolean checked)
	{
		return new FilterPetsSetting(
			"description.wurst.setting.generic.filter_pets_combat", checked);
	}
	
	public static FilterPetsSetting genericVision(boolean checked)
	{
		return new FilterPetsSetting(
			"description.wurst.setting.generic.filter_pets_vision", checked);
	}
}
