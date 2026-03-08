/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filterlists.RemoteViewFilterList;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.FakePlayerEntity;

/**
 * 远程视角（RemoteView）：切换到指定实体的视角进行观察，自身进入无碰撞模式，
 * 同时在原位置生成假人伪装，隐藏被观察实体并屏蔽自身移动数据包，避免位置暴露。
 */
@SearchTags({"其它视角", "remote view"})
@DontSaveState // 禁用状态保存（重启客户端后默认关闭）
public final class RemoteViewHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	// 实体过滤列表：用于筛选可观察的目标实体（如排除玩家、生物类型限制等）
	private final EntityFilterList entityFilters =
		RemoteViewFilterList.create();
	
	// 当前观察的目标实体
	private Entity entity = null;
	// 记录目标实体原本的隐身状态（禁用时恢复）
	private boolean wasInvisible;
	
	// 生成的假人实体（用于伪装自身位置）
	private FakePlayerEntity fakePlayer;
	
	public RemoteViewHack()
	{
		super("其它视角"); // 功能显示名称
		setCategory(Category.RENDER); // 归类到“渲染”类别
		// 批量添加实体过滤列表中的所有设置项（如生物类型筛选、距离筛选等）
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		// 1. 未指定目标实体时，自动搜索符合条件的实体
		if(entity == null)
		{
			Stream<Entity> stream = StreamSupport
				.stream(MC.world.getEntities().spliterator(), true)
				// 仅保留有生命的实体（LivingEntity）
				.filter(LivingEntity.class::isInstance)
				// 过滤：未被移除 + 生命值>0（存活状态）
				.filter(e -> !e.isRemoved() && ((LivingEntity)e).getHealth() > 0)
				// 排除自身玩家
				.filter(e -> e != MC.player)
				// 排除假人实体
				.filter(e -> !(e instanceof FakePlayerEntity));
			
			// 应用实体过滤规则（如仅观察怪物/动物等）
			stream = entityFilters.applyTo(stream);
			
			// 筛选距离自身最近的实体作为目标
			entity = stream
				.min(Comparator.comparingDouble(e -> MC.player.squaredDistanceTo(e)))
				.orElse(null);
			
			// 未找到有效实体时，提示并禁用功能
			if(entity == null)
			{
				ChatUtils.error("未找到有效可观察的实体。");
				setEnabled(false);
				return;
			}
		}
		
		// 2. 保存目标实体原有数据（隐身状态）
		wasInvisible = entity.isInvisible();
		
		// 3. 开启自身无碰撞模式（穿墙，避免视角被阻挡）
		MC.player.noClip = true;
		
		// 4. 在原位置生成假人（伪装自身存在）
		fakePlayer = new FakePlayerEntity();
		
		// 5. 发送成功提示
		ChatUtils.message("当前视角已切换至：" + entity.getName().getString() + "。");
		
		// 6. 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 1. 移除事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		
		// 2. 重置目标实体状态
		if(entity != null)
		{
			ChatUtils.message("已退出 " + entity.getName().getString() + " 的视角。");
			// 恢复目标实体原本的隐身状态
			entity.setInvisible(wasInvisible);
			entity = null;
		}
		
		// 3. 关闭自身无碰撞模式
		MC.player.noClip = false;
		
		// 4. 移除假人实体
		if(fakePlayer != null)
		{
			fakePlayer.resetPlayerPosition(); // 重置假人位置
			fakePlayer.despawn(); // 移除假人
		}
	}
	
	/**
	 * 通过指令切换视角（指定实体名称）
	 * @param viewName 要观察的实体名称
	 */
	public void onToggledByCommand(String viewName)
	{
		// 1. 未启用时，根据名称筛选目标实体
		if(!isEnabled() && viewName != null && !viewName.isEmpty())
		{
			entity = StreamSupport
				.stream(MC.world.getEntities().spliterator(), false)
				.filter(LivingEntity.class::isInstance)
				.filter(e -> !e.isRemoved() && ((LivingEntity)e).getHealth() > 0)
				.filter(e -> e != MC.player)
				.filter(e -> !(e instanceof FakePlayerEntity))
				// 匹配实体名称（忽略大小写）
				.filter(e -> viewName.equalsIgnoreCase(e.getName().getString()))
				// 筛选距离最近的匹配实体
				.min(Comparator.comparingDouble(e -> MC.player.squaredDistanceTo(e)))
				.orElse(null);
			
			// 未找到指定实体时提示
			if(entity == null)
			{
				ChatUtils.error("未找到名为 \"" + viewName + "\" 的实体。");
				return;
			}
		}
		
		// 2. 切换远程视角功能开关
		setEnabled(!isEnabled());
	}
	
	@Override
	public void onUpdate()
	{
		// 1. 验证目标实体有效性：已被移除/生命值≤0 → 禁用功能
		if(entity.isRemoved() || ((LivingEntity)entity).getHealth() <= 0)
		{
			setEnabled(false);
			return;
		}
		
		// 2. 同步视角到目标实体：复制位置、旋转角度等
		MC.player.copyPositionAndRotation(entity);
		// 修正视角高度（匹配实体的眼睛高度）
		MC.player.setPos(entity.getX(),
			entity.getY() - MC.player.getEyeHeight(MC.player.getPose())
				+ entity.getEyeHeight(entity.getPose()),
			entity.getZ());
		MC.player.resetPosition(); // 重置位置同步
		MC.player.setVelocity(Vec3d.ZERO); // 清空自身速度（避免移动）
		
		// 3. 隐藏目标实体（避免视角中看到自身）
		entity.setInvisible(true);
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		// 屏蔽移动数据包发送（避免服务器检测到自身位置异常）
		if(event.getPacket() instanceof PlayerMoveC2SPacket)
			event.cancel();
	}
}