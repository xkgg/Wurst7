/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autofarm.AutoFarmPlantType;
import net.wurstclient.hacks.autofarm.AutoFarmPlantTypeManager;
import net.wurstclient.hacks.autofarm.AutoFarmRenderer;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FacingSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockBreakingCache;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.InteractionSimulator;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.text.WText;

@SearchTags({"自动农场", "auto farm", "AutoHarvest", "auto harvest"})
public final class AutoFarmHack extends Hack
	implements UpdateListener, RenderListener
{
	// 采集范围（控制自动农场的有效作用距离）
	private final SliderSetting range =
		new SliderSetting("采集范围", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	// 自动补种开关（是否在收获后自动重新种植作物）
	private final CheckboxSetting replant = new CheckboxSetting("自动补种", true);
	
	// 仅操作与玩家视线相通的目标
	private final CheckboxSetting checkLOS =
		new CheckboxSetting("检测视线遮挡", "启用后不会隔墙收获或补种。", false);
	
	private final FacingSetting facing =
		FacingSetting.withoutPacketSpam("控制收获和补种时玩家的朝向。");
	
	private final SwingHandSetting swingHand = new SwingHandSetting(
		WText.literal("控制收获和补种时是否挥动手臂。"), SwingHand.SERVER);
	
	private final AutoFarmPlantTypeManager plantTypes =
		new AutoFarmPlantTypeManager();
	private final HashMap<BlockPos, AutoFarmPlantType> replantingSpots =
		new HashMap<>();
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
		addSetting(checkLOS);
		addSetting(facing);
		addSetting(swingHand);
		renderer.getSettings().forEach(this::addSetting);
		plantTypes.getSettings().forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().autoMineHack.setEnabled(false);
		
		// 清空已识别的作物位置
		replantingSpots.clear();
		
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
		Vec3d eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.ofFloored(eyesVec);
		double rangeSq = range.getValueSq();
		int blockRange = range.getValueCeil();
		
		List<BlockPos> nearbyBlocks =
			BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
				.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
				.filter(BlockUtils::canBeClicked).toList();
		
		for(BlockPos pos : nearbyBlocks)
		{
			AutoFarmPlantType plantType = plantTypes.getReplantingSpotType(pos);
			if(plantType != null)
				replantingSpots.put(pos, plantType);
		}
		
		List<BlockPos> blocksToMine =
			nearbyBlocks.stream().filter(plantTypes::shouldHarvestByMining)
				.sorted(Comparator
					.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
				.toList();
		List<BlockPos> blocksToInteract =
			nearbyBlocks.stream().filter(plantTypes::shouldHarvestByInteracting)
				.sorted(Comparator
					.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
				.toList();
		List<BlockPos> blocksToReplant =
			BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
				.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
				.filter(pos -> BlockUtils.getState(pos).isReplaceable())
				.filter(pos -> {
					AutoFarmPlantType plantType = replantingSpots.get(pos);
					return replant.isChecked() && plantType != null
						&& plantType.isReplantingEnabled()
						&& plantType.hasPlantingSurface(pos);
				})
				.sorted(Comparator
					.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
				.toList();
		
		boolean replanting = replant(blocksToReplant);
		boolean interacting =
			replanting ? false : harvestByInteracting(blocksToInteract);
		if(!interacting && !replanting)
			harvestByMining(blocksToMine);
		
		busy = replanting || interacting || currentlyHarvesting != null;
		
		List<BlockPos> blocksToHarvest = Stream
			.of(blocksToMine, blocksToInteract).flatMap(List::stream).toList();
		renderer.updateVertexBuffers(blocksToHarvest, replantingSpots.keySet(),
			blocksToReplant);
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// 渲染作物/操作提示
		renderer.render(matrixStack);
		if(renderer.drawBlocksToHarvest.isChecked())
			overlay.render(matrixStack, partialTicks, currentlyHarvesting);
	}
	
	/**
	 * 判断自动农场是否正在执行收获或补种操作
	 *
	 * @return 忙碌状态（true=正在操作，false=空闲）
	 */
	public boolean isBusy()
	{
		return busy;
	}
	
	/**
	 * 执行补种操作
	 *
	 * @param blocksToReplant
	 *            待补种的方块列表
	 * @return 是否成功补种
	 */
	private boolean replant(List<BlockPos> blocksToReplant)
	{
		if(MC.itemUseCooldown > 0)
			return false;
		
		for(BlockPos pos : blocksToReplant)
		{
			AutoFarmPlantType plantType = replantingSpots.get(pos);
			if(plantType == null || !plantType.isReplantingEnabled())
				continue;
			
			Item seed = plantType.getSeedItem();
			if(!MC.player.isHolding(seed))
				continue;
			
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.requiresSneaking()
				|| params.distanceSq() > range.getValueSq()
				|| checkLOS.isChecked() && !params.lineOfSight())
				continue;
			
			Hand hand = MC.player.getMainHandStack().isOf(seed) ? Hand.MAIN_HAND
				: Hand.OFF_HAND;
			cancelBreakingBlock();
			facing.getSelected().face(params.hitVec());
			MC.itemUseCooldown = 4;
			InteractionSimulator.rightClickBlock(params.toHitResult(), hand,
				swingHand.getSelected());
			return true;
		}
		
		for(BlockPos pos : blocksToReplant)
		{
			AutoFarmPlantType plantType = replantingSpots.get(pos);
			if(plantType == null || !plantType.isReplantingEnabled())
				continue;
			
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.requiresSneaking()
				|| params.distanceSq() > range.getValueSq()
				|| checkLOS.isChecked() && !params.lineOfSight())
				continue;
			
			if(InventoryUtils.selectItem(plantType.getSeedItem()))
			{
				cancelBreakingBlock();
				return true;
			}
		}
		
		return false;
	}
	
	private boolean harvestByInteracting(List<BlockPos> blocksToInteract)
	{
		if(MC.itemUseCooldown > 0)
			return false;
		
		for(BlockPos pos : blocksToInteract)
		{
			BlockBreakingParams params =
				BlockBreaker.getBlockBreakingParams(pos);
			if(params == null || params.distanceSq() > range.getValueSq()
				|| checkLOS.isChecked() && !params.lineOfSight())
				continue;
			
			cancelBreakingBlock();
			facing.getSelected().face(params.hitVec());
			MC.itemUseCooldown = 4;
			InteractionSimulator.rightClickBlock(params.toHitResult(),
				swingHand.getSelected());
			return true;
		}
		
		return false;
	}
	
	private void harvestByMining(List<BlockPos> blocksToMine)
	{
		if(MC.player.getAbilities().creativeMode)
		{
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			
			List<BlockPos> blocks = cache.filterOutRecentBlocks(
				blocksToMine.stream().filter(this::hasValidBreakingParams));
			if(blocks.isEmpty())
				return;
			
			currentlyHarvesting = blocks.get(0);
			BlockBreaker.breakBlocksWithPacketSpam(blocks);
			return;
		}
		
		currentlyHarvesting = blocksToMine.stream().filter(pos -> {
			BlockBreakingParams params = getValidBreakingParams(pos);
			return params != null && BlockBreaker.breakOneBlock(params);
		}).findFirst().orElse(null);
		
		if(currentlyHarvesting == null)
		{
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			return;
		}
		
		overlay.updateProgress();
	}
	
	private void cancelBreakingBlock()
	{
		if(!MC.interactionManager.isBreakingBlock())
			return;
		
		MC.interactionManager.cancelBlockBreaking();
		overlay.resetProgress();
	}
	
	private boolean hasValidBreakingParams(BlockPos pos)
	{
		return getValidBreakingParams(pos) != null;
	}
	
	private BlockBreakingParams getValidBreakingParams(BlockPos pos)
	{
		BlockBreakingParams params = BlockBreaker.getBlockBreakingParams(pos);
		if(params == null || params.distanceSq() > range.getValueSq()
			|| checkLOS.isChecked() && !params.lineOfSight())
			return null;
		
		return params;
	}
}
