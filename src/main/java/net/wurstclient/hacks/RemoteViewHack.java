/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.LivingEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.InteractFromSetting;
import net.wurstclient.settings.InteractFromSetting.InteractFrom;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filterlists.RemoteViewFilterList;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.FakePlayerEntity;

/**
 * Switches the camera to another entity without moving the local player.
 */
@SearchTags({"其它视角", "remote view"})
@DontSaveState // 禁用状态保存（重启客户端后默认关闭）
public final class RemoteViewHack extends Hack implements UpdateListener
{
	private final InteractFromSetting interactFrom = new InteractFromSetting(
		"description.wurst.setting.remoteview.interact_from",
		InteractFrom.CAMERA);
	
	private final CheckboxSetting hideHand = new CheckboxSetting("隐藏手部",
		"description.wurst.setting.remoteview.hide_hand", true);
	
	// 实体过滤列表：用于筛选可观察的目标实体（如排除玩家、生物类型限制等）
	private final EntityFilterList entityFilters =
		RemoteViewFilterList.create();
	
	// 当前观察的目标实体
	private Entity entity = null;
	
	public RemoteViewHack()
	{
		super("其它视角"); // 功能显示名称
		setCategory(Category.RENDER); // 归类到“渲染”类别
		addSetting(interactFrom);
		addSetting(hideHand);
		// 批量添加实体过滤列表中的所有设置项（如生物类型筛选、距离筛选等）
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		if(MC.player == null || MC.world == null)
		{
			entity = null;
			ChatUtils.error("当前没有可观察的实体。");
			setEnabled(false);
			return;
		}
		
		// 1. 未指定目标实体时，自动搜索符合条件的实体
		if(entity == null)
		{
			Stream<Entity> stream = StreamSupport
				.stream(MC.world.getEntities().spliterator(), false)
				.filter(RemoteViewHack::isViewableEntity)
				// 排除自身玩家
				.filter(
					e -> e != MC.player && !(e instanceof FakePlayerEntity));
			
			// 应用实体过滤规则（如仅观察怪物/动物等）
			stream = entityFilters.applyTo(stream);
			
			// 筛选距离自身最近的实体作为目标
			entity = stream
				.min(
					Comparator.comparingDouble(EntityUtils::distanceToHitboxSq))
				.orElse(null);
		}
		
		if(!isEntityValid())
		{
			entity = null;
			ChatUtils.error("未找到有效可观察的实体。");
			setEnabled(false);
			return;
		}
		
		WURST.getHax().freecamHack.setEnabled(false);
		WURST.getHax().lsdHack.setEnabled(false);
		MC.setCameraEntity(entity);
		ChatUtils.message("当前视角已切换至：" + entity.getName().getString() + "。");
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		if(entity == null)
			return;
		
		ChatUtils.message("已退出 " + entity.getName().getString() + " 的视角。");
		if(MC.getCameraEntity() == entity && MC.player != null)
			MC.setCameraEntity(MC.player);
		
		entity = null;
	}
	
	/**
	 * 通过指令切换视角（指定实体名称）
	 *
	 * @param viewName
	 *            要观察的实体名称
	 */
	public void onToggledByCommand(String name)
	{
		if(name == null || name.isEmpty())
		{
			setEnabled(!isEnabled());
			return;
		}
		
		if(MC.world == null || MC.player == null)
		{
			ChatUtils.error("当前没有可观察的实体。");
			return;
		}
		
		List<Entity> matches =
			StreamSupport.stream(MC.world.getEntities().spliterator(), false)
				.filter(RemoteViewHack::isViewableEntity)
				.filter(e -> e != MC.player && !(e instanceof FakePlayerEntity))
				.filter(e -> name.equalsIgnoreCase(e.getName().getString()))
				.sorted(
					Comparator.comparingDouble(EntityUtils::distanceToHitboxSq))
				.toList();
		
		if(matches.isEmpty())
		{
			ChatUtils.error("未找到名为 \"" + name + "\" 的实体。");
			return;
		}
		
		Entity nextEntity =
			matches.get((matches.indexOf(entity) + 1) % matches.size());
		if(isEnabled() && entity == nextEntity)
			return;
		
		if(isEnabled())
			setEnabled(false);
		
		entity = nextEntity;
		setEnabled(true);
	}
	
	@Override
	public void onUpdate()
	{
		if(!isEntityValid() || MC.getCameraEntity() != entity)
			setEnabled(false);
	}
	
	private boolean isEntityValid()
	{
		return entity != null && !entity.isRemoved() && MC.world != null
			&& MC.world.getEntityById(entity.getId()) == entity
			&& (!(entity instanceof LivingEntity living)
				|| living.getHealth() > 0);
	}
	
	private static boolean isViewableEntity(Entity entity)
	{
		return (entity instanceof LivingEntity
			|| entity instanceof InteractionEntity) && !entity.isRemoved()
			&& (!(entity instanceof LivingEntity living)
				|| living.getHealth() > 0);
	}
	
	public boolean isViewingEntity()
	{
		return isEnabled() && entity != null && MC.getCameraEntity() == entity;
	}
	
	public boolean isClickingFromPlayer()
	{
		return isViewingEntity()
			&& interactFrom.getSelected() == InteractFrom.PLAYER;
	}
	
	public boolean shouldHideHand()
	{
		return isViewingEntity() && hideHand.isChecked();
	}
}
