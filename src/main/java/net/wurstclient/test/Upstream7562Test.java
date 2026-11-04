/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;

public enum Upstream7562Test
{
	;
	
	public static void testNameProtectDisconnect()
	{
		submitAndWait(mc -> {
			var hack = WurstClient.INSTANCE.getHax().nameProtectHack;
			var world = mc.world;
			hack.setEnabled(true);
			try
			{
				mc.world = null;
				assertTrue(hack.protect("#").equals("#"),
					"NameProtect changed text while disconnecting");
				String username = mc.getSession().getUsername();
				assertTrue(hack.protect(username).equals(username),
					"NameProtect used a stale player while disconnecting");
			}finally
			{
				mc.world = world;
				hack.setEnabled(false);
			}
		});
	}
	
	public static void testPacketSneak()
	{
		submitAndWait(mc -> {
			var hack = WurstClient.INSTANCE.getHax().sneakHack;
			var mode = (EnumSetting<?>)hack.getSettings().get("\u6a21\u5f0f");
			String previousMode = mode.getSelected().toString();
			boolean previousSneaking = mc.player.input.sneaking;
			boolean previousKey = mc.options.sneakKey.isPressed();
			var events = WurstClient.INSTANCE.getEventManager();
			List<Mode> packets = new ArrayList<>();
			PacketOutputListener listener = event -> {
				if(event.getPacket() instanceof ClientCommandC2SPacket packet
					&& (packet.getMode() == Mode.PRESS_SHIFT_KEY
						|| packet.getMode() == Mode.RELEASE_SHIFT_KEY))
				{
					packets.add(packet.getMode());
					event.cancel();
				}
			};
			events.add(PacketOutputListener.class, listener);
			try
			{
				mode.setSelected("\u6570\u636e\u5305");
				mc.player.input.sneaking = false;
				hack.setEnabled(true);
				hack.onPreMotion();
				hack.onPostMotion();
				assertTrue(
					packets.equals(
						List.of(Mode.PRESS_SHIFT_KEY, Mode.RELEASE_SHIFT_KEY,
							Mode.RELEASE_SHIFT_KEY, Mode.PRESS_SHIFT_KEY)),
					"Packet Sneak changed the 1.20.1 command sequence");
				
				packets.clear();
				mode.setSelected("\u5408\u6cd5");
				hack.onPostMotion();
				hack.onPostMotion();
				assertTrue(packets.equals(List.of(Mode.RELEASE_SHIFT_KEY)),
					"Changing Sneak mode did not release packet sneaking once");
				
				mode.setSelected("\u6570\u636e\u5305");
				hack.onPostMotion();
				packets.clear();
				mc.player.input.sneaking = true;
				mode.setSelected("\u5408\u6cd5");
				hack.onPostMotion();
				assertTrue(packets.equals(List.of(Mode.PRESS_SHIFT_KEY)),
					"Changing Sneak mode released the player's actual sneak");
				
				mode.setSelected("\u6570\u636e\u5305");
				mc.player.input.sneaking = false;
				hack.onPostMotion();
				packets.clear();
				assertTrue(mc.player
					.startRiding(EntityType.BOAT.create(mc.world), true),
					"Could not mount the test boat");
				hack.onPreMotion();
				hack.onPostMotion();
				hack.onPostMotion();
				assertTrue(packets.equals(List.of(Mode.RELEASE_SHIFT_KEY)),
					"Packet Sneak kept sending sneak commands while riding");
				
				mc.player.stopRiding();
				hack.onPostMotion();
				packets.clear();
				hack.setEnabled(false);
				assertTrue(packets.equals(List.of(Mode.RELEASE_SHIFT_KEY)),
					"Disabling Sneak left packet sneaking active");
				
				hack.setEnabled(true);
				hack.onPostMotion();
				packets.clear();
				mc.player.input.sneaking = true;
				hack.setEnabled(false);
				assertTrue(packets.equals(List.of(Mode.PRESS_SHIFT_KEY)),
					"Disabling Sneak released the player's actual sneak");
			}finally
			{
				mc.player.stopRiding();
				hack.setEnabled(false);
				events.remove(PacketOutputListener.class, listener);
				mode.setSelected(previousMode);
				mc.player.input.sneaking = previousSneaking;
				mc.options.sneakKey.setPressed(previousKey);
			}
		});
	}
	
	public static void testFreecamPanorama()
	{
		submitAndWait(mc -> {
			var hack = WurstClient.INSTANCE.getHax().freecamHack;
			var reload = (CheckboxSetting)hack.getSettings()
				.get("\u5173\u95ed\u65f6\u91cd\u8f7d\u533a\u5757");
			boolean previousReload = reload.isChecked();
			boolean previousPanorama = mc.gameRenderer.isRenderingPanorama();
			float yaw = mc.player.getYaw();
			float pitch = mc.player.getPitch();
			var camera = mc.gameRenderer.getCamera();
			try
			{
				reload.setChecked(false);
				mc.gameRenderer.setRenderingPanorama(false);
				mc.player.setYaw(20);
				mc.player.setPitch(10);
				hack.setEnabled(true);
				mc.player.setYaw(80);
				mc.player.setPitch(30);
				camera.update(mc.world, mc.player, false, false, 1);
				assertTrue(
					Math.abs(camera.getYaw() - 20) < 0.001
						&& Math.abs(camera.getPitch() - 10) < 0.001,
					"Freecam lost its normal camera rotation");
				
				mc.gameRenderer.setRenderingPanorama(true);
				camera.update(mc.world, mc.player, false, false, 1);
				assertTrue(
					Math.abs(camera.getYaw() - 80) < 0.001
						&& Math.abs(camera.getPitch() - 30) < 0.001,
					"Freecam overrode the panorama face rotation");
				assertTrue(
					camera.getPos().distanceTo(hack.getCamPos(1)) < 0.001,
					"Panorama no longer uses the Freecam position");
			}finally
			{
				hack.setEnabled(false);
				reload.setChecked(previousReload);
				mc.gameRenderer.setRenderingPanorama(previousPanorama);
				mc.player.setYaw(yaw);
				mc.player.setPitch(pitch);
				camera.update(mc.world, mc.player,
					!mc.options.getPerspective().isFirstPerson(),
					mc.options.getPerspective().isFrontView(), 1);
			}
		});
	}
}
