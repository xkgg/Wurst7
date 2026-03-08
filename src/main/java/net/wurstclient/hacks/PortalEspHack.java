/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.portalesp.PortalEspBlockGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ChunkAreaSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkSearcher.Result;
import net.wurstclient.util.chunk.ChunkSearcherCoordinator;

/**
 * 传送门透视（PortalESP）：高亮显示玩家周围区块内的各类传送门/传送门相关方块，
 * 支持下界传送门、末地传送门、末地传送门框架、末地折跃门，可自定义颜色和显示样式。
 */
public final class PortalEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	// ESP显示样式：控制透视的显示方式（方框/线条/混合等）
	private final EspStyleSetting style = new EspStyleSetting();
	
	// 下界传送门配置组：颜色（红色）、是否启用
	private final PortalEspBlockGroup netherPortal =
		new PortalEspBlockGroup(Blocks.NETHER_PORTAL,
			new ColorSetting("下界传送门颜色",
				"下界传送门将以此颜色高亮显示。", Color.RED),
			new CheckboxSetting("显示下界传送门", true));
	
	// 末地传送门配置组：颜色（绿色）、是否启用
	private final PortalEspBlockGroup endPortal =
		new PortalEspBlockGroup(Blocks.END_PORTAL,
			new ColorSetting("末地传送门颜色",
				"末地传送门将以此颜色高亮显示。", Color.GREEN),
			new CheckboxSetting("显示末地传送门", true));
	
	// 末地传送门框架配置组：颜色（蓝色）、是否启用
	private final PortalEspBlockGroup endPortalFrame = new PortalEspBlockGroup(
		Blocks.END_PORTAL_FRAME,
		new ColorSetting("末地传送门框架颜色",
			"末地传送门框架将以此颜色高亮显示。", Color.BLUE),
		new CheckboxSetting("显示末地传送门框架", true));
	
	// 末地折跃门配置组：颜色（黄色）、是否启用
	private final PortalEspBlockGroup endGateway = new PortalEspBlockGroup(
		Blocks.END_GATEWAY,
		new ColorSetting("末地折跃门颜色",
			"末地折跃门将以此颜色高亮显示。", Color.YELLOW),
		new CheckboxSetting("显示末地折跃门", true));
	
	// 所有传送门配置组列表
	private final List<PortalEspBlockGroup> groups =
		Arrays.asList(netherPortal, endPortal, endPortalFrame, endGateway);
	
	// 搜索范围设置：控制玩家周围搜索传送门的区块范围（值越高性能要求越高）
	private final ChunkAreaSetting area = new ChunkAreaSetting("搜索范围",
		"玩家周围需要搜索传送门的区块范围。\n"
			+ "数值越高，对电脑性能要求越高。");
	
	// 区块搜索查询条件：匹配下界传送门/末地传送门/末地传送门框架/末地折跃门
	private final BiPredicate<BlockPos, BlockState> query =
		(pos, state) -> state.getBlock() == Blocks.NETHER_PORTAL
			|| state.getBlock() == Blocks.END_PORTAL
			|| state.getBlock() == Blocks.END_PORTAL_FRAME
			|| state.getBlock() == Blocks.END_GATEWAY;
	
	// 区块搜索协调器：管理多线程搜索传送门方块
	private final ChunkSearcherCoordinator coordinator =
		new ChunkSearcherCoordinator(query, area);
	
	// 标记配置组的包围盒是否已更新
	private boolean groupsUpToDate;
	
	public PortalEspHack()
	{
		super("传送门高亮"); // 功能显示名称
		setCategory(Category.RENDER); // 归类到“渲染”类别
		
		addSetting(style); // 添加ESP显示样式设置
		// 批量添加所有传送门配置组的设置项（颜色、启用开关）
		groups.stream().flatMap(PortalEspBlockGroup::getSettings)
			.forEach(this::addSetting);
		addSetting(area); // 添加搜索范围设置
	}
	
	@Override
	protected void onEnable()
	{
		// 初始化配置组更新标记为未更新
		groupsUpToDate = false;
		
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this); // 帧更新（搜索逻辑）
		EVENTS.add(PacketInputListener.class, coordinator); // 数据包监听（区块加载）
		EVENTS.add(CameraTransformViewBobbingListener.class, this); // 相机晃动（禁用ESP线条时的晃动）
		EVENTS.add(RenderListener.class, this); // 渲染（绘制ESP）
	}
	
	@Override
	protected void onDisable()
	{
		// 移除事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, coordinator);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// 重置区块搜索协调器
		coordinator.reset();
		// 清空所有配置组的包围盒数据
		groups.forEach(PortalEspBlockGroup::clear);
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		// 显示ESP线条时，禁用相机晃动（避免线条抖动）
		if(style.getSelected().hasLines())
			event.cancel();
	}
	
	@Override
	public void onUpdate()
	{
		// 更新区块搜索器，返回是否有搜索器状态变化
		boolean searchersChanged = coordinator.update();
		if(searchersChanged)
			groupsUpToDate = false;
		
		// 搜索完成且配置组未更新时，更新传送门包围盒
		if(!groupsUpToDate && coordinator.isDone())
			updateGroupBoxes();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// 显示ESP方框：绘制传送门的实心/轮廓方框
		if(style.getSelected().hasBoxes())
			renderBoxes(matrixStack);
		
		// 显示ESP线条：绘制从玩家到传送门的追踪线条
		if(style.getSelected().hasLines())
			renderTracers(matrixStack, partialTicks);
	}
	
	/**
	 * 绘制传送门ESP方框（实心+轮廓）
	 */
	private void renderBoxes(MatrixStack matrixStack)
	{
		for(PortalEspBlockGroup group : groups)
		{
			// 配置组未启用则跳过
			if(!group.isEnabled())
				return;
			
			List<Box> boxes = group.getBoxes(); // 获取传送门包围盒列表
			// 实心方框颜色（半透明，0x40=64/255透明度）
			int quadsColor = group.getColorI(0x40);
			// 轮廓线条颜色（半透明，0x80=128/255透明度）
			int linesColor = group.getColorI(0x80);
			
			// 绘制实心方框
			RenderUtils.drawSolidBoxes(matrixStack, boxes, quadsColor, false);
			// 绘制轮廓方框
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, linesColor,
				false);
		}
	}
	
	/**
	 * 绘制从玩家到传送门的ESP追踪线条
	 */
	private void renderTracers(MatrixStack matrixStack, float partialTicks)
	{
		for(PortalEspBlockGroup group : groups)
		{
			// 配置组未启用则跳过
			if(!group.isEnabled())
				return;
			
			List<Box> boxes = group.getBoxes(); // 获取传送门包围盒列表
			// 计算每个包围盒的中心坐标（线条终点）
			List<Vec3d> ends = boxes.stream().map(Box::getCenter).toList();
			// 线条颜色（半透明，0x80=128/255透明度）
			int color = group.getColorI(0x80);
			
			// 绘制追踪线条
			RenderUtils.drawTracers(matrixStack, partialTicks, ends, color,
				false);
		}
	}
	
	/**
	 * 更新所有传送门配置组的包围盒数据
	 */
	private void updateGroupBoxes()
	{
		// 清空原有包围盒数据
		groups.forEach(PortalEspBlockGroup::clear);
		// 将搜索到的传送门方块添加到对应配置组
		coordinator.getMatches().forEach(this::addToGroupBoxes);
		// 标记配置组已更新
		groupsUpToDate = true;
	}
	
	/**
	 * 将搜索结果添加到对应传送门配置组的包围盒
	 */
	private void addToGroupBoxes(Result result)
	{
		for(PortalEspBlockGroup group : groups)
			// 匹配方块类型则添加到对应配置组
			if(result.state().getBlock() == group.getBlock())
			{
				group.add(result.pos());
				break;
			}
	}
}