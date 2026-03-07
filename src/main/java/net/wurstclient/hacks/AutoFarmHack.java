/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.block.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autofarm.AutoFarmRenderer;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreakingCache;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RotationUtils;

@SearchTags({"自动农场", "auto farm", "AutoHarvest", "auto harvest"})
public final class AutoFarmHack extends Hack
	implements UpdateListener, RenderListener
{
	// 采集范围（控制自动农场的有效作用距离）
	private final SliderSetting range =
		new SliderSetting("采集范围", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	// 自动补种开关（是否在收获后自动重新种植作物）
	private final CheckboxSetting replant =
		new CheckboxSetting("自动补种", true);
	
	// 作物种子映射表（键：作物方块，值：对应的种子物品）
	private final HashMap<Block, Item> seeds = new HashMap<>();
	{
		seeds.put(Blocks.WHEAT, Items.WHEAT_SEEDS);
		seeds.put(Blocks.CARROTS, Items.CARROT);
		seeds.put(Blocks.POTATOES, Items.POTATO);
		seeds.put(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
		seeds.put(Blocks.PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
		seeds.put(Blocks.MELON_STEM, Items.MELON_SEEDS);
		seeds.put(Blocks.NETHER_WART, Items.NETHER_WART);
		seeds.put(Blocks.COCOA, Items.COCOA_BEANS);
	}
	
	// 已识别的作物位置映射表（键：作物方块坐标，值：对应的种子物品）
	private final HashMap<BlockPos, Item> plants = new HashMap<>();
	// 方块破坏缓存（用于避免重复破坏同一方块）
	private final BlockBreakingCache cache = new BlockBreakingCache();
	// 当前正在收获的方块坐标
	private BlockPos currentlyHarvesting;
	
	// 自动农场渲染器（用于在游戏中绘制作物/操作提示）
	private final AutoFarmRenderer renderer = new AutoFarmRenderer();
	// 进度覆盖渲染器（用于显示方块破坏进度）
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	// 忙碌状态标记（是否正在执行收获/补种操作）
	private boolean busy;
	
	public AutoFarmHack()
	{
		super("自动农场");
		
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(replant);
	}
	
	@Override
	protected void onEnable()
	{
		// 清空已识别的作物列表
		plants.clear();
		
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// 若正在收获方块，取消当前破坏操作
		if(currentlyHarvesting != null)
		{
			MC.interactionManager.breakingBlock = true;
			MC.interactionManager.cancelBlockBreaking();
			currentlyHarvesting = null;
		}
		
		// 重置缓存和进度
		cache.reset();
		overlay.resetProgress();
		busy = false;
		
		// 重置渲染器
		renderer.reset();
	}
	
	@Override
	public void onUpdate()
	{
		currentlyHarvesting = null;
		// 获取玩家眼睛位置
		Vec3d eyesVec = RotationUtils.getEyesPos();
		// 获取玩家眼睛所在方块坐标
		BlockPos eyesBlock = BlockPos.ofFloored(eyesVec);
		// 采集范围的平方（用于距离判断，避免开方运算）
		double rangeSq = range.getValueSq();
		// 采集范围的整数上限（用于方块遍历）
		int blockRange = range.getValueCeil();
		
		// 获取附近可交互的非空方块
		ArrayList<BlockPos> blocks =
			BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
				// 过滤：在采集范围内的方块
				.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
				// 过滤：可被点击交互的方块
				.filter(BlockUtils::canBeClicked)
				.collect(Collectors.toCollection(ArrayList::new));
		
		// 检测新作物并更新作物映射表
		updatePlants(blocks);
		
		// 待收获的方块列表
		ArrayList<BlockPos> blocksToHarvest = new ArrayList<>();
		// 待补种的方块列表
		ArrayList<BlockPos> blocksToReplant = new ArrayList<>();
		
		// 自由视角模式下不执行任何方块操作
		if(!WURST.getHax().freecamHack.isEnabled())
		{
			// 筛选出可收获的方块
			blocksToHarvest = getBlocksToHarvest(eyesVec, blocks);
			
			// 若开启自动补种，筛选出可补种的空方块
			if(replant.isChecked())
				blocksToReplant =
					getBlocksToReplant(eyesVec, eyesBlock, rangeSq, blockRange);
		}
		
		// 优先执行补种操作
		boolean replanting = replant(blocksToReplant);
		
		// 若补种失败，执行收获操作
		if(!replanting)
			harvest(blocksToHarvest.stream());
		
		// 更新忙碌状态
		busy = replanting || currentlyHarvesting != null;
		
		// 更新渲染器数据（绘制待收获/已识别/待补种的方块）
		renderer.updateVertexBuffers(blocksToHarvest, plants.keySet(),
			blocksToReplant);
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// 渲染作物/操作提示
		renderer.render(matrixStack);
		// 渲染方块破坏进度
		overlay.render(matrixStack, partialTicks, currentlyHarvesting);
	}
	
	/**
	 * 判断自动农场是否正在执行收获或补种操作
	 * @return 忙碌状态（true=正在操作，false=空闲）
	 */
	public boolean isBusy()
	{
		return busy;
	}
	
	/**
	 * 更新已识别的作物列表（从指定方块列表中检测新作物）
	 * @param blocks 待检测的方块列表
	 */
	private void updatePlants(List<BlockPos> blocks)
	{
		for(BlockPos pos : blocks)
		{
			// 获取方块对应的种子，无对应种子则跳过
			Item seed = seeds.get(BlockUtils.getBlock(pos));
			if(seed == null)
				continue;
			
			// 将作物位置和对应种子加入映射表
			plants.put(pos, seed);
		}
	}
	
	/**
	 * 筛选出可收获的方块（按距离玩家由近到远排序）
	 * @param eyesVec 玩家眼睛位置
	 * @param blocks 待筛选的方块列表
	 * @return 可收获的方块列表
	 */
	private ArrayList<BlockPos> getBlocksToHarvest(Vec3d eyesVec,
		ArrayList<BlockPos> blocks)
	{
		return blocks.parallelStream()
			// 过滤：符合收获条件的方块
			.filter(this::shouldBeHarvested)
			// 排序：按距离玩家由近到远
			.sorted(Comparator
				.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	/**
	 * 判断指定方块是否达到收获条件
	 * @param pos 方块坐标
	 * @return 是否可收获
	 */
	private boolean shouldBeHarvested(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos);
		BlockState state = BlockUtils.getState(pos);
		
		// 普通作物（小麦/胡萝卜等）：是否成熟
		if(block instanceof CropBlock)
			return ((CropBlock)block).isMature(state);
		
		// 下界疣：生长阶段≥3（完全成熟）
		if(block instanceof NetherWartBlock)
			return state.get(NetherWartBlock.AGE) >= 3;
		
		// 可可豆：生长阶段≥2（完全成熟）
		if(block instanceof CocoaBlock)
			return state.get(CocoaBlock.AGE) >= 2;
		
		// 南瓜/西瓜：直接收获
		if(block instanceof GourdBlock)
			return true;
		
		// 甘蔗：高度≥2格（下方1格是甘蔗，下方2格不是）
		if(block instanceof SugarCaneBlock)
			return BlockUtils.getBlock(pos.down()) instanceof SugarCaneBlock
				&& !(BlockUtils
					.getBlock(pos.down(2)) instanceof SugarCaneBlock);
		
		// 仙人掌：高度≥2格（下方1格是仙人掌，下方2格不是）
		if(block instanceof CactusBlock)
			return BlockUtils.getBlock(pos.down()) instanceof CactusBlock
				&& !(BlockUtils.getBlock(pos.down(2)) instanceof CactusBlock);
		
		// 海带：高度≥2格（下方1格是海带，下方2格不是）
		if(block instanceof KelpPlantBlock)
			return BlockUtils.getBlock(pos.down()) instanceof KelpPlantBlock
				&& !(BlockUtils
					.getBlock(pos.down(2)) instanceof KelpPlantBlock);
		
		// 竹子：高度≥2格（下方1格是竹子，下方2格不是）
		if(block instanceof BambooBlock)
			return BlockUtils.getBlock(pos.down()) instanceof BambooBlock
				&& !(BlockUtils.getBlock(pos.down(2)) instanceof BambooBlock);
		
		// 其他方块：不可收获
		return false;
	}
	
	/**
	 * 筛选出可补种的空方块（按距离玩家由近到远排序）
	 * @param eyesVec 玩家眼睛位置
	 * @param eyesBlock 玩家眼睛所在方块
	 * @param rangeSq 采集范围平方
	 * @param blockRange 采集范围整数上限
	 * @return 可补种的方块列表
	 */
	private ArrayList<BlockPos> getBlocksToReplant(Vec3d eyesVec,
		BlockPos eyesBlock, double rangeSq, int blockRange)
	{
		return BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			// 过滤：在采集范围内的方块
			.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
			// 过滤：可被替换的空方块
			.filter(pos -> BlockUtils.getState(pos).isReplaceable())
			// 过滤：属于已识别的作物位置
			.filter(pos -> plants.containsKey(pos))
			// 过滤：符合补种条件的方块
			.filter(this::canBeReplanted)
			// 排序：按距离玩家由近到远
			.sorted(Comparator
				.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	/**
	 * 判断指定位置是否符合补种条件（土壤/附着方块匹配）
	 * @param pos 方块坐标
	 * @return 是否可补种
	 */
	private boolean canBeReplanted(BlockPos pos)
	{
		Item item = plants.get(pos);
		
		// 普通农作物（小麦/胡萝卜等）：下方是耕地
		if(item == Items.WHEAT_SEEDS || item == Items.CARROT
			|| item == Items.POTATO || item == Items.BEETROOT_SEEDS
			|| item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS)
			return BlockUtils.getBlock(pos.down()) instanceof FarmlandBlock;
		
		// 下界疣：下方是灵魂沙
		if(item == Items.NETHER_WART)
			return BlockUtils.getBlock(pos.down()) instanceof SoulSandBlock;
		
		// 可可豆：四周有丛林原木
		if(item == Items.COCOA_BEANS)
			return BlockUtils.getState(pos.north()).isIn(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.east()).isIn(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.south()).isIn(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.west()).isIn(BlockTags.JUNGLE_LOGS);
		
		// 其他种子：不可补种
		return false;
	}
	
	/**
	 * 执行补种操作
	 * @param blocksToReplant 待补种的方块列表
	 * @return 是否成功补种
	 */
	private boolean replant(List<BlockPos> blocksToReplant)
	{
		// 检查物品使用冷却（冷却中则跳过）
		if(MC.itemUseCooldown > 0)
			return false;
		
		// 检查是否手持待补种方块所需的种子
		Optional<Item> heldSeed = blocksToReplant.stream().map(plants::get)
			.distinct().filter(item -> MC.player.isHolding(item)).findFirst();
		
		// 若手持对应种子，尝试补种
		if(heldSeed.isPresent())
		{
			// 获取种子物品和手持的手（主手/副手）
			Item item = heldSeed.get();
			Hand hand = MC.player.getMainHandStack().isOf(item) ? Hand.MAIN_HAND
				: Hand.OFF_HAND;
			
			// 筛选出需要当前手持种子的补种方块
			ArrayList<BlockPos> blocksToReplantWithHeldSeed =
				blocksToReplant.stream().filter(pos -> plants.get(pos) == item)
					.collect(Collectors.toCollection(ArrayList::new));
			
			for(BlockPos pos : blocksToReplantWithHeldSeed)
			{
				// 跳过无法到达的方块
				BlockPlacingParams params =
					BlockPlacer.getBlockPlacingParams(pos);
				if(params == null || params.distanceSq() > range.getValueSq())
					continue;
				
				// 朝向目标方块（模拟玩家视角）
				WURST.getRotationFaker().faceVectorPacket(params.hitVec());
				
				// 放置种子
				ActionResult result = MC.interactionManager
					.interactBlock(MC.player, hand, params.toHitResult());
				
				// 挥动手臂（模拟玩家操作）
				if(result.isAccepted() && result.shouldSwingHand())
					SwingHand.SERVER.swing(hand);
				
				// 重置物品使用冷却
				MC.itemUseCooldown = 4;
				return true;
			}
		}
		
		// 若未手持种子，尝试从背包选择对应种子
		for(BlockPos pos : blocksToReplant)
		{
			// 跳过无法到达的方块
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.distanceSq() > range.getValueSq())
				continue;
			
			// 选择种子（返回false表示背包中无对应种子）
			Item item = plants.get(pos);
			if(InventoryUtils.selectItem(item))
				return true;
		}
		
		// 补种失败
		return false;
	}
	
	/**
	 * 执行收获操作
	 * @param stream 待收获的方块流
	 */
	private void harvest(Stream<BlockPos> stream)
	{
		// 创造模式：批量破坏所有可收获方块
		if(MC.player.getAbilities().creativeMode)
		{
			// 取消当前破坏操作
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			
			// 过滤掉近期已破坏的方块
			ArrayList<BlockPos> blocks = cache.filterOutRecentBlocks(stream);
			if(blocks.isEmpty())
				return;
			
			// 标记当前收获的方块并批量破坏
			currentlyHarvesting = blocks.get(0);
			BlockBreaker.breakBlocksWithPacketSpam(blocks);
			return;
		}
		
		// 生存模式：破坏第一个可收获的有效方块
		currentlyHarvesting =
			stream.filter(BlockBreaker::breakOneBlock).findFirst().orElse(null);
		
		// 无可用方块时重置状态
		if(currentlyHarvesting == null)
		{
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			return;
		}
		
		// 更新破坏进度显示
		overlay.updateProgress();
	}
}