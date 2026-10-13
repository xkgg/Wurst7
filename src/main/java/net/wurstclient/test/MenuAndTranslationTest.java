/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.wurstclient.WurstClient;
import net.wurstclient.options.WurstOptionsScreen;

public enum MenuAndTranslationTest
{
	;
	
	public static void testMenus()
	{
		var options = WurstClient.INSTANCE.getOtfs().wurstOptionsOtf;
		openGameMenu();
		takeScreenshot("game_menu");
		clickButton("            Options");
		waitForScreen(WurstOptionsScreen.class);
		takeScreenshot("wurst_options");
		submitAndWait(mc -> mc.currentScreen.close());
		waitForScreen(GameMenuScreen.class);
		closeScreen();
		
		runWurstCommand("setmode " + options.getName() + " 显示位置 统计信息");
		try
		{
			openGameMenu();
			assertTrue(submitAndGet(
				mc -> mc.currentScreen.drawables.stream().noneMatch(
					drawable -> drawable instanceof net.minecraft.client.gui.widget.ButtonWidget b
						&& b.visible
						&& b.getMessage().getString()
							.equals("            Options"))),
				"Alternative Wurst Options location still in game menu");
			clickButton("gui.stats");
			waitForScreen(StatsScreen.class);
			clickButton("Wurst选项");
			waitForScreen(WurstOptionsScreen.class);
			takeScreenshot("wurst_options_from_statistics");
		}finally
		{
			closeScreen();
			runWurstCommand("setmode " + options.getName() + " 显示位置 暂停菜单");
		}
		
		var background = WurstClient.INSTANCE.getHax().noBackgroundHack;
		submitAndWait(mc -> {
			background.allGuis.setChecked(true);
			background.setEnabled(true);
			assertTrue(
				background.shouldCancelBackground(new GameMenuScreen(true)),
				"NoBackground does not cover the game menu");
			assertTrue(
				background.shouldCancelBackground(
					new OptionsScreen(null, mc.options)),
				"NoBackground does not cover options");
			background.setEnabled(false);
			assertTrue(
				!background.shouldCancelBackground(new GameMenuScreen(true)),
				"Disabled NoBackground still cancels backgrounds");
			background.allGuis.resetToDefault();
		});
	}
	
	public static void testTranslations()
	{
		submitAndWait(mc -> {
			var translator = WurstClient.INSTANCE.getTranslator();
			for(String key : new String[]{
				"description.wurst.setting.aimassist.fov",
				"description.wurst.setting.anchoraura.range",
				"description.wurst.setting.antiafk.wait_time",
				"description.wurst.setting.follow.filter_players",
				"description.wurst.setting.mobspawnesp.hitbox_check.slow",
				"description.wurst.setting.remoteview.filter_players",
				"description.wurst.setting.generic.filter_golems_combat",
				"gui.wurst.altmanager.add.title"})
			{
				String value = translator.translate(key);
				assertTrue(
					!value.equals(key) && value.codePoints()
						.anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF),
					"Chinese translation missing for " + key + ": " + value);
			}
			assertTrue(
				!translator
					.translate(
						"description.wurst.setting.anchoraura.face_anchors")
					.equals(
						"description.wurst.setting.anchoraura.face_anchors"),
				"English fallback missing");
		});
	}
}
