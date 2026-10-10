/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autofarm;

import java.util.Objects;
import java.util.stream.Stream;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.wurstclient.settings.PlantTypeSetting;
import net.wurstclient.util.text.WText;

public abstract class AutoFarmPlantType
{
	private final PlantTypeSetting setting;
	
	protected AutoFarmPlantType(String name, WText description, Item icon,
		boolean harvest, boolean replant)
	{
		setting = Objects.requireNonNull(
			new PlantTypeSetting(name, description, icon, harvest, replant));
	}
	
	public final PlantTypeSetting getSetting()
	{
		return setting;
	}
	
	public final boolean isHarvestingEnabled()
	{
		return setting.isHarvestingEnabled();
	}
	
	public final boolean isReplantingEnabled()
	{
		return setting.isReplantingEnabled();
	}
	
	public final Stream<PlantTypeSetting> getSettings()
	{
		return Stream.of(setting);
	}
	
	public abstract boolean isReplantingSpot(BlockPos pos, BlockState state);
	
	public abstract boolean hasPlantingSurface(BlockPos pos);
	
	public abstract Item getSeedItem();
	
	public abstract boolean shouldHarvestByMining(BlockPos pos,
		BlockState state);
	
	public boolean shouldHarvestByInteracting(BlockPos pos, BlockState state)
	{
		return false;
	}
}
