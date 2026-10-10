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

import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IKeyBinding;

public enum FreecamHackTest
{
	;
	
	public static void testFreecamHack()
	{
		String hackName = WurstClient.INSTANCE.getHax().freecamHack.getName();
		System.out.println("Testing " + hackName + " hack");
		
		// Enable Freecam with default settings
		runWurstCommand("setcheckbox " + hackName + " 追踪线 off");
		runWurstCommand("t " + hackName + " on");
		takeScreenshot("freecam_default", Duration.ofMillis(100));
		clearChat();
		
		// Hide hand
		takeScreenshot("freecam_hidden_hand", Duration.ofMillis(100));
		runWurstCommand("setcheckbox " + hackName + " 隐藏手部 off");
		takeScreenshot("freecam_hand_visible", Duration.ofMillis(100));
		runWurstCommand("setcheckbox " + hackName + " 隐藏手部 on");
		clearChat();
		
		// Press shift to fly down a bit
		submitAndWait(
			mc -> IKeyBinding.get(mc.options.sneakKey).simulatePress(true));
		waitForWorldTicks(5);
		submitAndWait(
			mc -> IKeyBinding.get(mc.options.sneakKey).simulatePress(false));
		takeScreenshot("freecam_down", Duration.ofMillis(300));
		clearChat();
		
		// Tracer
		runWurstCommand("setcheckbox " + hackName + " 追踪线 on");
		takeScreenshot("freecam_tracer", Duration.ofMillis(100));
		clearChat();
		
		// Clean up
		runWurstCommand("setcheckbox " + hackName + " 追踪线 off");
		runWurstCommand("setcheckbox " + hackName + " 关闭时重载区块 off");
		runWurstCommand("t " + hackName + " off");
		waitForWorldTicks(5);
		clearChat();
	}
}
