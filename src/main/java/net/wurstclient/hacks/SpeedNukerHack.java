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
import java.util.stream.Stream;

import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.nukers.CommonNukerSettings;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RotationUtils;

/**
 * 快速拆除（SpeedNuker）：高速批量破坏指定范围内的方块，通过数据包轰炸实现无动画快速挖掘，
 * 是Nuker系列中速度最快的版本，优先级高于普通Nuker、连锁挖矿等功能。
 */
@SearchTags({"快速拆除","speed nuker", "FastNuker", "fast nuker"})
@DontSaveState // 禁用状态保存（重启客户端后默认关闭）
public final class SpeedNukerHack extends Hack implements UpdateListener
{
    // 挖掘半径：控制方块破坏的最大范围（1~6格，精度0.05）
    private final SliderSetting range =
        new SliderSetting("挖掘半径", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
    
    // Nuker通用设置：包含方块黑白名单、形状（立方体/球形）、模式（ID/非ID）等核心配置
    private final CommonNukerSettings commonSettings =
        new CommonNukerSettings();
    
    // 挥动手臂设置：控制挖掘时是否显示手臂摆动动画（默认关闭，避免频繁动画）
    private final SwingHandSetting swingHand = new SwingHandSetting(
        SwingHandSetting.genericMiningDescription(this), SwingHand.OFF);
    
    public SpeedNukerHack()
    {
        super("快速拆除"); // 功能显示名称
        setCategory(Category.BLOCKS); // 归类到“方块操作”类别
        addSetting(range); // 添加“挖掘半径”设置项
        // 批量添加CommonNukerSettings中的所有设置项（黑白名单、形状等）
        commonSettings.getSettings().forEach(this::addSetting);
        addSetting(swingHand); // 添加“挥动手臂”设置项
    }
    
    @Override
    public String getRenderName()
    {
        // 界面显示名称：功能名 + 通用设置后缀（如[仅钻石 | 球形]）
        return getName() + commonSettings.getRenderNameSuffix();
    }
    
    @Override
    protected void onEnable()
    {
        // 启用时关闭冲突的挖掘类功能，避免逻辑冲突
        WURST.getHax().autoMineHack.setEnabled(false); // 自动挖矿
        WURST.getHax().excavatorHack.setEnabled(false); // 挖掘机
        WURST.getHax().nukerHack.setEnabled(false); // 普通拆除
        WURST.getHax().nukerLegitHack.setEnabled(false); // 合法拆除
        WURST.getHax().tunnellerHack.setEnabled(false); // 隧道挖掘
        WURST.getHax().veinMinerHack.setEnabled(false); // 连锁挖矿
        
        // 注册事件监听器：左键点击（绑定通用设置）、帧更新（核心逻辑）
        EVENTS.add(LeftClickListener.class, commonSettings);
        EVENTS.add(UpdateListener.class, this);
    }
    
    @Override
    protected void onDisable()
    {
        // 禁用时移除事件监听器，停止功能执行
        EVENTS.remove(LeftClickListener.class, commonSettings);
        EVENTS.remove(UpdateListener.class, this);
        
        // 重置通用设置到初始状态
        commonSettings.reset();
    }
    
    @Override
    public void onUpdate()
    {
        // 空模式（仅空气）：无有效方块可破坏，直接返回
        if(commonSettings.isIdModeWithAir())
            return;
        
        // 1. 获取玩家视角核心参数
        Vec3d eyesVec = RotationUtils.getEyesPos(); // 玩家眼睛的精确坐标（浮点数）
        BlockPos eyesBlock = BlockPos.ofFloored(eyesVec); // 眼睛所在的方块坐标（整数）
        double rangeSq = range.getValueSq(); // 挖掘半径的平方（用于距离判断，优化性能）
        int blockRange = range.getValueCeil(); // 挖掘半径向上取整（整数格数，用于立方体范围计算）
        
        // 2. 筛选符合条件的待破坏方块
        Stream<BlockPos> stream =
            // 2.1 获取以眼睛方块为中心、指定半径内的所有方块（立方体范围）
            BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
            // 2.2 过滤：仅保留可交互的方块（非空气/液体/基岩等）
            .filter(BlockUtils::canBeClicked)
            // 2.3 过滤：仅保留符合通用设置规则的方块（黑白名单、模式等）
            .filter(commonSettings::shouldBreakBlock);
        
        // 2.4 球形模式补充过滤：仅保留与玩家眼睛距离≤挖掘半径的方块
        if(commonSettings.isSphereShape())
            stream = stream
                .filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq);
        
        // 3. 排序并转为列表：按与玩家距离由近到远排序（优先破坏近处方块）
        ArrayList<BlockPos> blocks = stream
            .sorted(Comparator.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
            .collect(Collectors.toCollection(ArrayList::new));
        
        // 无符合条件的方块：直接返回，不执行挖掘
        if(blocks.isEmpty())
            return;
        
        // 4. 执行核心挖掘逻辑
        WURST.getHax().autoToolHack.equipIfEnabled(blocks.get(0)); // 自动切换最优挖掘工具
        BlockBreaker.breakBlocksWithPacketSpam(blocks); // 数据包轰炸：批量破坏方块（无原生挖掘动画）
        swingHand.swing(Hand.MAIN_HAND); // 执行挥动手臂动画（按设置项控制）
    }
}