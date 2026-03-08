/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.wurstclient.Category;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.MouseUpdateListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

/**
 * 简易杀戮光环（KillauraLegit）：模拟手动攻击的自动化战斗功能，
 * 相比普通杀戮光环更贴近人工操作（缓慢转头、合规攻击频率），降低被反作弊检测的概率，
 * 支持自定义攻击范围、速度、优先级，附带血量伤害指示器。
 */
public final class KillauraLegitHack extends Hack implements UpdateListener,
	HandleInputListener, MouseUpdateListener, RenderListener
{
	// 攻击范围：最大4.25格（原版近战攻击最大距离）
	private final SliderSetting range =
		new SliderSetting("攻击范围", 4.25, 1, 4.25, 0.05, ValueDisplay.DECIMAL);
	
	// 攻击速度：控制攻击频率（贴合原版冷却）
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	// 速度随机化：通过随机攻击间隔规避反作弊检测
	private final SliderSetting speedRandMS =
		new SliderSetting("攻击间隔随机化",
			"通过随机调整攻击间隔帮助规避反作弊插件检测。\n\n" + "推荐为Vulcan反作弊设置±100ms。\n\n"
				+ "NoCheat+、AAC、Grim、Verus、Spartan反作弊及原版服务器设置0（关闭）即可。",
			100, 0, 1000, 50, ValueDisplay.INTEGER.withPrefix("±")
				.withSuffix("ms").withLabel(0, "关闭"));
	
	// 转头速度：控制自动转向目标的速度（度/秒）
	private final SliderSetting rotationSpeed =
		new SliderSetting("转头速度", 600, 10, 3600, 10,
			ValueDisplay.DEGREES.withSuffix("/秒"));
	
	// 攻击优先级：决定优先攻击哪个实体
	private final EnumSetting<Priority> priority = new EnumSetting<>("攻击优先级",
		"决定优先攻击哪个实体。\n"
			+ "§l距离§r - 优先攻击最近的实体。\n"
			+ "§l角度§r - 优先攻击需要转头幅度最小的实体。\n"
			+ "§l血量§r - 优先攻击血量最低的实体。",
		Priority.values(), Priority.ANGLE);
	
	// 视野范围（FOV）：准星周围可攻击的角度范围
	private final SliderSetting fov = new SliderSetting("视野范围(FOV)",
		"视野范围 - 实体离准星的最大角度，超过则忽略该实体。\n"
			+ "360° = 可攻击周围所有实体。",
		360, 30, 360, 10, ValueDisplay.DEGREES);
	
	// 挥动手臂样式：无副手选项（仅主手攻击）
	private final SwingHandSetting swingHand =
		SwingHandSetting.withoutOffOption(
			SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);
	
	// 伤害指示器：在目标实体上渲染与剩余血量成反比的彩色方框
	private final CheckboxSetting damageIndicator = new CheckboxSetting(
		"伤害指示器",
		"在目标实体内部渲染彩色方框，方框大小与剩余血量成反比（血量越少方框越大）。",
		true);
	
	// 实体过滤列表：与普通杀戮光环相同，但默认规则更严格
	private final EntityFilterList entityFilters =
		new EntityFilterList(FilterPlayersSetting.genericCombat(false), // 玩家过滤
			FilterSleepingSetting.genericCombat(true), // 睡眠玩家过滤
			FilterFlyingSetting.genericCombat(0.5), // 飞行实体过滤
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
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF), // 僵尸猪灵过滤
			FilterEndermenSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF), // 末影人过滤
			FilterShulkersSetting.genericCombat(false), // 潜影贝过滤
			FilterAllaysSetting.genericCombat(false), // 悦灵过滤
			FilterInvisibleSetting.genericCombat(true), // 隐身实体过滤
			FilterNamedSetting.genericCombat(false), // 命名实体过滤
			FilterShulkerBulletSetting.genericCombat(false), // 潜影贝子弹过滤
			FilterArmorStandsSetting.genericCombat(false), // 盔甲架过滤
			FilterCrystalsSetting.genericCombat(false)); // 水晶过滤
	
	// 当前攻击目标
	private Entity target;
	// 目标偏航角（Yaw）
	private float nextYaw;
	// 目标俯仰角（Pitch）
	private float nextPitch;
	
	public KillauraLegitHack()
	{
		super("合法杀戮光环"); // 功能显示名称
		setCategory(Category.COMBAT); // 归类到“战斗”类别
		
		addSetting(range); // 添加攻击范围设置
		addSetting(speed); // 添加攻击速度设置
		addSetting(speedRandMS); // 添加攻击间隔随机化设置
		addSetting(rotationSpeed); // 添加转头速度设置
		addSetting(priority); // 添加攻击优先级设置
		addSetting(fov); // 添加视野范围设置
		addSetting(swingHand); // 添加挥动手臂样式设置
		addSetting(damageIndicator); // 添加伤害指示器设置
		
		// 批量添加实体过滤列表中的所有设置项
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		// 禁用冲突的战斗类功能
		WURST.getHax().aimAssistHack.setEnabled(false);
		WURST.getHax().clickAuraHack.setEnabled(false);
		WURST.getHax().crystalAuraHack.setEnabled(false);
		WURST.getHax().fightBotHack.setEnabled(false);
		WURST.getHax().killauraHack.setEnabled(false);
		WURST.getHax().multiAuraHack.setEnabled(false);
		WURST.getHax().protectHack.setEnabled(false);
		WURST.getHax().triggerBotHack.setEnabled(false);
		WURST.getHax().tpAuraHack.setEnabled(false);
		
		// 重置攻击计时器（应用随机化间隔）
		speed.resetTimer(speedRandMS.getValue());
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(MouseUpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 移除事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(MouseUpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		// 清空攻击目标
		target = null;
	}
	
	@Override
	public void onUpdate()
	{
		// 重置当前目标
		target = null;
		
		// 打开物品栏/容器界面时不攻击
		if(MC.currentScreen instanceof HandledScreen)
			return;
		
		// 筛选可攻击的实体
		Stream<Entity> stream = EntityUtils.getAttackableEntities();
		double rangeSq = range.getValueSq(); // 攻击范围的平方（优化性能）
		// 过滤：在攻击范围内的实体
		stream = stream.filter(e -> MC.player.squaredDistanceTo(e) <= rangeSq);
		
		// 过滤：在视野范围内的实体（FOV<360时）
		if(fov.getValue() < 360.0)
			stream = stream.filter(e -> RotationUtils.getAngleToLookVec(
				e.getBoundingBox().getCenter()) <= fov.getValue() / 2.0);
		
		// 应用实体过滤规则
		stream = entityFilters.applyTo(stream);
		
		// 按优先级选择攻击目标
		target = stream.min(priority.getSelected().comparator).orElse(null);
		if(target == null)
			return;
		
		// 检查视线是否畅通（无方块阻挡）
		if(!BlockUtils.hasLineOfSight(target.getBoundingBox().getCenter()))
		{
			target = null;
			return;
		}
		
		// 自动切换最优武器（如果AutoSword启用）
		WURST.getHax().autoSwordHack.setSlot(target);
		// 客户端侧转向目标实体
		faceEntityClient(target);
	}
	
	@Override
	public void onHandleInput()
	{
		// 无目标时跳过
		if(target == null)
			return;
		
		// 更新攻击计时器
		speed.updateTimer();
		// 攻击冷却未结束时跳过
		if(!speed.isTimeToAttack())
			return;
		
		// 未对准目标时跳过
		if(!RotationUtils.isFacingBox(target.getBoundingBox(),
			range.getValue()))
			return;
		
		// 执行攻击
		MC.interactionManager.attackEntity(MC.player, target);
		// 播放挥动手臂动画
		swingHand.swing(Hand.MAIN_HAND);
		// 重置攻击计时器（应用随机化间隔）
		speed.resetTimer(speedRandMS.getValue());
	}
	
	/**
	 * 客户端侧缓慢转向目标实体（模拟手动转头）
	 * @param entity 目标实体
	 * @return 是否已对准目标
	 */
	private boolean faceEntityClient(Entity entity)
	{
		// 获取对准目标所需的旋转角度
		Box box = entity.getBoundingBox();
		Rotation needed = RotationUtils.getNeededRotations(box.getCenter());
		
		// 缓慢转向目标中心（按转头速度限制）
		Rotation next = RotationUtils.slowlyTurnTowards(needed,
			rotationSpeed.getValueI() / 20F);
		nextYaw = next.yaw();
		nextPitch = next.pitch();
		
		// 检查是否已对准目标中心
		if(RotationUtils.isAlreadyFacing(needed))
			return true;
		
		// 未对准中心时，检查是否对准目标包围盒内任意位置
		return RotationUtils.isFacingBox(box, range.getValue());
	}
	
	@Override
	public void onMouseUpdate(MouseUpdateEvent event)
	{
		// 无目标或玩家未加载时跳过
		if(target == null || MC.player == null)
			return;
		
		// 计算需要调整的角度差值
		int diffYaw = (int)(nextYaw - MC.player.getYaw());
		int diffPitch = (int)(nextPitch - MC.player.getPitch());
		// 角度差值过小（<1度）时跳过
		if(MathHelper.abs(diffYaw) < 1 && MathHelper.abs(diffPitch) < 1)
			return;
		
		// 调整鼠标移动量，模拟手动转头
		event.setDeltaX(event.getDefaultDeltaX() + diffYaw);
		event.setDeltaY(event.getDefaultDeltaY() + diffPitch);
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// 无目标或禁用伤害指示器时跳过
		if(target == null || !damageIndicator.isChecked())
			return;
		
		// 计算血量比例（1=满血，0=空血）
		float p = 1;
		if(target instanceof LivingEntity le)
			p = (le.getMaxHealth() - le.getHealth()) / le.getMaxHealth();
		// 计算颜色（红→黄→绿，对应血量从低到高）
		float red = p * 2F;
		float green = 2 - red;
		float[] rgb = {red, green, 0};
		// 实心方框颜色（25%透明度）
		int quadColor = RenderUtils.toIntColor(rgb, 0.25F);
		// 轮廓线条颜色（50%透明度）
		int lineColor = RenderUtils.toIntColor(rgb, 0.5F);
		
		// 获取目标实体的插值包围盒（平滑渲染）
		Box box = EntityUtils.getLerpedBox(target, partialTicks);
		// 血量越低，方框越大（收缩比例与血量成正比）
		if(p < 1)
			box = box.contract((1 - p) * 0.5 * box.getXLength(),
				(1 - p) * 0.5 * box.getYLength(),
				(1 - p) * 0.5 * box.getZLength());
		
		// 绘制实心方框
		RenderUtils.drawSolidBox(matrixStack, box, quadColor, false);
		// 绘制轮廓方框
		RenderUtils.drawOutlinedBox(matrixStack, box, lineColor, false);
	}
	
	/**
	 * 攻击优先级枚举：距离/角度/血量
	 */
	private enum Priority
	{
		DISTANCE("距离", e -> MC.player.squaredDistanceTo(e)), // 按距离排序（近→远）
		
		ANGLE("角度",
			e -> RotationUtils
				.getAngleToLookVec(e.getBoundingBox().getCenter())), // 按角度排序（小→大）
		
		HEALTH("血量", e -> e instanceof LivingEntity
			? ((LivingEntity)e).getHealth() : Integer.MAX_VALUE); // 按血量排序（低→高）
		
		private final String name; // 显示名称
		private final Comparator<Entity> comparator; // 比较器
		
		private Priority(String name, ToDoubleFunction<Entity> keyExtractor)
		{
			this.name = name;
			comparator = Comparator.comparingDouble(keyExtractor);
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}