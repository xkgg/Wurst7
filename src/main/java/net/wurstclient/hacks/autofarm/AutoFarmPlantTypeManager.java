/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autofarm;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.block.*;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.ToggleAllPlantTypesSetting;
import net.wurstclient.util.BlockUtils;

public final class AutoFarmPlantTypeManager
{
	private static final Direction[] HORIZONTAL_DIRECTIONS =
		{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	
	private final List<AutoFarmPlantType> plantTypes = new ArrayList<>();
	
	public final ToggleAllPlantTypesSetting toggleAllSetting;
	
	public AutoFarmPlantTypeManager()
	{
		add("紫水晶", Items.AMETHYST_SHARD, Items.BUDDING_AMETHYST, true, true,
			(pos, state) -> state.isOf(Blocks.BUDDING_AMETHYST), pos -> true,
			(pos, state) -> state.isOf(Blocks.AMETHYST_CLUSTER), never(),
			"紫水晶簇成熟后收获；紫水晶母岩位置用于补种。");
		add("竹子", Items.BAMBOO, Items.BAMBOO, true, true,
			AutoFarmPlantTypeManager::isBambooBase,
			pos -> BlockUtils.getState(pos.down())
				.isIn(BlockTags.BAMBOO_PLANTABLE_ON),
			(pos, state) -> state.isOf(Blocks.BAMBOO)
				&& isBambooBase(pos.down(), BlockUtils.getState(pos.down())),
			never(), "收获时保留竹子基部。");
		add("甜菜根", Items.BEETROOT, Items.BEETROOT_SEEDS, true, true,
			(pos, state) -> state.isOf(Blocks.BEETROOTS),
			AutoFarmPlantTypeManager::isFarmland,
			(pos, state) -> isMatureCrop(state, Blocks.BEETROOTS), never(),
			"成熟后收获，并可重新种植。");
		add("仙人掌", Items.CACTUS, Items.CACTUS, true, true,
			AutoFarmPlantTypeManager::isCactusBase,
			AutoFarmPlantTypeManager::hasCactusSurface,
			(pos, state) -> state.isOf(Blocks.CACTUS)
				&& isCactusBase(pos.down(), BlockUtils.getState(pos.down())),
			never(), "收获时保留仙人掌基部。");
		add("胡萝卜", Items.CARROT, Items.CARROT, true, true,
			(pos, state) -> state.isOf(Blocks.CARROTS),
			AutoFarmPlantTypeManager::isFarmland,
			(pos, state) -> isMatureCrop(state, Blocks.CARROTS), never(),
			"成熟后收获，并可重新种植。");
		add("紫颂植物", Items.CHORUS_FLOWER, Items.CHORUS_FLOWER, true, true,
			(pos,
				state) -> (state.isOf(Blocks.CHORUS_FLOWER)
					|| state.isOf(Blocks.CHORUS_PLANT)) && isEndStoneBelow(pos),
			AutoFarmPlantTypeManager::isEndStoneBelow,
			AutoFarmPlantTypeManager::shouldHarvestChorus, never(),
			"仅收获已成熟、被阻挡或不再支撑花朵的紫颂植物。");
		add("可可豆", Items.COCOA_BEANS, Items.COCOA_BEANS, true, true,
			(pos, state) -> state.getBlock() instanceof CocoaBlock,
			AutoFarmPlantTypeManager::hasJungleLog,
			(pos, state) -> state.getBlock() instanceof CocoaBlock
				&& state.get(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE,
			never(), "成熟后收获，并可在丛林原木旁重新种植。");
		add("发光浆果", Items.GLOW_BERRIES, Items.GLOW_BERRIES, true, true,
			(pos, state) -> state.getBlock() instanceof CaveVines
				&& hasPlantingSurface(pos),
			AutoFarmPlantTypeManager::hasVineCeiling,
			(pos, state) -> state.getBlock() instanceof CaveVines
				&& !isGlowBerryBase(pos, state),
			(pos, state) -> state.getBlock() instanceof CaveVines
				&& CaveVines.hasBerries(state) && isGlowBerryBase(pos, state),
			"对顶部浆果右键收获；必要时保留藤蔓基部。");
		add("海带", Items.KELP, Items.KELP, true, true,
			AutoFarmPlantTypeManager::isKelpBase,
			AutoFarmPlantTypeManager::hasKelpSurface,
			(pos, state) -> isKelp(state)
				&& isKelpBase(pos.down(), BlockUtils.getState(pos.down())),
			never(), "收获时保留海带基部。");
		add("西瓜", Items.MELON, Items.MELON_SEEDS, true, true,
			(pos, state) -> isMelonStem(state),
			AutoFarmPlantTypeManager::isFarmland,
			(pos, state) -> state.isOf(Blocks.MELON), never(), "收获西瓜方块，不破坏藤蔓。");
		add("下界疣", Items.NETHER_WART, Items.NETHER_WART, true, true,
			(pos, state) -> state.getBlock() instanceof NetherWartBlock,
			pos -> BlockUtils.getState(pos.down()).isOf(Blocks.SOUL_SAND),
			(pos, state) -> state.getBlock() instanceof NetherWartBlock
				&& state.get(NetherWartBlock.AGE) >= NetherWartBlock.AGE
					.getValues().size() - 1,
			never(), "成熟后收获，并可在灵魂沙上重新种植。");
		add("瓶子草", Items.PITCHER_PLANT, Items.PITCHER_POD, true, true,
			(pos, state) -> state.isOf(Blocks.PITCHER_CROP) && state.get(
				PitcherCropBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER,
			AutoFarmPlantTypeManager::isFarmland,
			(pos, state) -> state.isOf(Blocks.PITCHER_CROP)
				&& state.get(PitcherCropBlock.AGE) >= PitcherCropBlock.AGE
					.getValues().size() - 1,
			never(), "成熟后收获，并可重新种植。");
		add("马铃薯", Items.POTATO, Items.POTATO, true, true,
			(pos, state) -> state.isOf(Blocks.POTATOES),
			AutoFarmPlantTypeManager::isFarmland,
			(pos, state) -> isMatureCrop(state, Blocks.POTATOES), never(),
			"成熟后收获，并可重新种植。");
		add("南瓜", Items.PUMPKIN, Items.PUMPKIN_SEEDS, true, true,
			(pos, state) -> isPumpkinStem(state),
			AutoFarmPlantTypeManager::isFarmland,
			(pos, state) -> state.isOf(Blocks.PUMPKIN), never(),
			"收获南瓜方块，不破坏藤蔓。");
		add("海泡菜", Items.SEA_PICKLE, Items.SEA_PICKLE, true, true,
			(pos, state) -> state.getBlock() instanceof SeaPickleBlock
				&& hasSeaPickleSurface(pos),
			AutoFarmPlantTypeManager::hasSeaPickleSurface,
			(pos, state) -> state.getBlock() instanceof SeaPickleBlock
				&& state.get(SeaPickleBlock.PICKLES) > 1,
			never(), "只收获包含多个海泡菜的方块。");
		add("甘蔗", Items.SUGAR_CANE, Items.SUGAR_CANE, true, true,
			AutoFarmPlantTypeManager::isSugarCaneBase,
			AutoFarmPlantTypeManager::hasSugarCaneSurface,
			(pos, state) -> state.isOf(Blocks.SUGAR_CANE)
				&& isSugarCaneBase(pos.down(), BlockUtils.getState(pos.down())),
			never(), "收获时保留甘蔗基部。");
		add("甜浆果", Items.SWEET_BERRIES, Items.SWEET_BERRIES, true, true,
			(pos, state) -> state.getBlock() instanceof SweetBerryBushBlock
				&& hasBerryBushSurface(pos),
			AutoFarmPlantTypeManager::hasBerryBushSurface, never(),
			(pos, state) -> state.getBlock() instanceof SweetBerryBushBlock
				&& state.get(SweetBerryBushBlock.AGE) > 1,
			"成熟后右键收获，不破坏浆果丛。");
		add("火把花", Items.TORCHFLOWER, Items.TORCHFLOWER_SEEDS, true, true,
			(pos,
				state) -> (state.isOf(Blocks.TORCHFLOWER)
					|| state.isOf(Blocks.TORCHFLOWER_CROP)) && isFarmland(pos),
			AutoFarmPlantTypeManager::isFarmland,
			(pos, state) -> state.isOf(Blocks.TORCHFLOWER), never(),
			"收获成熟的火把花。");
		add("缠怨藤", Items.TWISTING_VINES, Items.TWISTING_VINES, false, false,
			AutoFarmPlantTypeManager::isTwistingVineBase,
			AutoFarmPlantTypeManager::hasTwistingVineSurface,
			(pos, state) -> isTwistingVine(state)
				&& !isTwistingVineBase(pos, state),
			never(), "默认关闭收获和补种。");
		add("垂泣藤", Items.WEEPING_VINES, Items.WEEPING_VINES, false, false,
			AutoFarmPlantTypeManager::isWeepingVineBase,
			AutoFarmPlantTypeManager::hasWeepingVineSurface,
			(pos, state) -> isWeepingVine(state)
				&& !isWeepingVineBase(pos, state),
			never(), "默认关闭收获和补种。");
		add("小麦", Items.WHEAT, Items.WHEAT_SEEDS, true, true,
			(pos, state) -> state.isOf(Blocks.WHEAT),
			AutoFarmPlantTypeManager::isFarmland,
			(pos, state) -> isMatureCrop(state, Blocks.WHEAT), never(),
			"成熟后收获，并可重新种植。");
		
		toggleAllSetting = new ToggleAllPlantTypesSetting("全部作物类型",
			plantTypes.stream().flatMap(AutoFarmPlantType::getSettings));
	}
	
	private void add(String name, Item icon, Item seed, boolean harvest,
		boolean replant, PlantRule replantingSpot,
		Predicate<BlockPos> plantingSurface, PlantRule mine, PlantRule interact,
		String description)
	{
		plantTypes.add(new FunctionalPlantType(name, icon, seed, harvest,
			replant, replantingSpot, plantingSurface, mine, interact,
			net.wurstclient.util.text.WText.literal(description)));
	}
	
	private static PlantRule never()
	{
		return (pos, state) -> false;
	}
	
	public AutoFarmPlantType getReplantingSpotType(BlockPos pos)
	{
		BlockState state = BlockUtils.getState(pos);
		return plantTypes.stream()
			.filter(type -> type.isReplantingSpot(pos, state)).findFirst()
			.orElse(null);
	}
	
	public boolean shouldHarvestByMining(BlockPos pos)
	{
		BlockState state = BlockUtils.getState(pos);
		return plantTypes.stream()
			.filter(AutoFarmPlantType::isHarvestingEnabled)
			.anyMatch(type -> type.shouldHarvestByMining(pos, state));
	}
	
	public boolean shouldHarvestByInteracting(BlockPos pos)
	{
		BlockState state = BlockUtils.getState(pos);
		return plantTypes.stream()
			.filter(AutoFarmPlantType::isHarvestingEnabled)
			.anyMatch(type -> type.shouldHarvestByInteracting(pos, state));
	}
	
	public Stream<Setting> getSettings()
	{
		return Stream.concat(Stream.of(toggleAllSetting),
			plantTypes.stream().flatMap(AutoFarmPlantType::getSettings));
	}
	
	private static boolean isFarmland(BlockPos pos)
	{
		return BlockUtils.getState(pos.down()).isOf(Blocks.FARMLAND);
	}
	
	private static boolean isMatureCrop(BlockState state, Block crop)
	{
		return state.isOf(crop) && state.getBlock() instanceof CropBlock plant
			&& plant.isMature(state);
	}
	
	private static boolean isPumpkinStem(BlockState state)
	{
		return state.isOf(Blocks.PUMPKIN_STEM)
			|| state.isOf(Blocks.ATTACHED_PUMPKIN_STEM);
	}
	
	private static boolean isMelonStem(BlockState state)
	{
		return state.isOf(Blocks.MELON_STEM)
			|| state.isOf(Blocks.ATTACHED_MELON_STEM);
	}
	
	private static boolean isBambooBase(BlockPos pos, BlockState state)
	{
		if(!state.isOf(Blocks.BAMBOO) && !state.isOf(Blocks.BAMBOO_SAPLING))
			return false;
		
		BlockState floor = BlockUtils.getState(pos.down());
		return !floor.isOf(Blocks.BAMBOO) && !floor.isOf(Blocks.BAMBOO_SAPLING)
			&& floor.isIn(BlockTags.BAMBOO_PLANTABLE_ON);
	}
	
	private static boolean isCactusBase(BlockPos pos, BlockState state)
	{
		return state.isOf(Blocks.CACTUS)
			&& !BlockUtils.getState(pos.down()).isOf(Blocks.CACTUS)
			&& hasCactusSurface(pos);
	}
	
	private static boolean hasCactusSurface(BlockPos pos)
	{
		BlockPos floorPos = pos.down();
		if(!BlockUtils.getState(floorPos).isIn(BlockTags.SAND))
			return false;
		
		for(Direction side : HORIZONTAL_DIRECTIONS)
		{
			BlockPos neighborPos = pos.offset(side);
			BlockState neighbor = BlockUtils.getState(neighborPos);
			if(neighbor.isSolidBlock(WurstClient.MC.world, neighborPos)
				|| neighbor.getFluidState().isIn(FluidTags.LAVA))
				return false;
		}
		
		return true;
	}
	
	private static boolean isKelp(BlockState state)
	{
		return state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT);
	}
	
	private static boolean isKelpBase(BlockPos pos, BlockState state)
	{
		if(!isKelp(state))
			return false;
		
		return !isKelp(BlockUtils.getState(pos.down())) && hasKelpSurface(pos);
	}
	
	private static boolean hasKelpSurface(BlockPos pos)
	{
		FluidState fluid = BlockUtils.getState(pos).getFluidState();
		if(!fluid.isIn(FluidTags.WATER) || fluid.getLevel() != 8)
			return false;
		
		BlockPos floorPos = pos.down();
		BlockState floor = BlockUtils.getState(floorPos);
		return !floor.isOf(Blocks.MAGMA_BLOCK) && floor.isSideSolidFullSquare(
			WurstClient.MC.world, floorPos, Direction.UP);
	}
	
	private static boolean isSugarCaneBase(BlockPos pos, BlockState state)
	{
		return state.isOf(Blocks.SUGAR_CANE)
			&& !BlockUtils.getState(pos.down()).isOf(Blocks.SUGAR_CANE)
			&& hasSugarCaneSurface(pos);
	}
	
	private static boolean hasSugarCaneSurface(BlockPos pos)
	{
		BlockPos floorPos = pos.down();
		BlockState floor = BlockUtils.getState(floorPos);
		if(!floor.isIn(BlockTags.DIRT) && !floor.isIn(BlockTags.SAND))
			return false;
		
		for(Direction side : HORIZONTAL_DIRECTIONS)
		{
			BlockState neighbor = BlockUtils.getState(floorPos.offset(side));
			if(neighbor.getFluidState().isIn(FluidTags.WATER)
				|| neighbor.isOf(Blocks.FROSTED_ICE))
				return true;
		}
		
		return false;
	}
	
	private static boolean hasJungleLog(BlockPos pos)
	{
		for(Direction side : HORIZONTAL_DIRECTIONS)
			if(BlockUtils.getState(pos.offset(side))
				.isIn(BlockTags.JUNGLE_LOGS))
				return true;
			
		return false;
	}
	
	private static boolean hasVineCeiling(BlockPos pos)
	{
		BlockPos ceilPos = pos.up();
		BlockState ceil = BlockUtils.getState(ceilPos);
		return !(ceil.getBlock() instanceof CaveVines)
			&& ceil.isSideSolidFullSquare(WurstClient.MC.world, ceilPos,
				Direction.DOWN);
	}
	
	private static boolean hasPlantingSurface(BlockPos pos)
	{
		return hasVineCeiling(pos);
	}
	
	private static boolean isGlowBerryBase(BlockPos pos, BlockState state)
	{
		return state.getBlock() instanceof CaveVines && hasPlantingSurface(pos);
	}
	
	private static boolean hasSeaPickleSurface(BlockPos pos)
	{
		BlockState floor = BlockUtils.getState(pos.down());
		if(!floor.isIn(BlockTags.CORAL_BLOCKS))
			return false;
		
		FluidState fluid = BlockUtils.getState(pos).getFluidState();
		return fluid.isIn(FluidTags.WATER) && fluid.getLevel() == 8;
	}
	
	private static boolean hasBerryBushSurface(BlockPos pos)
	{
		BlockState floor = BlockUtils.getState(pos.down());
		return floor.isIn(BlockTags.DIRT) || floor.isOf(Blocks.FARMLAND);
	}
	
	private static boolean isTwistingVine(BlockState state)
	{
		return state.isOf(Blocks.TWISTING_VINES)
			|| state.isOf(Blocks.TWISTING_VINES_PLANT);
	}
	
	private static boolean isTwistingVineBase(BlockPos pos, BlockState state)
	{
		if(!isTwistingVine(state))
			return false;
		
		return hasTwistingVineSurface(pos);
	}
	
	private static boolean hasTwistingVineSurface(BlockPos pos)
	{
		BlockPos floorPos = pos.down();
		BlockState floor = BlockUtils.getState(floorPos);
		return !isTwistingVine(floor) && floor.isSideSolidFullSquare(
			WurstClient.MC.world, floorPos, Direction.UP);
	}
	
	private static boolean isWeepingVine(BlockState state)
	{
		return state.isOf(Blocks.WEEPING_VINES)
			|| state.isOf(Blocks.WEEPING_VINES_PLANT);
	}
	
	private static boolean isWeepingVineBase(BlockPos pos, BlockState state)
	{
		if(!isWeepingVine(state))
			return false;
		
		return hasWeepingVineSurface(pos);
	}
	
	private static boolean hasWeepingVineSurface(BlockPos pos)
	{
		BlockPos ceilPos = pos.up();
		BlockState ceil = BlockUtils.getState(ceilPos);
		return !isWeepingVine(ceil) && ceil.isSideSolidFullSquare(
			WurstClient.MC.world, ceilPos, Direction.DOWN);
	}
	
	private static boolean isEndStoneBelow(BlockPos pos, BlockState state)
	{
		return (state.isOf(Blocks.CHORUS_FLOWER)
			|| state.isOf(Blocks.CHORUS_PLANT)) && isEndStoneBelow(pos);
	}
	
	private static boolean isEndStoneBelow(BlockPos pos)
	{
		return BlockUtils.getState(pos.down()).isOf(Blocks.END_STONE);
	}
	
	private static final Map<Direction, BooleanProperty> CHORUS_GROWING_DIRECTIONS;
	static
	{
		EnumMap<Direction, BooleanProperty> directions =
			new EnumMap<>(Direction.class);
		directions.put(Direction.NORTH, ConnectingBlock.NORTH);
		directions.put(Direction.SOUTH, ConnectingBlock.SOUTH);
		directions.put(Direction.WEST, ConnectingBlock.WEST);
		directions.put(Direction.EAST, ConnectingBlock.EAST);
		directions.put(Direction.UP, ConnectingBlock.UP);
		CHORUS_GROWING_DIRECTIONS = Map.copyOf(directions);
	}
	
	private static boolean shouldHarvestChorus(BlockPos pos, BlockState state)
	{
		if(state.isOf(Blocks.CHORUS_FLOWER))
		{
			int age = state.get(ChorusFlowerBlock.AGE);
			return age >= ChorusFlowerBlock.MAX_AGE
				|| !BlockUtils.getState(pos.up()).isAir();
		}
		
		return state.isOf(Blocks.CHORUS_PLANT)
			&& !hasAttachedFlowers(pos, state, new HashSet<>());
	}
	
	private static boolean hasAttachedFlowers(BlockPos pos, BlockState state,
		HashSet<BlockPos> visited)
	{
		if(visited.size() > 1000)
			return true;
		
		if(!visited.add(pos))
			return false;
		
		for(Map.Entry<Direction, BooleanProperty> entry : CHORUS_GROWING_DIRECTIONS
			.entrySet())
		{
			BooleanProperty property = entry.getValue();
			if(!state.contains(property) || !state.get(property))
				continue;
			
			Direction direction = entry.getKey();
			BlockPos neighborPos = pos.offset(direction);
			BlockState neighbor = BlockUtils.getState(neighborPos);
			if(neighbor.isOf(Blocks.CHORUS_FLOWER))
				return true;
			
			if(!neighbor.isOf(Blocks.CHORUS_PLANT))
				continue;
			
			if(direction.getAxis().isHorizontal()
				&& neighbor.contains(ConnectingBlock.DOWN)
				&& neighbor.get(ConnectingBlock.DOWN))
				continue;
			
			if(hasAttachedFlowers(neighborPos, neighbor, visited))
				return true;
		}
		
		return false;
	}
	
	@FunctionalInterface
	private interface PlantRule
	{
		boolean test(BlockPos pos, BlockState state);
	}
	
	private static final class FunctionalPlantType extends AutoFarmPlantType
	{
		private final Item seed;
		private final PlantRule replantingSpot;
		private final Predicate<BlockPos> plantingSurface;
		private final PlantRule mine;
		private final PlantRule interact;
		
		private FunctionalPlantType(String name, Item icon, Item seed,
			boolean harvest, boolean replant, PlantRule replantingSpot,
			Predicate<BlockPos> plantingSurface, PlantRule mine,
			PlantRule interact, net.wurstclient.util.text.WText description)
		{
			super(name, description, icon, harvest, replant);
			this.seed = seed;
			this.replantingSpot = replantingSpot;
			this.plantingSurface = plantingSurface;
			this.mine = mine;
			this.interact = interact;
		}
		
		@Override
		public boolean isReplantingSpot(BlockPos pos, BlockState state)
		{
			return replantingSpot.test(pos, state);
		}
		
		@Override
		public boolean hasPlantingSurface(BlockPos pos)
		{
			return plantingSurface.test(pos);
		}
		
		@Override
		public Item getSeedItem()
		{
			return seed;
		}
		
		@Override
		public boolean shouldHarvestByMining(BlockPos pos, BlockState state)
		{
			return mine.test(pos, state);
		}
		
		@Override
		public boolean shouldHarvestByInteracting(BlockPos pos,
			BlockState state)
		{
			return interact.test(pos, state);
		}
	}
}
