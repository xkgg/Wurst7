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
import java.util.stream.Collectors;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RotationUtils;

/**
 * 范围爆破（Kaboom）：以玩家为中心触发大范围方块破坏功能，
 * 模拟爆炸效果（音效+粒子），可自定义破坏强度，仅在非飞行/创造模式且落地时生效，
 * 避免因空中使用触发服务器反作弊检测导致被踢出。
 */
public final class KaboomHack extends Hack implements UpdateListener
{
	// 爆破强度：控制单次破坏的方块数量（32-512，步长32）
	private final SliderSetting power =
		new SliderSetting("核爆强度", "设置单次爆破破坏的方块数量",
			128, 32, 512, 32, ValueDisplay.INTEGER);
	
	// 爆炸音效：是否播放爆炸音效
	private final CheckboxSetting sound = new CheckboxSetting("爆炸音效",
		"触发爆破时播放爆炸音效", true);
	
	// 爆炸粒子：是否生成爆炸粒子效果
	private final CheckboxSetting particles = new CheckboxSetting("爆炸粒子",
		"触发爆破时生成爆炸粒子特效", true);
	
	// 随机数生成器：用于音效音调随机化
	private final Random random = Random.create();
	
	public KaboomHack()
	{
		super("核爆"); // 功能显示名称
		setCategory(Category.BLOCKS); // 归类到“方块”类别
		addSetting(power); // 添加爆破强度设置
		addSetting(sound); // 添加爆炸音效设置
		addSetting(particles); // 添加爆炸粒子设置
	}
	
	@Override
	protected void onEnable()
	{
		// 注册帧更新监听器（执行爆破逻辑）
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 移除帧更新监听器
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		// 安全校验：非创造模式且处于飞行/悬空状态时终止（防止被服务器踢出）
		if(!MC.player.getAbilities().creativeMode && !MC.player.isOnGround())
			return;
		
		// 获取玩家当前坐标
		double x = MC.player.getX();
		double y = MC.player.getY();
		double z = MC.player.getZ();
		
		// 播放爆炸音效（启用时）
		if(sound.isChecked())
		{
			// 随机化音效音调（模拟真实爆炸的音效变化）
			float soundPitch =
				(1F + (random.nextFloat() - random.nextFloat()) * 0.2F) * 0.7F;
			MC.world.playSound(x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE,
				SoundCategory.BLOCKS, 4, soundPitch, false);
		}
		
		// 生成爆炸粒子（启用时）
		if(particles.isChecked())
			MC.world.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0,
				0);
		
		// 获取按距离倒序排列的方块列表（远→近）
		ArrayList<BlockPos> blocks = getBlocksByDistanceReversed();
		// 按爆破强度循环破坏方块（通过数据包刷屏快速破坏）
		for(int i = 0; i < power.getValueI(); i++)
			BlockBreaker.breakBlocksWithPacketSpam(blocks);
		
		// 爆破完成后自动禁用功能
		setEnabled(false);
	}
	
	/**
	 * 获取玩家周围6格范围内的方块列表，按距离从远到近排序
	 * @return 按距离倒序排列的方块坐标列表
	 */
	private ArrayList<BlockPos> getBlocksByDistanceReversed()
	{
		// 获取玩家眼睛位置
		Vec3d eyesVec = RotationUtils.getEyesPos();
		// 玩家眼睛所在方块坐标
		BlockPos eyesBlock = BlockPos.ofFloored(eyesVec);
		// 搜索范围平方（6格范围，6*6=36）
		double rangeSq = 36;
		// 方块搜索范围（6格）
		int blockRange = 6;
		
		// 筛选并排序方块：
		// 1. 获取玩家周围6格内所有方块
		// 2. 过滤：距离≤6格的方块
		// 3. 排序：按距离从远到近（负数排序实现倒序）
		// 4. 转换为ArrayList返回
		return BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
			.sorted(Comparator
				.comparingDouble(pos -> -pos.getSquaredDistance(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
	}
}