/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.text.WText;

public final class FovSliderSetting extends SliderSetting
	implements Predicate<Vec3d>
{
	public FovSliderSetting(String name, WText description, double value)
	{
		super(name, description, value, 30, 360, 10, ValueDisplay.DEGREES);
	}
	
	public FovSliderSetting(WText description, double value)
	{
		this("FOV", description, value);
	}
	
	public static FovSliderSetting genericCombat(String name, double value)
	{
		return new FovSliderSetting(name,
			WText.translated("description.wurst.setting.generic.fov_combat"),
			value);
	}
	
	public static FovSliderSetting genericCombat(double value)
	{
		return genericCombat("FOV", value);
	}
	
	@Override
	public boolean test(Vec3d vec)
	{
		if(getValue() >= 360)
			return true;
		
		return RotationUtils.getAngleToLookVec(vec) <= getValue() / 2;
	}
	
	public Predicate<Entity> asEntityPredicate(
		Function<Entity, Vec3d> converter)
	{
		return e -> test(converter.apply(e));
	}
}
