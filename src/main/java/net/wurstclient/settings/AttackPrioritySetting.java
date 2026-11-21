/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import java.util.Comparator;
import java.util.function.Function;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.text.WText;

public final class AttackPrioritySetting
	extends EnumSetting<AttackPrioritySetting.AttackPriority>
{
	private static final WText FULL_DESCRIPTION_SUFFIX =
		buildDescriptionSuffix(true);
	private static final WText REDUCED_DESCRIPTION_SUFFIX =
		buildDescriptionSuffix(false);
	
	private AttackPrioritySetting(String name, WText description,
		AttackPriority[] values, AttackPriority selected)
	{
		super(name, description, values, selected);
	}
	
	public AttackPrioritySetting(String name, WText description,
		AttackPriority selected)
	{
		this(name, description.append(FULL_DESCRIPTION_SUFFIX),
			AttackPriority.values(), selected);
	}
	
	public AttackPrioritySetting(WText description, AttackPriority selected)
	{
		this("优先级", description, selected);
	}
	
	public static AttackPrioritySetting withoutAngleDistOption(String name,
		WText description, AttackPriority selected)
	{
		AttackPriority[] values = {AttackPriority.DISTANCE,
			AttackPriority.ANGLE, AttackPriority.HEALTH};
		return new AttackPrioritySetting(name,
			description.append(REDUCED_DESCRIPTION_SUFFIX), values, selected);
	}
	
	public static AttackPrioritySetting withoutAngleDistOption(
		WText description, AttackPriority selected)
	{
		return withoutAngleDistOption("优先级", description, selected);
	}
	
	public static WText genericDescription()
	{
		return WText
			.translated("description.wurst.setting.generic.attack_priority");
	}
	
	@Override
	public boolean setSelected(String selected)
	{
		return super.setSelected("生命值".equals(selected) ? "血量" : selected);
	}
	
	public Comparator<Entity> getComparator(Function<Entity, Vec3d> aimPoint)
	{
		return getSelected().getComparator(aimPoint);
	}
	
	private static WText buildDescriptionSuffix(boolean includeAngleDist)
	{
		WText text = WText.literal("\n\n");
		AttackPriority[] values = includeAngleDist ? AttackPriority.values()
			: new AttackPriority[]{AttackPriority.DISTANCE,
				AttackPriority.ANGLE, AttackPriority.HEALTH};
		
		for(AttackPriority value : values)
			text.append("\u00a7l" + value.name + "\u00a7r - ")
				.append(value.description).append("\n\n");
		
		return text;
	}
	
	public enum AttackPriority
	{
		DISTANCE("距离"),
		ANGLE("角度"),
		ANGLE_DIST("角度+距离"),
		HEALTH("血量");
		
		private static final String TRANSLATION_KEY_PREFIX =
			"description.wurst.setting.generic.attack_priority.";
		
		private final String name;
		private final WText description;
		
		private AttackPriority(String name)
		{
			this.name = name;
			description =
				WText.translated(TRANSLATION_KEY_PREFIX + name().toLowerCase());
		}
		
		public Comparator<Entity> getComparator(
			Function<Entity, Vec3d> aimPoint)
		{
			return switch(this)
			{
				case DISTANCE -> Comparator
					.comparingDouble(EntityUtils::distanceToHitboxSq);
				
				case ANGLE -> Comparator.comparingDouble(e -> RotationUtils
					.getYawPitchDistanceToLookVec(aimPoint.apply(e)));
				
				case ANGLE_DIST -> Comparator.comparingDouble(e -> Math
					.pow(RotationUtils
						.getYawPitchDistanceToLookVec(aimPoint.apply(e)), 2)
					+ EntityUtils.distanceToHitboxSq(e));
				
				case HEALTH -> Comparator
					.comparingDouble(e -> e instanceof LivingEntity le
						? le.getHealth() : Integer.MAX_VALUE);
			};
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
