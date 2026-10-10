/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ConnectionPacketOutputListener;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.settings.CheckboxSetting;

@DontBlock
@SearchTags({"原版伪装", "vanilla spoof", "AntiFabric", "anti fabric",
	"LibHatesMods", "HackedServer"})
public final class VanillaSpoofOtf extends OtherFeature
	implements ConnectionPacketOutputListener
{
	private final CheckboxSetting spoof = new CheckboxSetting("伪装原版", false);
	
	public VanillaSpoofOtf()
	{
		super("原版伪装", "通过假装成原版客户端来绕过反Fabric插件。" + "\n\n请在连接服务器前启用，修改设置后需要重新连接。"
			+ "\n\n需要模组的服务器可能无法使用此功能。");
		addSetting(spoof);
		
		EVENTS.add(ConnectionPacketOutputListener.class, this);
	}
	
	@Override
	public void onSentConnectionPacket(ConnectionPacketOutputEvent event)
	{
		if(!spoof.isChecked())
			return;
		
		if(event.getPacket() instanceof CustomPayloadC2SPacket packet)
		{
			if(packet.getChannel().getNamespace().equals("minecraft")
				&& packet.getChannel().getPath().equals("brand"))
				event.setPacket(
					new CustomPayloadC2SPacket(CustomPayloadC2SPacket.BRAND,
						new PacketByteBuf(Unpooled.buffer())
							.writeString("vanilla")));
			else
				event.cancel();
			
		}else if(event
			.getPacket() instanceof LoginQueryResponseC2SPacket packet)
			event.setPacket(
				new LoginQueryResponseC2SPacket(packet.getQueryId(), null));
	}
	
	@Override
	public boolean isEnabled()
	{
		return spoof.isChecked();
	}
	
	@Override
	public String getPrimaryAction()
	{
		return isEnabled() ? "禁用" : "启用";
	}
	
	@Override
	public void doPrimaryAction()
	{
		spoof.setChecked(!spoof.isChecked());
	}
}
