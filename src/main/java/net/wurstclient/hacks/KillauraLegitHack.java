/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.stream.Stream;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.wurstclient.Category;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.MouseUpdateListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AimAtSetting;
import net.wurstclient.settings.AttackPrioritySetting;
import net.wurstclient.settings.AttackPrioritySetting.AttackPriority;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FovSliderSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.MouseAimController;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.text.WText;

/**
 * 简易杀戮光环（KillauraLegit）：模拟手动攻击的自动化战斗功能，
 * 相比普通杀戮光环更贴近人工操作（缓慢转头、合规攻击频率），降低被反作弊检测的概率，
 * 支持自定义攻击范围、速度、优先级，附带血量伤害指示器。
 */
public final class KillauraLegitHack extends Hack implements UpdateListener,
	PreMotionListener, HandleInputListener, MouseUpdateListener, RenderListener
{
	// 攻击范围：最大4.25格（原版近战攻击最大距离）
	private final SliderSetting range =
		new SliderSetting("攻击范围", 4.25, 1, 4.25, 0.05, ValueDisplay.DECIMAL);
	
	// 攻击速度：控制攻击频率（贴合原版冷却）
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	// 速度随机化：通过随机攻击间隔规避反作弊检测
	private final SliderSetting speedRandMS = new SliderSetting("攻击间隔随机化",
		"通过随机调整攻击间隔帮助规避反作弊插件检测。\n\n" + "推荐为Vulcan反作弊设置±100ms。\n\n"
			+ "NoCheat+、AAC、Grim、Verus、Spartan反作弊及原版服务器设置0（关闭）即可。",
		100, 0, 1000, 50, ValueDisplay.INTEGER.withPrefix("±").withSuffix("ms")
			.withLabel(0, "关闭"));
	
	// 转头速度：控制自动转向目标的速度（度/秒）
	private final SliderSetting rotationSpeed = new SliderSetting("转头速度", 600,
		10, 3600, 10, ValueDisplay.DEGREES.withSuffix("/秒"));
	
	// 攻击优先级：决定优先攻击哪个实体
	private final AttackPrioritySetting priority =
		AttackPrioritySetting.withoutAngleDistOption("攻击优先级",
			AttackPrioritySetting.genericDescription(), AttackPriority.ANGLE);
	
	// 视野范围（FOV）：准星周围可攻击的角度范围
	private final FovSliderSetting fov = new FovSliderSetting("视野范围(FOV)",
		WText.translated("description.wurst.setting.generic.fov_combat"), 360);
	
	private final AimAtSetting aimAt = new AimAtSetting(
		AimAtSetting.genericDescription(this), AimAtSetting.AimAt.CENTER);
	
	private final SliderSetting ignoreMouseInput =
		new SliderSetting("Ignore mouse input",
			WText.translated(
				"description.wurst.setting.generic.ignore_mouse_input",
				getName()),
			0, 0, 1, 0.01, ValueDisplay.PERCENTAGE);
	
	// 挥动手臂样式：无副手选项（仅主手攻击）
	private final SwingHandSetting swingHand =
		SwingHandSetting.withoutOffOption(
			SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);
	
	// 伤害指示器：在目标实体上渲染与剩余血量成反比的彩色方框
	private final CheckboxSetting damageIndicator = new CheckboxSetting("伤害指示器",
		"在目标实体内部渲染彩色方框，方框大小与剩余血量成反比（血量越少方框越大）。", true);
	
	// 实体过滤列表：与普通杀戮光环相同，但默认规则更严格
	private final EntityFilterList entityFilters =
		new EntityFilterList(FilterPlayersSetting.genericCombat(false), // 玩家过滤
			FilterCreativeSetting.genericCombat(true), // 创造模式玩家过滤
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
			FilterProjectilesSetting.genericCombat(false), // 可反弹火球过滤
			FilterInteractionsSetting.genericCombat(true), // 交互实体过滤
			FilterArmorStandsSetting.genericCombat(false), // 盔甲架过滤
			FilterCrystalsSetting.genericCombat(false)); // 水晶过滤
	
	private final MouseAimController mouseAim = new MouseAimController();
	// 当前攻击目标
	private Entity target;
	
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
		addSetting(aimAt);
		addSetting(ignoreMouseInput);
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
		WURST.getHax().autoFishHack.setEnabled(false);
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
		EVENTS.add(PreMotionListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(MouseUpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 移除事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(MouseUpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		// 清空攻击目标
		target = null;
		mouseAim.reset();
	}
	
	@Override
	public void onUpdate()
	{
		// 重置当前目标
		target = null;
		
		// 打开物品栏/容器界面时不攻击
		if(MC.currentScreen instanceof HandledScreen)
			return;
		
		double rangeSq = range.getValueSq(); // 攻击范围的平方（优化性能）
		Stream<Entity> stream = EntityUtils.getMeleeAttackableEntities()
			.filter(e -> EntityUtils.distanceToHitboxSq(e) <= rangeSq)
			.filter(fov.asEntityPredicate(aimAt::getAimPoint))
			.filter(e -> BlockUtils.hasLineOfSight(aimAt.getAimPoint(e)));
		
		// 应用实体过滤规则
		stream = entityFilters.applyTo(stream);
		
		// 按优先级选择攻击目标
		target =
			stream.min(priority.getComparator(aimAt::getAimPoint)).orElse(null);
		if(target == null)
			return;
		
		// 自动切换最优武器（如果AutoSword启用）
		if(!MC.player.isUsingItem())
			WURST.getHax().autoSwordHack.setSlot(target);
	}
	
	@Override
	public void onPreMotion()
	{
		if(target == null)
		{
			mouseAim.reset();
			return;
		}
		
		mouseAim.tick(aimAt.getAimPoint(target), rotationSpeed.getValueI());
	}
	
	@Override
	public void onHandleInput()
	{
		// 更新攻击计时器
		speed.updateTimer();
		// 攻击冷却未结束时跳过
		if(!speed.isTimeToAttack())
			return;
		
		if(target == null || MC.player.isUsingItem())
			return;
		
		// 未对准目标时跳过
		if(!RotationUtils.isFacingBoxServerSide(target.getBoundingBox(),
			range.getValue()))
			return;
		
		// 执行攻击
		MC.interactionManager.attackEntity(MC.player, target);
		// 播放挥动手臂动画
		swingHand.swing(Hand.MAIN_HAND);
		// 重置攻击计时器（应用随机化间隔）
		speed.resetTimer(speedRandMS.getValue());
	}
	
	@Override
	public void onMouseUpdate(MouseUpdateEvent event)
	{
		if(target != null)
			mouseAim.applyTo(event, ignoreMouseInput.getValue());
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
}
