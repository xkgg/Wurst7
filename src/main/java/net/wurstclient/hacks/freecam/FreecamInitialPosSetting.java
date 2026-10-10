/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.freecam;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.text.WText;

public final class FreecamInitialPosSetting
	extends EnumSetting<FreecamInitialPosSetting.InitialPosition>
{
	private static final WText DESCRIPTION = buildDescription();
	
	public FreecamInitialPosSetting()
	{
		super("初始位置", DESCRIPTION, InitialPosition.values(),
			InitialPosition.INSIDE);
	}
	
	private static WText buildDescription()
	{
		WText text = WText
			.translated("description.wurst.setting.freecam.initial_position");
		
		for(InitialPosition value : InitialPosition.values())
			text = text
				.append(WText.literal("\n\n\u00a7l" + value.name + ":\u00a7r "))
				.append(value.description);
		
		return text;
	}
	
	public enum InitialPosition
	{
		INSIDE("当前位置")
		{
			@Override
			public Vec3d getOffset(Entity cameraEntity)
			{
				return Vec3d.ZERO;
			}
		},
		
		IN_FRONT("前方")
		{
			@Override
			public Vec3d getOffset(Entity cameraEntity)
			{
				double distance = 0.55;
				float yawRad =
					cameraEntity.getYaw() * MathHelper.RADIANS_PER_DEGREE;
				double offsetX = -MathHelper.sin(yawRad) * distance;
				double offsetZ = MathHelper.cos(yawRad) * distance;
				return new Vec3d(offsetX, 0, offsetZ);
			}
		},
		
		ABOVE("上方")
		{
			@Override
			public Vec3d getOffset(Entity cameraEntity)
			{
				double distance = 0.55;
				return new Vec3d(0, distance, 0);
			}
		};
		
		private static final String TRANSLATION_KEY_PREFIX =
			"description.wurst.setting.freecam.initial_position.";
		
		private final String name;
		private final WText description;
		
		private InitialPosition(String name)
		{
			this.name = name;
			description =
				WText.translated(TRANSLATION_KEY_PREFIX + name().toLowerCase());
		}
		
		public abstract Vec3d getOffset(Entity cameraEntity);
		
		public Vec3d getPos(Entity cameraEntity)
		{
			return cameraEntity.getCameraPosVec(1.0F)
				.add(getOffset(cameraEntity));
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
