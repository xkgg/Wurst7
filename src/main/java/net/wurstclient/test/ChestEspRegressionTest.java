/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;

import java.awt.Color;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.ChestEspHack;
import net.wurstclient.hacks.chestesp.ChestEspGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;

public enum ChestEspRegressionTest
{
	;
	
	private record Container(String block, String group, int x, int y)
	{}
	
	private static final List<Container> CONTAINERS =
		List.of(new Container("chest", "包含普通箱子", -5, 100),
			new Container("trapped_chest", "包含陷阱箱", -3, 100),
			new Container("ender_chest", "包含末影箱", -1, 100),
			new Container("barrel", "包含木桶", 1, 100),
			new Container("decorated_pot", "包含装饰陶罐", 3, 100),
			new Container("shulker_box", "包含潜影盒", 5, 100),
			new Container("hopper", "包含漏斗", -5, 102),
			new Container("dropper", "包含投掷器", -3, 102),
			new Container("dispenser", "包含发射器", -1, 102),
			new Container("furnace", "包含熔炉", 1, 102),
			new Container("smoker", "包含熔炉", 3, 102),
			new Container("blast_furnace", "包含熔炉", 5, 102));
	
	public static void testGroupsAndRendering()
	{
		var hack = WurstClient.INSTANCE.getHax().chestEspHack;
		List<Integer> vehicles = new ArrayList<>();
		boolean hudHidden = submitAndGet(mc -> mc.options.hudHidden);
		try
		{
			for(Container container : CONTAINERS)
				runChatCommand("setblock " + container.x + " " + container.y
					+ " 5 " + container.block);
			for(int i = 0; i < 4; i++)
			{
				final int index = i;
				vehicles.add(submitOnServer(server -> {
					var world = server.getOverworld();
					var entity = index < 2
						? (index == 0 ? EntityType.CHEST_MINECART.create(world)
							: EntityType.HOPPER_MINECART.create(world))
						: EntityType.CHEST_BOAT.create(world);
					if(index == 3)
						((BoatEntity)entity).setVariant(BoatEntity.Type.BAMBOO);
					entity.setPosition(-4 + index * 3, 100, 3);
					entity.setNoGravity(true);
					world.spawnEntity(entity);
					return entity.getId();
				}));
			}
			waitUntil("container vehicles tracked", mc -> vehicles.stream()
				.allMatch(id -> mc.world.getEntityById(id) != null));
			submitAndWait(mc -> {
				mc.options.hudHidden = true;
				hack.getSettings().values().stream()
					.filter(CheckboxSetting.class::isInstance)
					.map(CheckboxSetting.class::cast)
					.forEach(setting -> setting.setChecked(true));
			});
			waitForWorldTicks(5);
			Path off =
				takeScreenshotPath("chestesp_off", Duration.ofMillis(100));
			submitAndWait(mc -> hack.setEnabled(true));
			waitForWorldTicks(2);
			testGroups(hack, vehicles);
			testRendering(hack, off);
		}finally
		{
			submitAndWait(mc -> {
				hack.setEnabled(false);
				hack.getSettings().values()
					.forEach(setting -> setting.resetToDefault());
				mc.options.hudHidden = hudHidden;
			});
			submitOnServer(server -> {
				for(int id : vehicles)
				{
					var entity = server.getOverworld().getEntityById(id);
					if(entity != null)
						entity.discard();
				}
				return null;
			});
			runChatCommand("fill -6 100 3 6 104 5 air");
		}
	}
	
	private static void testGroups(ChestEspHack hack, List<Integer> vehicles)
	{
		System.out.println("Testing ChestESP container groups");
		submitAndWait(mc -> {
			hack.onUpdate();
			List<ChestEspGroup> groups = getGroups(hack);
			for(ChestEspGroup group : groups)
				if(group instanceof net.wurstclient.hacks.chestesp.ChestEspEntityGroup entityGroup)
					entityGroup.updateBoxes(1);
			for(Container container : CONTAINERS)
				assertOnlyGroup(groups, container.group,
					new Box(container.x + 0.1, container.y + 0.1, 5.1,
						container.x + 0.9, container.y + 0.9, 5.9));
			for(int i = 0; i < vehicles.size(); i++)
				assertOnlyGroup(groups,
					i == 0 ? "包含箱子矿车" : i == 1 ? "包含漏斗矿车" : "包含箱子船",
					mc.world.getEntityById(vehicles.get(i)).getBoundingBox());
		});
	}
	
	private static void assertOnlyGroup(List<ChestEspGroup> groups,
		String expected, Box box)
	{
		int matches = 0;
		for(ChestEspGroup group : groups)
			if(group.getBoxes().stream()
				.anyMatch(actual -> actual.intersects(box)))
			{
				matches++;
				assertTrue(
					group.getSettings().anyMatch(
						setting -> setting.getName().equals(expected)),
					"Container unexpectedly matched another ChestESP group instead of "
						+ expected);
			}
		assertTrue(matches == 1,
			"Expected one group for " + expected + ", got " + matches);
	}
	
	private static void testRendering(ChestEspHack hack, Path off)
	{
		System.out.println("Testing ChestESP rendering");
		for(String mode : new String[]{"仅方块", "仅线条", "线条和方块"})
		{
			submitAndWait(mc -> ((EnumSetting<?>)hack.getSettings().get("样式"))
				.setSelected(mode));
			waitForWorldTicks(2);
			Path on =
				takeScreenshotPath("chestesp_" + mode, Duration.ofMillis(100));
			assertTrue(imageDifference(off, on) > 0.05,
				"ChestESP " + mode + " did not render");
		}
		submitAndWait(mc -> hack.getSettings().values().stream()
			.filter(ColorSetting.class::isInstance)
			.map(ColorSetting.class::cast)
			.forEach(setting -> setting.setColor(Color.MAGENTA)));
		takeScreenshot("chestesp_custom_colors");
	}
	
	private static List<ChestEspGroup> getGroups(ChestEspHack hack)
	{
		List<ChestEspGroup> groups = new ArrayList<>();
		for(Field field : ChestEspHack.class.getDeclaredFields())
			if(ChestEspGroup.class.isAssignableFrom(field.getType()))
				try
				{
					field.setAccessible(true);
					groups.add((ChestEspGroup)field.get(hack));
				}catch(ReflectiveOperationException e)
				{
					throw new RuntimeException(e);
				}
		return groups;
	}
}
