/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Arrays;
import java.util.Objects;

import net.minecraft.block.entity.BlockEntity;

/**
 * Detects Lootr block entities without making Lootr a required dependency.
 */
public enum LootrModCompat
{
	;
	
	private static final Class<?>[] BARRELS = getClassesIfPresent(
		"net.zestyblaze.lootr.block.entities.LootrBarrelBlockEntity",
		"noobanidus.mods.lootr.block.entities.LootrBarrelBlockEntity",
		"noobanidus.mods.lootr.common.block.entity.LootrBarrelBlockEntity");
	private static final Class<?>[] SHULKER_BOXES = getClassesIfPresent(
		"net.zestyblaze.lootr.block.entities.LootrShulkerBlockEntity",
		"noobanidus.mods.lootr.block.entities.LootrShulkerBlockEntity",
		"noobanidus.mods.lootr.common.block.entity.LootrShulkerBlockEntity",
		"noobanidus.mods.lootr.common.block.entity.LootrShulkerBoxBlockEntity");
	private static final Class<?>[] TRAPPED_CHESTS = getClassesIfPresent(
		"net.zestyblaze.lootr.block.entities.LootrTrappedChestBlockEntity",
		"noobanidus.mods.lootr.block.entities.LootrTrappedChestBlockEntity",
		"noobanidus.mods.lootr.common.block.entity.LootrTrappedChestBlockEntity");
	
	public static boolean isLootrBarrel(BlockEntity blockEntity)
	{
		return isInstanceOfAny(blockEntity, BARRELS);
	}
	
	public static boolean isLootrShulkerBox(BlockEntity blockEntity)
	{
		return isInstanceOfAny(blockEntity, SHULKER_BOXES);
	}
	
	public static boolean isLootrTrappedChest(BlockEntity blockEntity)
	{
		return isInstanceOfAny(blockEntity, TRAPPED_CHESTS);
	}
	
	private static boolean isInstanceOfAny(BlockEntity blockEntity,
		Class<?>[] classes)
	{
		if(blockEntity == null)
			return false;
		
		for(Class<?> type : classes)
			if(type.isInstance(blockEntity))
				return true;
			
		return false;
	}
	
	private static Class<?>[] getClassesIfPresent(String... names)
	{
		return Arrays.stream(names).map(LootrModCompat::getClassIfPresent)
			.filter(Objects::nonNull).toArray(Class<?>[]::new);
	}
	
	private static Class<?> getClassIfPresent(String name)
	{
		try
		{
			return Class.forName(name, false,
				LootrModCompat.class.getClassLoader());
			
		}catch(ClassNotFoundException | LinkageError e)
		{
			return null;
		}
	}
}
