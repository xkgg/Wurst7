/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import net.minecraft.block.Block;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.nukers.NukerMultiIdListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockBreakingCache;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

public final class VeinMinerHack extends Hack
	implements UpdateListener, LeftClickListener, RenderListener
{
	private static final Box BLOCK_BOX =
		new Box(1 / 16.0, 1 / 16.0, 1 / 16.0, 15 / 16.0, 15 / 16.0, 15 / 16.0);
	
	// 挖矿范围
	private final SliderSetting range =
		new SliderSetting("挖矿范围", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	// 平面模式（不破坏脚下以下的方块）
	private final CheckboxSetting flat = new CheckboxSetting("平面模式",
		"不会破坏你脚下以下的任何方块。", false);
	
	// 连锁挖矿的方块类型列表
	private final NukerMultiIdListSetting multiIdList =
		new NukerMultiIdListSetting("作为矿脉挖掘的方块类型。");
	
	// 挥动手臂设置
	private final SwingHandSetting swingHand = new SwingHandSetting(
		SwingHandSetting.genericMiningDescription(this), SwingHand.SERVER);
	
	private final BlockBreakingCache cache = new BlockBreakingCache();
	private final OverlayRenderer overlay = new OverlayRenderer();
	private final HashSet<BlockPos> currentVein = new HashSet<>();
	private BlockPos currentBlock;
	
	// 最大矿脉大小
	private final SliderSetting maxVeinSize = new SliderSetting("最大矿脉大小",
		"单次连锁挖矿的最大方块数量。", 64, 1, 1000, 1,
		ValueDisplay.INTEGER);
	
	// 检测视线遮挡
	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"检测视线遮挡",
		"确保不会穿墙破坏方块。", false);
	
	public VeinMinerHack()
	{
		super("自动挖矿"); // 功能名称改为“连锁挖矿”
		setCategory(Category.BLOCKS); // 按要求设置类别为自动挖矿（注：Category枚举需确保有BLOCKS，若要字面量为“自动挖矿”需修改Category类）
		addSetting(range);
		addSetting(flat);
		addSetting(multiIdList);
		addSetting(swingHand);
		addSetting(maxVeinSize);
		addSetting(checkLOS);
	}
	
	@Override
	protected void onEnable()
	{
		// 启用时关闭其他挖矿类hack
		WURST.getHax().autoMineHack.setEnabled(false);
		WURST.getHax().excavatorHack.setEnabled(false);
		WURST.getHax().nukerHack.setEnabled(false);
		WURST.getHax().nukerLegitHack.setEnabled(false);
		WURST.getHax().speedNukerHack.setEnabled(false);
		WURST.getHax().tunnellerHack.setEnabled(false);
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(LeftClickListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(LeftClickListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		currentVein.clear();
		if(currentBlock != null)
		{
			MC.interactionManager.breakingBlock = true;
			MC.interactionManager.cancelBlockBreaking();
			currentBlock = null;
		}
		
		cache.reset();
		overlay.resetProgress();
	}
	
	@Override
	public void onUpdate()
	{
		currentBlock = null;
		// 移除已被破坏的方块
		currentVein.removeIf(pos -> BlockUtils.getState(pos).isReplaceable());
		
		// 如果攻击键被按下则返回
		if(MC.options.attackKey.isPressed())
			return;
		
		Vec3d eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.ofFloored(eyesVec);
		double rangeSq = range.getValueSq();
		int blockRange = range.getValueCeil();
		
		// 筛选符合条件的可破坏方块
		Stream<BlockBreakingParams> stream = BlockUtils
			.getAllInBoxStream(eyesBlock, blockRange)
			.filter(this::shouldBreakBlock)
			.map(BlockBreaker::getBlockBreakingParams).filter(Objects::nonNull)
			.filter(params -> params.distanceSq() <= rangeSq);
		
		// 如果启用了视线检测，则过滤掉视线被遮挡的方块
		if(checkLOS.isChecked())
			stream = stream.filter(BlockBreakingParams::lineOfSight);
		
		// 按优先级排序
		stream = stream.sorted(BlockBreaker.comparingParams());
		
		// 创造模式下一次性破坏所有符合条件的方块
		if(MC.player.getAbilities().creativeMode)
		{
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			
			ArrayList<BlockPos> blocks = cache
				.filterOutRecentBlocks(stream.map(BlockBreakingParams::pos));
			if(blocks.isEmpty())
				return;
			
			currentBlock = blocks.get(0);
			BlockBreaker.breakBlocksWithPacketSpam(blocks);
			swingHand.swing(Hand.MAIN_HAND);
			return;
		}
		
		// 生存模式下逐个破坏方块
		currentBlock = stream.filter(this::breakOneBlock)
			.map(BlockBreakingParams::pos).findFirst().orElse(null);
		
		if(currentBlock == null)
		{
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			return;
		}
		
		overlay.updateProgress();
	}
	
	/**
	 * 判断是否应该破坏指定位置的方块
	 */
	private boolean shouldBreakBlock(BlockPos pos)
	{
		// 平面模式下跳过脚下以下的方块
		if(flat.isChecked() && pos.getY() < MC.player.getY())
			return false;
		
		// 只破坏当前矿脉中的方块
		return currentVein.contains(pos);
	}
	
	/**
	 * 破坏单个方块
	 */
	private boolean breakOneBlock(BlockBreakingParams params)
	{
		// 自动瞄准方块
		WURST.getRotationFaker().faceVectorPacket(params.hitVec());
		
		// 更新方块破坏进度
		if(!MC.interactionManager.updateBlockBreakingProgress(params.pos(),
			params.side()))
			return false;
		
		// 挥动手臂动画
		swingHand.swing(Hand.MAIN_HAND);
		return true;
	}
	
	@Override
	public void onLeftClick(LeftClickEvent event)
	{
		// 如果已有矿脉则返回
		if(!currentVein.isEmpty())
			return;
		
		// 检查准星是否指向方块
		if(!(MC.crosshairTarget instanceof BlockHitResult bHitResult)
			|| bHitResult.getType() != HitResult.Type.BLOCK)
			return;
		
		// 检查方块是否在允许的列表中
		if(!multiIdList.contains(BlockUtils.getBlock(bHitResult.getBlockPos())))
			return;
		
		// 构建矿脉
		buildVein(bHitResult.getBlockPos());
	}
	
	/**
	 * 构建矿脉（查找相连的同类型方块）
	 */
	private void buildVein(BlockPos pos)
	{
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Block targetBlock = BlockUtils.getBlock(pos);
		int maxSize = maxVeinSize.getValueI();
		
		queue.offer(pos);
		currentVein.add(pos);
		
		// 广度优先搜索相连的同类型方块
		while(!queue.isEmpty() && currentVein.size() < maxSize)
		{
			BlockPos current = queue.poll();
			
			// 检查六个方向的相邻方块
			for(Direction direction : Direction.values())
			{
				BlockPos neighbor = current.offset(direction);
				// 如果相邻方块未被加入且类型相同，则加入矿脉
				if(!currentVein.contains(neighbor)
					&& BlockUtils.getBlock(neighbor) == targetBlock)
				{
					queue.offer(neighbor);
					currentVein.add(neighbor);
				}
			}
		}
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// 渲染破坏进度覆盖层
		overlay.render(matrixStack, partialTicks, currentBlock);
		// 如果矿脉为空则返回
		if(currentVein.isEmpty())
			return;
		
		// 渲染矿脉方块的轮廓
		List<Box> boxes =
			currentVein.stream().map(pos -> BLOCK_BOX.offset(pos)).toList();
		RenderUtils.drawOutlinedBoxes(matrixStack, boxes, 0x80000000, false);
	}
}