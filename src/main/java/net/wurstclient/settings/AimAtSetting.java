/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import java.util.function.Function;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.text.WText;

public final class AimAtSetting extends EnumSetting<AimAtSetting.AimAt>
{
	private static final WText FULL_DESCRIPTION_SUFFIX =
		buildDescriptionSuffix();
	
	private AimAtSetting(String name, WText description, AimAt[] values,
		AimAt selected)
	{
		super(name, description, values, selected);
	}
	
	public AimAtSetting(WText description, AimAt selected)
	{
		this("瞄准位置", description.append(FULL_DESCRIPTION_SUFFIX),
			AimAt.values(), selected);
	}
	
	public AimAtSetting(String name, String description, AimAt selected)
	{
		this(name, WText.literal(description).append(FULL_DESCRIPTION_SUFFIX),
			AimAt.values(), selected);
	}
	
	public AimAtSetting(String description, AimAt selected)
	{
		this("瞄准位置", description, selected);
	}
	
	public static WText genericDescription(Hack hack)
	{
		return WText.translated("description.wurst.setting.generic.aim_at",
			hack.getName());
	}
	
	public Vec3d getAimPoint(Entity e)
	{
		return getSelected().aimFunction.apply(e);
	}
	
	private static WText buildDescriptionSuffix()
	{
		WText text = WText.literal("\n\n");
		
		for(AimAt value : AimAt.values())
			text.append("\u00a7l" + value.name + "\u00a7r - ")
				.append(value.description).append("\n\n");
		
		return text;
	}
	
	private static Box getInsetBox(Entity e)
	{
		Box box = e.getBoundingBox();
		return box.contract(Math.min(0.05, box.getXLength() / 2),
			Math.min(0.05, box.getYLength() / 2),
			Math.min(0.05, box.getZLength() / 2));
	}
	
	private static Vec3d aimAtClosestPoint(Entity e)
	{
		Box box = getInsetBox(e);
		Vec3d eyes = WurstClient.MC.player.getEyePos();
		
		if(box.contains(eyes))
			return eyes;
		
		double clampedX = MathHelper.clamp(eyes.x, box.minX, box.maxX);
		double clampedY = MathHelper.clamp(eyes.y, box.minY, box.maxY);
		double clampedZ = MathHelper.clamp(eyes.z, box.minZ, box.maxZ);
		
		return new Vec3d(clampedX, clampedY, clampedZ);
	}
	
	private static Vec3d aimAtHead(Entity e)
	{
		float eyeHeight = e.getEyeHeight(e.getPose());
		return e.getPos().add(0, eyeHeight, 0);
	}
	
	private static Vec3d aimAtCenter(Entity e)
	{
		return e.getBoundingBox().getCenter();
	}
	
	private static Vec3d aimAtFeet(Entity e)
	{
		Box box = getInsetBox(e);
		return new Vec3d((box.minX + box.maxX) / 2, box.minY,
			(box.minZ + box.maxZ) / 2);
	}
	
	public enum AimAt
	{
		AUTO("自动", AimAtSetting::aimAtClosestPoint),
		
		HEAD("头部", AimAtSetting::aimAtHead),
		
		CENTER("中心", AimAtSetting::aimAtCenter),
		
		FEET("脚部", AimAtSetting::aimAtFeet);
		
		private static final String TRANSLATION_KEY_PREFIX =
			"description.wurst.setting.generic.aim_at.";
		
		private final String name;
		private final WText description;
		private final Function<Entity, Vec3d> aimFunction;
		
		private AimAt(String name, Function<Entity, Vec3d> aimFunction)
		{
			this.name = name;
			description =
				WText.translated(TRANSLATION_KEY_PREFIX + name().toLowerCase());
			this.aimFunction = aimFunction;
		}
		
		public Vec3d getAimPoint(Entity e)
		{
			return aimFunction.apply(e);
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
