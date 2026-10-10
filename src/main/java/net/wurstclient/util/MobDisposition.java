/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.DolphinEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.entity.passive.PufferfishEntity;
import net.minecraft.entity.passive.RabbitEntity;

public enum MobDisposition
{
	PASSIVE,
	NEUTRAL,
	HOSTILE;
	
	public static MobDisposition of(MobEntity mob)
	{
		if(mob instanceof IronGolemEntity golem)
			return golem.isPlayerCreated() ? PASSIVE : NEUTRAL;
		
		if(mob instanceof PandaEntity panda)
			return panda.getMainGene() == PandaEntity.Gene.AGGRESSIVE ? NEUTRAL
				: PASSIVE;
		
		if(mob instanceof PiglinEntity piglin)
			return piglin.isBaby() ? PASSIVE : NEUTRAL;
		
		if(mob instanceof RabbitEntity rabbit)
			return rabbit.getVariant() == RabbitEntity.RabbitType.EVIL ? HOSTILE
				: PASSIVE;
		
		if(mob instanceof SlimeEntity slime
			&& !(mob instanceof MagmaCubeEntity))
			return slime.isSmall() ? PASSIVE : HOSTILE;
		
		// Their retaliation is too limited to make them a combat threat.
		if(mob instanceof DolphinEntity || mob instanceof LlamaEntity)
			return PASSIVE;
		
		if(mob instanceof PufferfishEntity || mob instanceof GoatEntity
			|| mob instanceof Angerable)
			return NEUTRAL;
		
		return mob instanceof Monster ? HOSTILE : PASSIVE;
	}
}
