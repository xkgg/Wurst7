/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filters;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.InteractionEntity;

public final class FilterInteractionsSetting extends EntityFilterCheckbox
{
	public FilterInteractionsSetting(String description, boolean checked)
	{
		super("过滤交互实体", description, checked);
	}
	
	@Override
	public boolean test(Entity e)
	{
		return !(e instanceof InteractionEntity);
	}
	
	public static FilterInteractionsSetting genericCombat(boolean checked)
	{
		return new FilterInteractionsSetting(
			"description.wurst.setting.generic.filter_interactions_combat",
			checked);
	}
	
	public static FilterInteractionsSetting genericVision(boolean checked)
	{
		return new FilterInteractionsSetting(
			"description.wurst.setting.generic.filter_interactions_vision",
			checked);
	}
	
	public static FilterInteractionsSetting follow()
	{
		return new FilterInteractionsSetting(
			"description.wurst.setting.follow.filter_interactions", true);
	}
	
	public static FilterInteractionsSetting remoteView()
	{
		return new FilterInteractionsSetting(
			"description.wurst.setting.remoteview.filter_interactions", true);
	}
}
