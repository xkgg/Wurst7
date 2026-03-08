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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.ai.PathFinder;
import net.wurstclient.ai.PathPos;
import net.wurstclient.ai.PathProcessor;
import net.wurstclient.commands.PathCmd;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filterlists.FollowFilterList;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.FakePlayerEntity;

/**
 * 自动跟随（Follow）：让玩家自动跟随指定实体（生物/矿车）的移动功能，
 * 支持自定义跟随距离、启用AI寻路（实验性），可通过过滤器筛选跟随目标，
 * 目标消失/死亡时会自动重新匹配同名实体，支持路径可视化渲染。
 */
@DontSaveState // 禁用状态保存（重启客户端后默认关闭）
public final class FollowHack extends Hack
	implements UpdateListener, RenderListener
{
	// 当前跟随的实体
	private Entity entity;
	// 实体寻路器：计算到目标的路径
	private EntityPathFinder pathFinder;
	// 路径处理器：执行寻路移动逻辑
	private PathProcessor processor;
	// 路径处理计时（防止无限处理）
	private int ticksProcessing;
	
	// 跟随距离：与目标保持的最小距离（1-12格，步长0.5）
	private final SliderSetting distance =
		new SliderSetting("跟随距离", "与目标实体保持的跟随距离", 1, 1,
			12, 0.5, ValueDisplay.DECIMAL);
	
	// AI寻路开关：启用实验性AI寻路功能
	private final CheckboxSetting useAi =
		new CheckboxSetting("使用AI寻路（实验性）", false);
	
	// 实体过滤列表：筛选可跟随的目标类型
	private final EntityFilterList entityFilters = FollowFilterList.create();
	
	public FollowHack()
	{
		super("自动跟随"); // 功能显示名称
		setCategory(Category.MOVEMENT); // 归类到“移动”类别
		addSetting(distance); // 添加跟随距离设置
		addSetting(useAi); // 添加AI寻路开关
		
		// 批量添加实体过滤列表的设置项
		entityFilters.forEach(this::addSetting);
	}
	
	/**
	 * 获取渲染名称（显示当前跟随的实体名称）
	 */
	@Override
	public String getRenderName()
	{
		if(entity != null)
			return "正在跟随 " + entity.getName().getString();
		return "自动跟随";
	}
	
	@Override
	protected void onEnable()
	{
		// 禁用冲突的移动/战斗类功能
		WURST.getHax().fightBotHack.setEnabled(false);
		WURST.getHax().protectHack.setEnabled(false);
		WURST.getHax().tunnellerHack.setEnabled(false);
		
		// 未指定跟随实体时，自动筛选最近的有效实体
		if(entity == null)
		{
			// 筛选规则：
			// 1. 未被移除的实体
			// 2. 存活的生物 或 矿车实体
			// 3. 非本地玩家
			// 4. 非假人实体
			Stream<Entity> stream =
				StreamSupport.stream(MC.world.getEntities().spliterator(), true)
					.filter(e -> !e.isRemoved())
					.filter(e -> e instanceof LivingEntity
						&& ((LivingEntity)e).getHealth() > 0
						|| e instanceof AbstractMinecartEntity)
					.filter(e -> e != MC.player)
					.filter(e -> !(e instanceof FakePlayerEntity));
			
			// 应用实体过滤规则
			stream = entityFilters.applyTo(stream);
			
			// 选择距离最近的实体作为目标
			entity = stream
				.min(Comparator
					.comparingDouble(e -> MC.player.squaredDistanceTo(e)))
				.orElse(null);
			
			// 无有效实体时提示并禁用功能
			if(entity == null)
			{
				ChatUtils.error("未找到有效跟随目标！");
				setEnabled(false);
				return;
			}
		}
		
		// 初始化实体寻路器
		pathFinder = new EntityPathFinder();
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		// 聊天栏提示当前跟随的实体
		ChatUtils.message("开始跟随：" + entity.getName().getString());
	}
	
	@Override
	protected void onDisable()
	{
		// 移除事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// 重置寻路相关变量
		pathFinder = null;
		processor = null;
		ticksProcessing = 0;
		// 释放移动控制（恢复玩家手动操作）
		PathProcessor.releaseControls();
		
		// 聊天栏提示停止跟随
		if(entity != null)
			ChatUtils
				.message("停止跟随：" + entity.getName().getString());
		
		// 清空跟随实体
		entity = null;
	}
	
	@Override
	public void onUpdate()
	{
		// 检查玩家是否死亡：死亡时停止跟随
		if(MC.player.getHealth() <= 0)
		{
			if(entity == null)
				ChatUtils.message("停止跟随实体");
			setEnabled(false);
			return;
		}
		
		// 检查目标实体是否消失/死亡
		if(entity.isRemoved() || entity instanceof LivingEntity
			&& ((LivingEntity)entity).getHealth() <= 0)
		{
			// 重新寻找同名的有效实体
			entity = StreamSupport
				.stream(MC.world.getEntities().spliterator(), true)
				.filter(LivingEntity.class::isInstance)
				.filter(
					e -> !e.isRemoved() && ((LivingEntity)e).getHealth() > 0)
				.filter(e -> e != MC.player)
				.filter(e -> !(e instanceof FakePlayerEntity))
				.filter(e -> entity.getName().getString()
					.equalsIgnoreCase(e.getName().getString()))
				.min(Comparator
					.comparingDouble(e -> MC.player.squaredDistanceTo(e)))
				.orElse(null);
			
			// 未找到同名实体时停止跟随
			if(entity == null)
			{
				ChatUtils.message("目标实体消失，停止跟随");
				setEnabled(false);
				return;
			}
			
			// 重置寻路器和处理器
			pathFinder = new EntityPathFinder();
			processor = null;
			ticksProcessing = 0;
		}
		
		// 启用AI寻路时的逻辑
		if(useAi.isChecked())
		{
			// 重置寻路器条件：
			// 1. 无处理器/处理器完成/处理超时（≥10tick）/路径失效
			// 2. 寻路器完成/失败
			if((processor == null || processor.isDone() || ticksProcessing >= 10
				|| !pathFinder.isPathStillValid(processor.getIndex()))
				&& (pathFinder.isDone() || pathFinder.isFailed()))
			{
				pathFinder = new EntityPathFinder();
				processor = null;
				ticksProcessing = 0;
			}
			
			// 执行寻路计算
			if(!pathFinder.isDone() && !pathFinder.isFailed())
			{
				// 锁定移动控制（接管玩家操作）
				PathProcessor.lockControls();
				// 客户端侧转向目标实体中心
				WURST.getRotationFaker()
					.faceVectorClient(entity.getBoundingBox().getCenter());
				// 执行寻路思考
				pathFinder.think();
				// 格式化寻路路径
				pathFinder.formatPath();
				// 获取路径处理器
				processor = pathFinder.getProcessor();
			}
			
			// 执行路径移动
			if(!processor.isDone())
			{
				processor.process();
				ticksProcessing++;
			}
		}else
		{
			// 禁用AI时的简易跟随逻辑：
			// 1. 碰到方块且在地面时自动跳跃
			if(MC.player.horizontalCollision && MC.player.isOnGround())
				MC.player.jump();
			
			// 2. 在水中且目标在上方时自动上浮
			if(MC.player.isTouchingWater() && MC.player.getY() < entity.getY())
				MC.player.setVelocity(MC.player.getVelocity().add(0, 0.04, 0));
			
			// 3. 飞行时调整高度（保持与目标的垂直距离）
			if(!MC.player.isOnGround()
				&& (MC.player.getAbilities().flying
					|| WURST.getHax().flightHack.isEnabled())
				&& MC.player.squaredDistanceTo(entity.getX(), MC.player.getY(),
					entity.getZ()) <= MC.player.squaredDistanceTo(
						MC.player.getX(), entity.getY(), MC.player.getZ()))
			{
				// 目标在下方1格以上时按潜行（下降）
				if(MC.player.getY() > entity.getY() + 1D)
					MC.options.sneakKey.setPressed(true);
				// 目标在上方1格以上时按跳跃（上升）
				else if(MC.player.getY() < entity.getY() - 1D)
					MC.options.jumpKey.setPressed(true);
			}else
			{
				// 非飞行状态时释放潜行/跳跃键
				MC.options.sneakKey.setPressed(false);
				MC.options.jumpKey.setPressed(false);
			}
			
			// 4. 转向目标实体并向前移动（超过跟随距离时）
			WURST.getRotationFaker()
				.faceVectorClient(entity.getBoundingBox().getCenter());
			double distanceSq = Math.pow(distance.getValue(), 2);
			MC.options.forwardKey
				.setPressed(MC.player.squaredDistanceTo(entity.getX(),
					MC.player.getY(), entity.getZ()) > distanceSq);
		}
	}
	
	/**
	 * 渲染寻路路径（可视化显示）
	 */
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		PathCmd pathCmd = WURST.getCmds().pathCmd;
		pathFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
			pathCmd.isDepthTest());
	}
	
	/**
	 * 手动设置跟随的实体
	 * @param entity 目标实体
	 */
	public void setEntity(Entity entity)
	{
		this.entity = entity;
	}
	
	/**
	 * 实体寻路器：专门针对跟随实体的寻路逻辑
	 */
	private class EntityPathFinder extends PathFinder
	{
		public EntityPathFinder()
		{
			// 以目标实体当前位置为终点初始化寻路器
			super(BlockPos.ofFloored(entity.getPos()));
			// 设置寻路思考间隔（1tick）
			setThinkTime(1);
		}
		
		/**
		 * 检查寻路是否完成（到达跟随距离内）
		 */
		@Override
		protected boolean checkDone()
		{
			Vec3d center = Vec3d.ofCenter(current);
			double distanceSq = Math.pow(distance.getValue(), 2);
			// 到达跟随距离内时标记寻路完成
			return done = entity.squaredDistanceTo(center) <= distanceSq;
		}
		
		/**
		 * 格式化寻路路径（未完成时标记为失败）
		 */
		@Override
		public ArrayList<PathPos> formatPath()
		{
			if(!done)
				failed = true;
			
			return super.formatPath();
		}
	}
}