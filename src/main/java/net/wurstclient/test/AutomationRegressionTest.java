/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;

import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.autofarm.AutoFarmPlantTypeManager;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.InventoryUtils;

public enum AutomationRegressionTest
{
	;
	
	public static void testAutoFarm()
	{
		runChatCommand("setblock 1 100 2 stone");
		runChatCommand("setblock 1 101 2 twisting_vines");
		runChatCommand("setblock -1 103 2 stone");
		runChatCommand("setblock -1 102 2 weeping_vines");
		var types = new AutoFarmPlantTypeManager();
		var twisting = submitAndGet(
			mc -> types.getReplantingSpotType(new BlockPos(1, 101, 2)));
		var weeping = submitAndGet(
			mc -> types.getReplantingSpotType(new BlockPos(-1, 102, 2)));
		assertTrue(twisting != null && weeping != null,
			"Vine replanting spots not recognized");
		runChatCommand("setblock 1 101 2 air");
		runChatCommand("setblock -1 102 2 air");
		submitAndWait(mc -> {
			assertTrue(twisting.hasPlantingSurface(new BlockPos(1, 101, 2)),
				"Twisting vine cannot replant into air");
			assertTrue(weeping.hasPlantingSurface(new BlockPos(-1, 102, 2)),
				"Weeping vine cannot replant into air");
			assertTrue(InventoryUtils.giveCreativeItem(Items.WHEAT_SEEDS),
				"Creative supply did not give seeds");
		});
		runChatCommand("clear");
		runChatCommand("gamemode survival");
		waitUntil("survival mode",
			mc -> !mc.player.getAbilities().creativeMode);
		assertTrue(
			!submitAndGet(
				mc -> InventoryUtils.giveCreativeItem(Items.WHEAT_SEEDS)),
			"Creative supply ran in survival mode");
		runChatCommand("gamemode creative");
		runChatCommand("fill -1 100 2 1 103 2 air");
		
		var hack = WurstClient.INSTANCE.getHax().autoFarmHack;
		runChatCommand("setblock 0 99 2 farmland");
		runChatCommand("setblock 0 100 2 wheat[age=7]");
		runWurstCommand("setmode " + hack.getName() + " 朝向 关闭");
		runWurstCommand("setmode " + hack.getName() + " 摆动手臂 关闭");
		submitAndWait(mc -> {
			mc.player.handSwinging = false;
			hack.setEnabled(true);
		});
		try
		{
			waitUntil("AutoFarm creative harvest and replant",
				mc -> mc.world.getBlockState(new BlockPos(0, 100, 2))
					.isOf(Blocks.WHEAT)
					&& mc.world.getBlockState(new BlockPos(0, 100, 2))
						.get(net.minecraft.block.CropBlock.AGE) < 7);
			assertTrue(!submitAndGet(mc -> mc.player.handSwinging),
				"AutoFarm ignored disabled hand swing");
		}finally
		{
			submitAndWait(mc -> {
				hack.setEnabled(false);
				hack.getSettings().get("朝向").resetToDefault();
				hack.getSettings().get("摆动手臂").resetToDefault();
			});
			runChatCommand("setblock 0 100 2 air");
			runChatCommand("setblock 0 99 2 stone");
			runChatCommand("clear");
		}
	}
	
	public static void testAutoSteal()
	{
		var hack = WurstClient.INSTANCE.getHax().autoStealHack;
		runChatCommand("setblock 0 100 2 chest");
		runChatCommand("item replace block 0 100 2 container.0 with stone");
		runChatCommand("item replace block 0 100 2 container.26 with diamond");
		submitAndWait(mc -> {
			((CheckboxSetting)hack.getSettings().get("反向偷取顺序"))
				.setChecked(true);
			mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
				new BlockHitResult(new Vec3d(0.5, 100.5, 2), Direction.NORTH,
					new BlockPos(0, 100, 2), false));
		});
		try
		{
			waitForScreen(GenericContainerScreen.class);
			submitAndWait(
				mc -> hack.steal((GenericContainerScreen)mc.currentScreen, 3));
			waitUntil("reverse stealing takes both items",
				mc -> InventoryUtils.count(Items.DIAMOND) == 1
					&& InventoryUtils.count(Items.STONE) == 1);
			assertTrue(submitAndGet(
				mc -> mc.player.getInventory().getStack(8).isOf(Items.DIAMOND)
					&& mc.player.getInventory().getStack(7).isOf(Items.STONE)),
				"Reverse stealing did not start with the last chest slot");
		}finally
		{
			closeScreen();
			submitAndWait(
				mc -> hack.getSettings().get("反向偷取顺序").resetToDefault());
			runChatCommand("setblock 0 100 2 air");
			runChatCommand("kill @e[type=item,distance=..10]");
			runChatCommand("clear");
		}
	}
	
	public static void testAutoFarmFacing()
	{
		var hack = WurstClient.INSTANCE.getHax().autoFarmHack;
		runChatCommand("gamemode survival");
		runChatCommand("tp @s 0.5 100 0.5 0 0");
		runChatCommand("setblock 2 99 2 farmland");
		runChatCommand("setblock 2 100 2 wheat[age=7]");
		runWurstCommand("setmode " + hack.getName() + " 朝向 客户端");
		runWurstCommand("setmode " + hack.getName() + " 摆动手臂 客户端");
		try
		{
			submitAndWait(mc -> {
				((CheckboxSetting)hack.getSettings().get("自动补种"))
					.setChecked(false);
				mc.player.handSwinging = false;
				hack.setEnabled(true);
				hack.onUpdate();
				assertTrue(
					Math.abs(mc.player.getYaw()) > 10
						|| Math.abs(mc.player.getPitch()) > 10,
					"AutoFarm ignored client facing in survival");
				assertTrue(mc.player.handSwinging,
					"AutoFarm ignored client hand swing in survival");
			});
			waitUntil("AutoFarm survival crop harvested",
				mc -> mc.world.getBlockState(new BlockPos(2, 100, 2)).isAir());
		}finally
		{
			resetAutoFarm();
			runChatCommand("setblock 2 100 2 air");
			runChatCommand("setblock 2 99 2 stone");
			runChatCommand("gamemode creative");
			runChatCommand("tp @s 0.5 100 0.5 0 0");
			runChatCommand("kill @e[type=item,distance=..10]");
			runChatCommand("clear");
		}
	}
	
	public static void testAutoFarmBoneMeal()
	{
		var hack = WurstClient.INSTANCE.getHax().autoFarmHack;
		runChatCommand("gamemode survival");
		runChatCommand("setblock 0 99 2 dirt");
		runChatCommand("setblock 0 100 2 sweet_berry_bush[age=2]");
		runChatCommand("item replace entity @s hotbar.0 with bone_meal 16");
		runChatCommand("item replace entity @s inventory.0 with stick");
		submitAndWait(mc -> {
			mc.player.getInventory().selectedSlot = 0;
		});
		// Fill the hotbar on the server so the safe item needs an inventory
		// swap.
		submitOnServer(server -> {
			var player = server.getPlayerManager().getPlayerList().get(0);
			for(int slot = 1; slot < 9; slot++)
				player.getInventory().setStack(slot,
					new net.minecraft.item.ItemStack(Items.BONE_MEAL, 16));
			player.currentScreenHandler.sendContentUpdates();
			return null;
		});
		waitUntil("bone meal hotbar synchronized",
			mc -> mc.player.getInventory().getStack(8).isOf(Items.BONE_MEAL));
		int boneMeal =
			submitAndGet(mc -> InventoryUtils.count(Items.BONE_MEAL));
		runWurstCommand("setmode " + hack.getName() + " 朝向 关闭");
		runWurstCommand("setmode " + hack.getName() + " 摆动手臂 关闭");
		try
		{
			submitAndWait(mc -> hack.setEnabled(true));
			waitUntil("AutoFarm harvests berries while holding bone meal",
				mc -> mc.world.getBlockState(new BlockPos(0, 100, 2))
					.get(net.minecraft.block.SweetBerryBushBlock.AGE) == 1);
			assertTrue(
				submitAndGet(
					mc -> InventoryUtils.count(Items.BONE_MEAL)) == boneMeal,
				"AutoFarm consumed bone meal instead of harvesting");
		}finally
		{
			resetAutoFarm();
			runChatCommand("setblock 0 100 2 air");
			runChatCommand("setblock 0 99 2 stone");
			runChatCommand("gamemode creative");
			runChatCommand("kill @e[type=item,distance=..10]");
			runChatCommand("clear");
		}
	}
	
	private static void resetAutoFarm()
	{
		var hack = WurstClient.INSTANCE.getHax().autoFarmHack;
		submitAndWait(mc -> {
			hack.setEnabled(false);
			for(String name : new String[]{"朝向", "摆动手臂", "自动补种"})
				hack.getSettings().get(name).resetToDefault();
		});
	}
}
