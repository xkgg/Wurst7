/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filterlists;

import java.util.ArrayList;
import java.util.List;

import net.wurstclient.settings.filters.*;

public final class RemoteViewFilterList extends EntityFilterList
{
	private RemoteViewFilterList(List<EntityFilter> filters)
	{
		super(filters);
	}
	
	public static RemoteViewFilterList create()
	{
		ArrayList<EntityFilter> builder = new ArrayList<>();
		
		builder.add(new FilterPlayersSetting(
			"description.wurst.setting.remoteview.filter_players", false));
		
		builder.add(new FilterSleepingSetting(
			"description.wurst.setting.remoteview.filter_sleeping", false));
		
		builder.add(new FilterFlyingSetting(
			"description.wurst.setting.remoteview.filter_flying", 0));
		
		builder.add(new FilterHostileSetting(
			"不会查看敌对生物，如僵尸和爬行者。", true));
		
		builder.add(FilterNeutralSetting.onOffOnly(
			"description.wurst.setting.remoteview.filter_neutral", true));
		
		builder.add(new FilterPassiveSetting("不会查看动物，如猪和牛，环境生物，如蝙蝠，以及水生生物，如鱼、鱿鱼"
			+ "和海豚。", true));
		
		builder.add(new FilterPassiveWaterSetting("不会查看被动水生生物，如鱼、鱿鱼、海豚和蝾螈。", true));
		
		builder.add(new FilterBabiesSetting(
			"不会查看小猪、小村民等。", true));
		
		builder.add(new FilterBatsSetting(
			"description.wurst.setting.remoteview.filter_bats", true));
		
		builder.add(new FilterSlimesSetting("不会查看史莱姆。", true));
		
		builder.add(new FilterPetsSetting(
			"description.wurst.setting.remoteview.filter_pets", true));
		
		builder.add(new FilterVillagersSetting(
			"description.wurst.setting.remoteview.filter_villagers", true));
		
		builder.add(new FilterZombieVillagersSetting(
			"description.wurst.setting.remoteview.filter_zombie_villagers",
			true));
		
		builder.add(new FilterGolemsSetting(
			"description.wurst.setting.remoteview.filter_golems", true));
		
		builder
			.add(FilterPiglinsSetting.onOffOnly("不会查看猪灵。", true));
		
		builder.add(FilterZombiePiglinsSetting.onOffOnly(
			"description.wurst.setting.remoteview.filter_zombie_piglins",
			true));
		
		builder.add(FilterEndermenSetting.onOffOnly(
			"description.wurst.setting.remoteview.filter_endermen", true));
		
		builder.add(new FilterShulkersSetting(
			"description.wurst.setting.remoteview.filter_shulkers", true));
		
		builder.add(new FilterAllaysSetting(
			"description.wurst.setting.remoteview.filter_allays", true));
		
		builder.add(new FilterInvisibleSetting(
			"description.wurst.setting.remoteview.filter_invisible", false));
		
		builder.add(new FilterArmorStandsSetting(
			"description.wurst.setting.remoteview.filter_armor_stands", true));
		
		return new RemoteViewFilterList(builder);
	}
}
