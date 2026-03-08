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
import net.minecraft.util.Hand;
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
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.PauseAttackOnContainersSetting;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.FakePlayerEntity;

/**
 * 护卫模式（Protect）：自动保护指定友方实体（玩家/生物），
 * 靠近友方并攻击接近的敌方实体，支持AI寻路和基础跟随两种模式。
 */
@DontSaveState // 禁用状态保存（重启客户端后默认关闭）
public final class ProtectHack extends Hack
	implements UpdateListener, RenderListener
{
	// 攻击速度设置：控制攻击敌方实体的频率
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	// 挥动手臂设置：控制攻击时的手臂摆动动画（客户端侧显示）
	private final SwingHandSetting swingHand = new SwingHandSetting(
		SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);
	
	// AI模式开关：是否启用实验性AI寻路（替代基础跟随）
	private final CheckboxSetting useAi =
		new CheckboxSetting("启用AI寻路（实验性）", false);
	
	// 容器暂停攻击：打开箱子/熔炉等容器时暂停攻击
	private final PauseAttackOnContainersSetting pauseOnContainers =
		new PauseAttackOnContainersSetting(true);
	
	// 实体过滤列表：筛选需攻击/忽略的实体（战斗通用过滤规则）
	private final EntityFilterList entityFilters =
		new EntityFilterList(FilterPlayersSetting.genericCombat(false), // 玩家过滤
			FilterSleepingSetting.genericCombat(false), // 睡眠玩家过滤
			FilterFlyingSetting.genericCombat(0), // 飞行实体过滤
			FilterHostileSetting.genericCombat(false), // 敌对生物过滤
			FilterNeutralSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF), // 中立生物过滤
			FilterPassiveSetting.genericCombat(false), // 被动生物过滤
			FilterPassiveWaterSetting.genericCombat(false), // 水生被动生物过滤
			FilterBabiesSetting.genericCombat(false), // 幼年生物过滤
			FilterBatsSetting.genericCombat(false), // 蝙蝠过滤
			FilterSlimesSetting.genericCombat(false), // 史莱姆过滤
			FilterPetsSetting.genericCombat(false), // 宠物过滤
			FilterVillagersSetting.genericCombat(false), // 村民过滤
			FilterZombieVillagersSetting.genericCombat(false), // 僵尸村民过滤
			FilterGolemsSetting.genericCombat(false), // 铁傀儡/雪傀儡过滤
			FilterPiglinsSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF), // 猪灵过滤
			FilterZombiePiglinsSetting
				.genericCombat(FilterZombiePiglinsSetting.Mode.OFF), // 僵尸猪灵过滤
			FilterEndermenSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF), // 末影人过滤
			FilterShulkersSetting.genericCombat(false), // 潜影贝过滤
			FilterAllaysSetting.genericCombat(false), // 悦灵过滤
			FilterInvisibleSetting.genericCombat(false), // 隐身实体过滤
			FilterNamedSetting.genericCombat(false), // 命名实体过滤
			FilterShulkerBulletSetting.genericCombat(false), // 潜影贝子弹过滤
			FilterArmorStandsSetting.genericCombat(false), // 盔甲架过滤
			FilterCrystalsSetting.genericCombat(true)); // 水晶过滤（默认攻击）
	
	// AI寻路相关：实体寻路器、路径处理器、寻路计时
	private EntityPathFinder pathFinder;
	private PathProcessor processor;
	private int ticksProcessing;
	
	// 友方实体（保护目标）、敌方实体（攻击目标）
	private Entity friend;
	private Entity enemy;
	
	// 距离阈值：与友方保持2格距离，与敌方保持3格距离
	private double distanceF = 2;
	private double distanceE = 3;
	
	public ProtectHack()
	{
		super("使用AI (实验性)"); // 功能显示名称
		
		setCategory(Category.COMBAT); // 归类到“战斗”类别
		addSetting(speed); // 添加攻击速度设置
		addSetting(swingHand); // 添加挥动手臂设置
		addSetting(useAi); // 添加AI寻路开关
		addSetting(pauseOnContainers); // 添加容器暂停攻击设置
		
		// 批量添加实体过滤列表中的所有设置项
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	public String getRenderName()
	{
		// 界面显示名称：保护中则显示“护卫 [友方名称]”，否则显示“护卫模式”
		if(friend != null)
			return "护卫 " + friend.getName().getString();
		return "护卫模式";
	}
	
	@Override
	protected void onEnable()
	{
		// 禁用冲突功能：跟随、隧道挖掘、各类杀戮光环/自动攻击功能
		WURST.getHax().followHack.setEnabled(false);
		WURST.getHax().tunnellerHack.setEnabled(false);
		WURST.getHax().aimAssistHack.setEnabled(false);
		WURST.getHax().clickAuraHack.setEnabled(false);
		WURST.getHax().crystalAuraHack.setEnabled(false);
		WURST.getHax().fightBotHack.setEnabled(false);
		WURST.getHax().killauraLegitHack.setEnabled(false);
		WURST.getHax().killauraHack.setEnabled(false);
		WURST.getHax().multiAuraHack.setEnabled(false);
		WURST.getHax().triggerBotHack.setEnabled(false);
		WURST.getHax().tpAuraHack.setEnabled(false);
		
		// 未指定友方实体时，自动选择距离最近的有效实体作为保护目标
		if(friend == null)
		{
			Stream<Entity> stream = StreamSupport
				.stream(MC.world.getEntities().spliterator(), true)
				// 仅保留有生命的实体
				.filter(LivingEntity.class::isInstance)
				// 过滤：未被移除 + 生命值>0（存活）
				.filter(e -> !e.isRemoved() && ((LivingEntity)e).getHealth() > 0)
				// 排除自身玩家
				.filter(e -> e != MC.player)
				// 排除假人实体
				.filter(e -> !(e instanceof FakePlayerEntity));
			
			// 选择距离最近的实体作为友方
			friend = stream
				.min(Comparator.comparingDouble(e -> MC.player.squaredDistanceTo(e)))
				.orElse(null);
		}
		
		// 初始化AI寻路器（指向友方实体，保持2格距离）
		pathFinder = new EntityPathFinder(friend, distanceF);
		
		// 重置攻击计时器
		speed.resetTimer();
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 移除事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// 重置寻路相关数据
		pathFinder = null;
		processor = null;
		ticksProcessing = 0;
		PathProcessor.releaseControls(); // 释放移动控制
		
		// 重置敌方实体
		enemy = null;
		
		// 重置友方实体和移动按键
		if(friend != null)
		{
			MC.options.forwardKey.setPressed(false);
			friend = null;
		}
	}
	
	@Override
	public void onUpdate()
	{
		// 更新攻击计时器
		speed.updateTimer();
		
		// 打开容器时暂停攻击
		if(pauseOnContainers.shouldPause())
			return;
		
		// 验证有效性：友方/自身死亡/友方消失 → 禁用功能
		if(friend == null || friend.isRemoved()
			|| !(friend instanceof LivingEntity)
			|| ((LivingEntity)friend).getHealth() <= 0
			|| MC.player.getHealth() <= 0)
		{
			friend = null;
			enemy = null;
			setEnabled(false);
			return;
		}
		
		// 筛选敌方实体：6格范围内可攻击的实体（排除友方）
		Stream<Entity> stream = EntityUtils.getAttackableEntities()
			.filter(e -> MC.player.squaredDistanceTo(e) <= 36)
			.filter(e -> e != friend);
		
		// 应用实体过滤规则
		stream = entityFilters.applyTo(stream);
		
		// 选择距离最近的敌方实体
		enemy = stream
			.min(Comparator.comparingDouble(e -> MC.player.squaredDistanceTo(e)))
			.orElse(null);
		
		// 确定当前目标：有敌方则攻击敌方，无敌方则跟随友方
		Entity target =
			enemy == null || MC.player.squaredDistanceTo(friend) >= 24 * 24
				? friend : enemy;
		
		// 目标距离阈值：敌方3格，友方2格
		double distance = target == enemy ? distanceE : distanceF;
		
		// AI寻路模式逻辑
		if(useAi.isChecked())
		{
			// 重置寻路器：路径完成/超时/路径失效 → 重新寻路
			if((processor == null || processor.isDone() || ticksProcessing >= 10
				|| !pathFinder.isPathStillValid(processor.getIndex()))
				&& (pathFinder.isDone() || pathFinder.isFailed()))
			{
				pathFinder = new EntityPathFinder(target, distance);
				processor = null;
				ticksProcessing = 0;
			}
			
			// 执行寻路计算
			if(!pathFinder.isDone() && !pathFinder.isFailed())
			{
				PathProcessor.lockControls(); // 锁定移动控制
				// 客户端侧朝向目标中心
				WURST.getRotationFaker().faceVectorClient(target.getBoundingBox().getCenter());
				pathFinder.think(); // 寻路思考
				pathFinder.formatPath(); // 格式化路径
				processor = pathFinder.getProcessor(); // 获取路径处理器
			}
			
			// 执行路径移动
			if(processor != null && !processor.isDone())
			{
				processor.process(); // 处理路径移动
				ticksProcessing++; // 累计寻路计时
			}
		// 基础跟随模式逻辑（无AI）
		}else
		{
			// 撞到方块且在地面 → 自动跳跃
			if(MC.player.horizontalCollision && MC.player.isOnGround())
				MC.player.jump();
			
			// 在水中且目标在上方 → 自动上浮
			if(MC.player.isTouchingWater() && MC.player.getY() < target.getY())
				MC.player.addVelocity(0, 0.04, 0);
			
			// 飞行时调整高度（创造模式飞行/飞行功能启用）
			if(!MC.player.isOnGround()
				&& (MC.player.getAbilities().flying
					|| WURST.getHax().flightHack.isEnabled())
				&& MC.player.squaredDistanceTo(target.getX(), MC.player.getY(),
					target.getZ()) <= MC.player.squaredDistanceTo(
						MC.player.getX(), target.getY(), MC.player.getZ()))
			{
				// 目标在下方 → 按住潜行（下降）
				if(MC.player.getY() > target.getY() + 1D)
					MC.options.sneakKey.setPressed(true);
				// 目标在上方 → 按住跳跃（上升）
				else if(MC.player.getY() < target.getY() - 1D)
					MC.options.jumpKey.setPressed(true);
			}else
			{
				// 停止高度调整
				MC.options.sneakKey.setPressed(false);
				MC.options.jumpKey.setPressed(false);
			}
			
			// 朝向目标并跟随（距离超过阈值时按住前进键）
			WURST.getRotationFaker().faceVectorClient(target.getBoundingBox().getCenter());
			MC.options.forwardKey.setPressed(MC.player.distanceTo(target) > (target == friend ? distanceF : distanceE));
		}
		
		// 攻击敌方实体逻辑
		if(target == enemy)
		{
			// 自动切换最优武器（如果AutoSword启用）
			WURST.getHax().autoSwordHack.setSlot(enemy);
			
			// 攻击冷却未结束 → 跳过
			if(!speed.isTimeToAttack())
				return;
			
			// 执行攻击
			MC.interactionManager.attackEntity(MC.player, enemy);
			swingHand.swing(Hand.MAIN_HAND); // 挥动手臂动画
			speed.resetTimer(); // 重置攻击计时器
		}
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// AI模式下渲染寻路路径（调试用）
		if(!useAi.isChecked())
			return;
		
		PathCmd pathCmd = WURST.getCmds().pathCmd;
		pathFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
			pathCmd.isDepthTest());
	}
	
	/**
	 * 设置保护目标（友方实体）
	 * @param friend 要保护的实体
	 */
	public void setFriend(Entity friend)
	{
		this.friend = friend;
	}
	
	/**
	 * 实体寻路器：继承自基础寻路器，适配实体跟随的寻路逻辑
	 */
	private class EntityPathFinder extends PathFinder
	{
		private final Entity entity; // 目标实体
		private double distanceSq; // 距离阈值的平方（优化性能）
		
		public EntityPathFinder(Entity entity, double distance)
		{
			super(BlockPos.ofFloored(entity.getPos())); // 初始化到实体所在方块
			this.entity = entity;
			distanceSq = distance * distance; // 计算距离平方
			setThinkTime(1); // 设置寻路思考间隔（1tick）
		}
		
		@Override
		protected boolean checkDone()
		{
			// 寻路完成判定：与目标实体距离≤设定阈值
			return done = entity.squaredDistanceTo(Vec3d.ofCenter(current)) <= distanceSq;
		}
		
		@Override
		public ArrayList<PathPos> formatPath()
		{
			// 未完成寻路则标记为失败
			if(!done)
				failed = true;
			
			return super.formatPath();
		}
	}
}