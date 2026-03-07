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

public final class AnchorAuraFilterList extends EntityFilterList
{
	private AnchorAuraFilterList(List<EntityFilter> filters)
	{
		super(filters);
	}
	
	public static AnchorAuraFilterList create()
	{
		ArrayList<EntityFilter> builder = new ArrayList<>();
		String damageWarning =
			"\n\n如果它们离有效目标或现有锚太近，仍然会受到伤害。";
		
		builder.add(new FilterPlayersSetting(
			"自动放置锚时不会攻击其他玩家。"
				+ damageWarning,
			false));
		
		builder.add(new FilterHostileSetting("自动放置锚时不会攻击敌对生物，如僵尸和爬行者。"
			+ damageWarning, true));
		
		builder.add(new FilterNeutralSetting("自动放置锚时不会攻击中立生物，如末影人和狼。" + damageWarning,
			AttackDetectingEntityFilter.Mode.ON));
		
		builder.add(new FilterPassiveSetting("自动放置锚时不会攻击动物，如猪和牛，环境生物，如蝙蝠，以及水生生物，如鱼、鱿鱼和海豚。" + damageWarning,
			true));
		
		builder.add(new FilterPassiveWaterSetting("自动放置锚时不会攻击被动水生生物，如鱼、鱿鱼、海豚和蝾螈。" + damageWarning, true));
		
		builder.add(new FilterBatsSetting("自动放置锚时不会攻击蝙蝠和任何其他\"环境\"生物。" + damageWarning,
			true));
		
		builder.add(new FilterSlimesSetting("自动放置锚时不会攻击史莱姆。" + damageWarning, true));
		
		builder.add(new FilterVillagersSetting("自动放置锚时不会攻击村民和流浪商人。" + damageWarning,
			true));
		
		builder.add(new FilterZombieVillagersSetting("自动放置锚时不会攻击僵尸村民。" + damageWarning, true));
		
		builder.add(new FilterGolemsSetting("自动放置锚时不会攻击铁傀儡和雪傀儡。" + damageWarning, true));
		
		builder.add(new FilterPiglinsSetting(
			"自动放置锚时不会攻击猪灵。",
			AttackDetectingEntityFilter.Mode.ON));
		
		builder.add(new FilterZombiePiglinsSetting("自动放置锚时不会攻击僵尸猪灵。" + damageWarning,
			AttackDetectingEntityFilter.Mode.ON));
		
		builder.add(new FilterShulkersSetting("自动放置锚时不会攻击潜影贝。" + damageWarning, true));
		
		builder.add(new FilterAllaysSetting(
			"自动放置锚时不会攻击悦灵。" + damageWarning,
			true));
		
		builder.add(new FilterInvisibleSetting(
			"自动放置锚时不会攻击隐形实体。"
				+ damageWarning,
			false));
		
		builder.add(new FilterNamedSetting(
			"自动放置锚时不会攻击有命名牌的实体。"
				+ damageWarning,
			false));
		
		builder.add(new FilterArmorStandsSetting(
			"自动放置锚时不会攻击盔甲架。"
				+ damageWarning,
			true));
		
		return new AnchorAuraFilterList(builder);
	}
}
