/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autofish.AutoFishDebugDraw;
import net.wurstclient.hacks.autofish.AutoFishRodSelector;
import net.wurstclient.hacks.autofish.FishingSpotManager;
import net.wurstclient.hacks.autofish.ShallowWaterWarningCheckbox;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"自动钓鱼","AutoFishing", "auto fishing", "AutoFisher", "auto fisher",
	"AFKFishBot", "afk fish bot", "AFKFishingBot", "afk fishing bot",
	"AFKFisherBot", "afk fisher bot"})
public final class AutoFishHack extends Hack
	implements UpdateListener, PacketInputListener, RenderListener
{
	// 咬钩检测模式
	private final EnumSetting<AutoFishHack.BiteMode> biteMode =
		new EnumSetting<>("咬钩检测模式",
			"\u00a7l声音\u00a7r模式通过监听咬钩音效检测咬钩。该方式准确性较低，但更难被反作弊检测。"
				+ " 参考「有效范围」设置项。\n\n"
				+ "\u00a7l实体\u00a7r模式通过检测钓鱼钩的实体更新数据包来识别咬钩。"
				+ " 比声音模式更准确，但更容易被反作弊检测。",
			AutoFishHack.BiteMode.values(), AutoFishHack.BiteMode.SOUND);
	
	// 有效范围（仅声音模式生效）
	private final SliderSetting validRange = new SliderSetting("有效范围",
		"超出此范围的咬钩音效将被忽略。\n\n"
			+ "若咬钩未被检测到，可增大此数值；若检测到他人的咬钩，可减小此数值。\n\n"
			+ "当「咬钩检测模式」设为「实体」时，此设置无效。",
		1.5, 0.25, 8, 0.25, ValueDisplay.DECIMAL);
	
	// 上钩延迟（检测到咬钩后等待多久收杆）
	private final SliderSetting catchDelay = new SliderSetting("上钩延迟",
		"检测到咬钩后，自动钓鱼将等待该时长再收杆。", 0, 0, 60,
		1, ValueDisplay.INTEGER.withSuffix(" 刻").withLabel(1, "1刻"));
	
	// 重试延迟（抛竿/收杆失败后等待多久重试）
	private final SliderSetting retryDelay = new SliderSetting("重试延迟",
		"若抛竿或收杆操作失败，自动钓鱼将等待该时长后重试。",
		15, 0, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" 刻").withLabel(1, "1刻"));
	
	// 耐心值（无咬钩时等待多久收杆重新抛竿）
	private final SliderSetting patience = new SliderSetting("耐心时长",
		"若长时间未检测到咬钩，自动钓鱼将等待该时长后收杆。",
		60, 10, 120, 1, ValueDisplay.INTEGER.withSuffix("秒"));
	
	// 浅水区警告开关
	private final ShallowWaterWarningCheckbox shallowWaterWarning =
		new ShallowWaterWarningCheckbox();
	
	// 钓鱼点管理器（记录/管理钓鱼位置）
	private final FishingSpotManager fishingSpots = new FishingSpotManager();
	// 调试绘制器（绘制有效范围、咬钩音效位置等调试信息）
	private final AutoFishDebugDraw debugDraw =
		new AutoFishDebugDraw(validRange, fishingSpots);
	// 鱼竿选择器（自动选择可用的钓鱼竿）
	private final AutoFishRodSelector rodSelector =
		new AutoFishRodSelector(this);
	
	// 抛竿计时器（控制抛竿重试间隔）
	private int castRodTimer;
	// 收杆计时器（控制收杆时机）
	private int reelInTimer;
	// 咬钩检测标记
	private boolean biteDetected;
	
	public AutoFishHack()
	{
		super("自动钓鱼");
		setCategory(Category.OTHER);
		addSetting(biteMode);
		addSetting(validRange);
		addSetting(catchDelay);
		addSetting(retryDelay);
		addSetting(patience);
		debugDraw.getSettings().forEach(this::addSetting);
		rodSelector.getSettings().forEach(this::addSetting);
		addSetting(shallowWaterWarning);
		fishingSpots.getSettings().forEach(this::addSetting);
	}
	
	@Override
	public String getRenderName()
	{
		// 无可用鱼竿时，在功能名称后标注
		if(rodSelector.isOutOfRods())
			return getName() + " [无可用鱼竿]";
		
		return getName();
	}
	
	@Override
	protected void onEnable()
	{
		// 重置计时器和状态标记
		castRodTimer = 0;
		reelInTimer = 0;
		biteDetected = false;
		// 重置子模块状态
		rodSelector.reset();
		debugDraw.reset();
		fishingSpots.reset();
		shallowWaterWarning.reset();
		
		// 禁用冲突功能
		WURST.getHax().antiAfkHack.setEnabled(false);
		WURST.getHax().aimAssistHack.setEnabled(false);
		
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		// 更新计时器（递减）
		if(castRodTimer > 0)
			castRodTimer--;
		if(reelInTimer > 0)
			reelInTimer--;
		
		// 更新鱼竿状态（无可用鱼竿则直接返回）
		if(!rodSelector.update())
			return;
		
		// 未在钓鱼：执行抛竿操作
		if(!isFishing())
		{
			// 抛竿冷却中：跳过
			if(castRodTimer > 0)
				return;
			
			// 设置收杆计时器（耐心时长，转换为刻：秒×20）
			reelInTimer = 20 * patience.getValueI();
			// 钓鱼点管理器记录抛竿操作（失败则跳过）
			if(!fishingSpots.onCast())
				return;
			
			// 执行抛竿
			MC.doItemUse();
			// 设置抛竿重试延迟
			castRodTimer = retryDelay.getValueI();
			return;
		}
		
		// 检测到咬钩：检查水域类型并设置收杆延迟
		if(biteDetected)
		{
			shallowWaterWarning.checkWaterType();
			reelInTimer = catchDelay.getValueI();
			// 钓鱼点管理器记录咬钩位置
			fishingSpots.onBite(MC.player.fishHook);
			// 重置咬钩标记
			biteDetected = false;
			
		// 鱼钩勾到实体：直接设置收杆延迟
		}else if(MC.player.fishHook.getHookedEntity() != null)
			reelInTimer = catchDelay.getValueI();
		
		// 收杆计时器归零：执行收杆操作
		if(reelInTimer == 0)
		{
			MC.doItemUse();
			// 设置收杆/抛竿重试延迟
			reelInTimer = retryDelay.getValueI();
			castRodTimer = retryDelay.getValueI();
		}
	}
	
	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		// 根据选中的咬钩检测模式处理数据包
		switch(biteMode.getSelected())
		{
			case SOUND -> processSoundUpdate(event);
			case ENTITY -> processEntityUpdate(event);
		}
	}
	
	/**
	 * 处理声音模式的数据包（检测咬钩音效）
	 * @param event 数据包输入事件
	 */
	private void processSoundUpdate(PacketInputEvent event)
	{
		// 过滤非音效数据包
		if(!(event.getPacket() instanceof PlaySoundS2CPacket sound))
			return;
		
		// 过滤非咬钩溅水音效
		if(!SoundEvents.ENTITY_FISHING_BOBBER_SPLASH
			.equals(sound.getSound().value()))
			return;
		
		// 玩家未在钓鱼：跳过
		if(!isFishing())
			return;
		
		// 更新音效位置（用于调试绘制）
		debugDraw.updateSoundPos(sound);
		
		// 计算音效与鱼钩的切比雪夫距离（仅检测X/Z轴）
		Vec3d bobber = MC.player.fishHook.getPos();
		double dx = Math.abs(sound.getX() - bobber.getX());
		double dz = Math.abs(sound.getZ() - bobber.getZ());
		// 超出有效范围：跳过
		if(Math.max(dx, dz) > validRange.getValue())
			return;
		
		// 标记检测到咬钩
		biteDetected = true;
	}
	
	/**
	 * 处理实体模式的数据包（检测鱼钩实体更新）
	 * @param event 数据包输入事件
	 */
	private void processEntityUpdate(PacketInputEvent event)
	{
		// 过滤非实体追踪更新数据包
		if(!(event.getPacket() instanceof EntityTrackerUpdateS2CPacket update))
			return;
		
		// 过滤非钓鱼钩实体
		if(!(MC.world
			.getEntityById(update.id()) instanceof FishingBobberEntity bobber))
			return;
		
		// 过滤非玩家自身的鱼钩
		if(bobber != MC.player.fishHook)
			return;
		
		// 玩家未在钓鱼：跳过
		if(!isFishing())
			return;
		
		// 标记检测到咬钩
		biteDetected = true;
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// 绘制调试信息（有效范围、音效位置等）
		debugDraw.render(matrixStack, partialTicks);
	}
	
	/**
	 * 判断玩家是否正在钓鱼
	 * @return 钓鱼状态
	 */
	private boolean isFishing()
	{
		ClientPlayerEntity player = MC.player;
		return player != null && player.fishHook != null
			&& !player.fishHook.isRemoved()
			&& player.getMainHandStack().isOf(Items.FISHING_ROD);
	}
	
	/**
	 * 咬钩检测模式枚举
	 */
	private enum BiteMode
	{
		SOUND("声音"),
		ENTITY("实体");
		
		private final String name;
		
		private BiteMode(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}