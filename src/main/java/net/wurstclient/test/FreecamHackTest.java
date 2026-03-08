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

import net.wurstclient.mixinterface.IKeyBinding;

public enum FreecamHackTest
{
	;
	
	public static void testFreecamHack()
	{
		System.out.println("Testing 自由视角 hack");
		
		// Enable Freecam with default settings
		runWurstCommand("setcheckbox 自由视角 tracer off");
		runWurstCommand("t 自由视角 on");
		takeScreenshot("freecam_default", Duration.ofMillis(100));
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
		runWurstCommand("setcheckbox 自由视角 tracer on");
		takeScreenshot("freecam_tracer", Duration.ofMillis(100));
		clearChat();
		
		// Clean up
		runWurstCommand("setcheckbox 自由视角 tracer off");
		runWurstCommand("t 自由视角 off");
		waitForWorldTicks(5);
		clearChat();
	}
}

