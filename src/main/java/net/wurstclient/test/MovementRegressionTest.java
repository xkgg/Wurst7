/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;

import java.time.Duration;

import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.SliderSetting;

public enum MovementRegressionTest
{
	;
	
	public static void testAutoSprint()
	{
		runChatCommand("gamemode survival");
		waitUntil("survival mode",
			mc -> !mc.player.getAbilities().creativeMode);
		var hack = WurstClient.INSTANCE.getHax().autoSprintHack;
		runWurstCommand("setcheckbox " + hack.getName() + " 全方位疾跑 on");
		submitAndWait(mc -> hack.setEnabled(true));
		Vec3d start = submitAndGet(mc -> mc.player.getPos());
		try
		{
			submitAndWait(
				mc -> IKeyBinding.get(mc.options.leftKey).simulatePress(true));
			waitForWorldTicks(10);
			double moved = submitAndGet(
				mc -> mc.player.getPos().subtract(start).horizontalLength());
			assertTrue(moved >= 2,
				"Omnidirectional sprint moved only " + moved);
		}finally
		{
			submitAndWait(mc -> {
				IKeyBinding.get(mc.options.leftKey).simulatePress(false);
				hack.setEnabled(false);
				mc.player.setSprinting(false);
				mc.player.setVelocity(Vec3d.ZERO);
			});
			runWurstCommand("setcheckbox " + hack.getName() + " 全方位疾跑 off");
			runChatCommand("gamemode creative");
			runChatCommand("tp @s 0.5 100 0.5 0 0");
		}
	}
	
	public static void testBlink()
	{
		var hack = WurstClient.INSTANCE.getHax().blinkHack;
		submitAndWait(mc -> hack.setEnabled(true));
		waitForWorldTicks(2);
		submitAndWait(mc -> hack.setEnabled(false));
		assertTrue(!hack.isEnabled(), "Blink did not disable");
	}
	
	public static void testDamage()
	{
		runChatCommand("gamemode survival");
		waitUntil("survival mode",
			mc -> !mc.player.getAbilities().creativeMode);
		waitUntil("player on ground", mc -> mc.player.isOnGround());
		float health = submitAndGet(mc -> mc.player.getHealth());
		try
		{
			runWurstCommand("damage 7");
			waitUntil("damage command damages player",
				mc -> mc.player.getHealth() < health);
		}finally
		{
			runChatCommand("effect give @s minecraft:instant_health 1 4 true");
			runChatCommand("gamemode creative");
			waitUntil("player recovered",
				mc -> mc.player.getHealth() == 20 && mc.player.hurtTime == 0);
		}
	}
	
	public static void testAttributeSwap()
	{
		var hack = WurstClient.INSTANCE.getHax().attributeSwapHack;
		runChatCommand("gamemode survival");
		waitUntil("survival mode",
			mc -> !mc.player.getAbilities().creativeMode);
		runChatCommand("item replace entity @s hotbar.0 with diamond_sword");
		runChatCommand("item replace entity @s hotbar.1 with stick");
		submitAndWait(mc -> {
			mc.player.getInventory().selectedSlot = 0;
			mc.player.networkHandler
				.sendPacket(new UpdateSelectedSlotC2SPacket(0));
		});
		waitForWorldTicks(30);
		int golemId = submitOnServer(server -> {
			var world = server.getOverworld();
			var golem = EntityType.IRON_GOLEM.create(world);
			golem.setPosition(0.5, 100, 2.5);
			golem.setAiDisabled(true);
			world.spawnEntity(golem);
			return golem.getId();
		});
		waitUntil("golem tracked",
			mc -> mc.world.getEntityById(golemId) != null);
		var uuid = submitAndGet(mc -> mc.player.getUuid());
		try
		{
			// Keep the swap and attack in one server tick so network scheduling
			// cannot refresh the attributes between the two actions.
			float health = submitOnServer(server -> {
				var player = server.getPlayerManager().getPlayer(uuid);
				var golem = (net.minecraft.entity.LivingEntity)server
					.getOverworld().getEntityById(golemId);
				player.getInventory().selectedSlot = 1;
				try
				{
					player.attack(golem);
					assertTrue(golem.getHealth() <= 95,
						"Same-tick attribute swap did not use sword damage");
					assertTrue(
						player.getInventory().getStack(0).getDamage() == 0,
						"Attribute swap damaged the sword");
					return golem.getHealth();
				}finally
				{
					player.getInventory().selectedSlot = 0;
				}
			});
			waitForWorldTicks(30);
			submitAndWait(mc -> {
				hack.getSettings().values()
					.forEach(setting -> setting.resetToDefault());
				((SliderSetting)hack.getSettings().get("目标栏位")).setValue(2);
				hack.setEnabled(true);
				mc.interactionManager.attackEntity(mc.player,
					mc.world.getEntityById(golemId));
				assertTrue(mc.player.getInventory().selectedSlot == 1,
					"AttributeSwap did not select the configured attack slot");
			});
			waitUntil("AttributeSwap attack reached the server",
				mc -> ((net.minecraft.entity.LivingEntity)mc.world
					.getEntityById(golemId)).getHealth() < health);
			waitUntil("AttributeSwap restored the sword slot",
				mc -> mc.player.getInventory().selectedSlot == 0);
			assertTrue(
				submitAndGet(mc -> mc.player.getInventory().getStack(0)
					.isOf(Items.DIAMOND_SWORD)
					&& mc.player.getInventory().getStack(0).getDamage() == 0),
				"Attribute swap damaged the sword");
		}finally
		{
			submitAndWait(mc -> {
				hack.setEnabled(false);
				hack.getSettings().values()
					.forEach(setting -> setting.resetToDefault());
			});
			submitOnServer(server -> {
				var golem = server.getOverworld().getEntityById(golemId);
				if(golem != null)
					golem.discard();
				return null;
			});
			runChatCommand("clear");
			runChatCommand("gamemode creative");
			submitAndWait(mc -> mc.player.getInventory().selectedSlot = 0);
		}
	}
	
	public static void testOcclusion()
	{
		var hack = WurstClient.INSTANCE.getHax().freecamHack;
		submitAndWait(mc -> {
			ChunkOcclusionData data = new ChunkOcclusionData();
			assertTrue(!data.isVisibleThrough(Direction.NORTH, Direction.SOUTH),
				"Closed visibility graph unexpectedly open");
			hack.setEnabled(true);
			try
			{
				assertTrue(
					data.isVisibleThrough(Direction.NORTH, Direction.SOUTH),
					"Freecam did not bypass chunk occlusion");
			}finally
			{
				hack.setEnabled(false);
			}
			assertTrue(!data.isVisibleThrough(Direction.NORTH, Direction.SOUTH),
				"Chunk occlusion did not recover after Freecam");
		});
		waitForWorldTicks(5);
		takeScreenshot("freecam_occlusion_restored", Duration.ofMillis(100));
	}
}
