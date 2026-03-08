/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

/**
 * 名字保护（NameProtect）：自动替换游戏内显示的玩家名称，
 * 自身名称替换为“Me”（斜体），其他玩家名称替换为“Player+数字”（斜体），
 * 防止在截图/录屏时泄露真实玩家名称。
 */
@SearchTags({"名字保护", "name protect"})
public final class NameProtectHack extends Hack
{
	public NameProtectHack()
	{
		super("名字保护"); // 功能显示名称
		setCategory(Category.RENDER); // 归类到“渲染”类别
	}
	
	/**
	 * 处理文本中的玩家名称，替换为保护后的别名
	 * @param string 原始文本（如聊天消息、实体名称标签等）
	 * @return 替换后的保护文本
	 */
	public String protect(String string)
	{
		// 功能未启用或玩家未加载 → 返回原始文本
		if(!isEnabled() || MC.player == null)
			return string;
		
		// 1. 替换自身名称：替换为斜体的“Me”（§o=斜体，§r=重置格式）
		String me = MC.getSession().getUsername();
		if(string.contains(me))
			return string.replace(me, "\u00a7oMe\u00a7r");
		
		int i = 0;
		// 2. 替换玩家列表中的玩家名称：替换为斜体的“Player+数字”
		for(PlayerListEntry info : MC.player.networkHandler.getPlayerList())
		{
			i++;
			// 移除名称中的格式符（如颜色码），获取纯文本名称
			String name =
				info.getProfile().getName().replaceAll("\u00a7(?:\\w|\\d)", "");
			
			if(string.contains(name))
				return string.replace(name, "\u00a7oPlayer" + i + "\u00a7r");
		}
		
		// 3. 替换世界内的玩家实体名称：替换为斜体的“Player+数字”
		for(AbstractClientPlayerEntity player : MC.world.getPlayers())
		{
			i++;
			String name = player.getName().getString();
			
			if(string.contains(name))
				return string.replace(name, "\u00a7oPlayer" + i + "\u00a7r");
		}
		
		// 无玩家名称需要替换 → 返回原始文本
		return string;
	}
}