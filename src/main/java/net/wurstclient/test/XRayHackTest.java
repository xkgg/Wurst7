/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;

import java.time.Duration;

public enum XRayHackTest
{
	;
	
	public static void testXRayHack()
	{
		System.out.println("Testing 矿透 hack");
		buildTestRig();
		clearChat();
		
		// Enable X-Ray with default settings
		runWurstCommand("setcheckbox 矿透 只显示暴露的 off");
		runWurstCommand("setslider 矿透 透明度 0");
		runWurstCommand("t 矿透 on");
		takeScreenshot("xray_default", Duration.ofMillis(300));
		runWurstCommand("t 矿透 off");
		clearChat();
		
		// Exposed only
		runWurstCommand("setcheckbox 矿透 只显示暴露的 on");
		runWurstCommand("setslider 矿透 透明度 0");
		runWurstCommand("t 矿透 on");
		takeScreenshot("xray_exposed_only", Duration.ofMillis(300));
		runWurstCommand("t 矿透 off");
		clearChat();
		
		// Opacity mode
		runWurstCommand("setcheckbox 矿透 只显示暴露的 off");
		runWurstCommand("setslider 矿透 透明度 0.5");
		runWurstCommand("t 矿透 on");
		takeScreenshot("xray_opacity", Duration.ofMillis(300));
		runWurstCommand("t 矿透 off");
		clearChat();
		
		// Exposed only + opacity
		runWurstCommand("setcheckbox 矿透 只显示暴露的 on");
		runWurstCommand("setslider 矿透 透明度 0.5");
		runWurstCommand("t 矿透 on");
		takeScreenshot("xray_exposed_only_opacity", Duration.ofMillis(300));
		runWurstCommand("t 矿透 off");
		clearChat();
		
		// Clean up
		runChatCommand("fill ~-7 ~ ~-7 ~7 ~30 ~7 air");
		runWurstCommand("setcheckbox 矿透 只显示暴露的 off");
		runWurstCommand("setslider 矿透 透明度 0");
		runWurstCommand("t 矿透 off");
		clearChat();
	}
	
	private static void buildTestRig()
	{
		// Stone wall (9 wide, 5 high, 3 deep)
		runChatCommand("fill ~-5 ~ ~5 ~5 ~5 ~7 stone");
		
		// Ores (1 exposed and 1 hidden each)
		runChatCommand("fill ~-4 ~1 ~5 ~-4 ~1 ~6 minecraft:coal_ore");
		runChatCommand("fill ~-2 ~1 ~5 ~-2 ~1 ~6 minecraft:iron_ore");
		runChatCommand("fill ~0 ~1 ~5 ~0 ~1 ~6 minecraft:gold_ore");
		runChatCommand("fill ~2 ~1 ~5 ~2 ~1 ~6 minecraft:diamond_ore");
		runChatCommand("fill ~4 ~1 ~5 ~4 ~1 ~6 minecraft:emerald_ore");
		runChatCommand("fill ~-4 ~3 ~5 ~-4 ~3 ~6 minecraft:lapis_ore");
		runChatCommand("fill ~-2 ~3 ~5 ~-2 ~3 ~6 minecraft:redstone_ore");
		runChatCommand("fill ~0 ~3 ~5 ~0 ~3 ~6 minecraft:copper_ore");
		runChatCommand("fill ~2 ~3 ~5 ~2 ~3 ~6 minecraft:nether_gold_ore");
		runChatCommand("fill ~4 ~3 ~5 ~4 ~3 ~6 minecraft:nether_quartz_ore");
		
		// Fluids
		runChatCommand("setblock ~1 ~0 ~6 minecraft:water");
		runChatCommand("setblock ~-1 ~0 ~6 minecraft:lava");
	}
}


