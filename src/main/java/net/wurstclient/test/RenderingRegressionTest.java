/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;

import java.nio.file.Path;
import java.time.Duration;

import net.minecraft.util.Hand;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;

public enum RenderingRegressionTest
{
	;
	
	public static void testAntiWobble()
	{
		var hack = WurstClient.INSTANCE.getHax().antiWobbleHack;
		boolean hudHidden = submitAndGet(mc -> mc.options.hudHidden);
		submitAndWait(mc -> mc.options.hudHidden = true);
		try
		{
			Path reference = takeScreenshotPath("antiwobble_reference",
				Duration.ofMillis(100));
			runChatCommand("effect give @s minecraft:nausea 20 0 true");
			waitForWorldTicks(100);
			Path off =
				takeScreenshotPath("antiwobble_off", Duration.ofMillis(100));
			submitAndWait(mc -> hack.setEnabled(true));
			waitForWorldTicks(2);
			Path on =
				takeScreenshotPath("antiwobble_on", Duration.ofMillis(100));
			double offDifference = imageDifference(reference, off);
			double onDifference = imageDifference(reference, on);
			assertTrue(offDifference > 0.5,
				"Nausea did not distort the reference scene");
			assertTrue(onDifference < offDifference * 0.75,
				"AntiWobble did not remove nausea: off=" + offDifference
					+ ", on=" + onDifference);
		}finally
		{
			submitAndWait(mc -> {
				hack.setEnabled(false);
				mc.options.hudHidden = hudHidden;
			});
			runChatCommand("effect clear @s");
			waitForWorldTicks(10);
		}
	}
	
	public static void testLsd()
	{
		var hack = WurstClient.INSTANCE.getHax().lsdHack;
		boolean hudHidden = submitAndGet(mc -> mc.options.hudHidden);
		submitAndWait(mc -> mc.options.hudHidden = true);
		try
		{
			Path off = takeScreenshotPath("lsd_off", Duration.ofMillis(100));
			submitAndWait(mc -> hack.setEnabled(true));
			waitUntil("LSD shader active",
				mc -> mc.gameRenderer.getPostProcessor() != null);
			waitForWorldTicks(5);
			Path on = takeScreenshotPath("lsd_on", Duration.ofMillis(100));
			assertTrue(imageDifference(off, on) > 0.5,
				"LSD did not alter the scene");
		}finally
		{
			submitAndWait(mc -> {
				hack.setEnabled(false);
				mc.options.hudHidden = hudHidden;
			});
			assertTrue(
				submitAndGet(mc -> mc.gameRenderer.getPostProcessor() == null),
				"LSD shader remained enabled");
		}
	}
	
	public static void testNoShieldOverlay()
	{
		var hack = WurstClient.INSTANCE.getHax().noShieldOverlayHack;
		runChatCommand("item replace entity @s weapon.mainhand with shield");
		waitForWorldTicks(15);
		try
		{
			for(boolean blocking : new boolean[]{false, true})
			{
				if(blocking)
				{
					submitAndWait(mc -> mc.interactionManager
						.interactItem(mc.player, Hand.MAIN_HAND));
					waitUntil("shield blocking", mc -> mc.player.isUsingItem());
				}
				String name = blocking ? "blocking" : "idle";
				Path off = takeScreenshotPath(
					"noshieldoverlay_" + name + "_off", Duration.ofMillis(100));
				submitAndWait(mc -> hack.setEnabled(true));
				waitForWorldTicks(2);
				Path on = takeScreenshotPath("noshieldoverlay_" + name + "_on",
					Duration.ofMillis(100));
				assertTrue(imageDifference(off, on) > 0.1,
					"NoShieldOverlay did not move the " + name + " shield");
				submitAndWait(mc -> {
					hack.setEnabled(false);
					mc.player.clearActiveItem();
				});
				waitForWorldTicks(2);
			}
			runChatCommand("item replace entity @s weapon.mainhand with bread");
			waitForWorldTicks(15);
			Path off = takeScreenshotPath("noshieldoverlay_bread_off",
				Duration.ofMillis(100));
			submitAndWait(mc -> hack.setEnabled(true));
			Path on = takeScreenshotPath("noshieldoverlay_bread_on",
				Duration.ofMillis(100));
			assertTrue(imageDifference(off, on) < 0.5,
				"NoShieldOverlay altered a non-shield item");
		}finally
		{
			submitAndWait(mc -> {
				hack.setEnabled(false);
				mc.player.clearActiveItem();
			});
			runChatCommand("clear");
			waitForWorldTicks(10);
		}
	}
	
	public static void testNoWeather()
	{
		var hack = WurstClient.INSTANCE.getHax().noWeatherHack;
		runChatCommand("weather rain");
		submitAndWait(mc -> mc.world.setRainGradient(1));
		try
		{
			assertTrue(submitAndGet(mc -> mc.world.getRainGradient(1) > 0),
				"Rain setup failed");
			takeScreenshot("noweather_rain");
			submitAndWait(mc -> hack.setEnabled(true));
			assertTrue(submitAndGet(mc -> mc.world.getRainGradient(1) == 0),
				"NoWeather did not remove rain");
			takeScreenshot("noweather_disabled_rain");
			submitAndWait(mc -> {
				((CheckboxSetting)hack.getSettings().get("更改世界时间"))
					.setChecked(true);
				((SliderSetting)hack.getSettings().get("时间")).setValue(18000);
				assertTrue(
					Math.abs(mc.world.getSkyAngle(1)
						- mc.world.getDimension().getSkyAngle(18000)) < 1e-5,
					"NoWeather did not change sky time");
				((CheckboxSetting)hack.getSettings().get("更改月相"))
					.setChecked(true);
				((SliderSetting)hack.getSettings().get("月相")).setValue(4);
				assertTrue(mc.world.getMoonPhase() == 4,
					"NoWeather did not change moon phase");
			});
			takeScreenshot("noweather_midnight_moon_phase_4");
		}finally
		{
			submitAndWait(mc -> {
				hack.setEnabled(false);
				hack.getSettings().values()
					.forEach(setting -> setting.resetToDefault());
				mc.world.setRainGradient(0);
			});
			runChatCommand("weather clear");
			runChatCommand("time set noon");
		}
	}
}
