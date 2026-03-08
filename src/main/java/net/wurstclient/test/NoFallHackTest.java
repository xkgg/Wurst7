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
import java.util.function.Predicate;

import net.minecraft.client.option.Perspective;

public enum NoFallHackTest
{
	;
	
	public static void testNoFallHack()
	{
		System.out.println("Testing 无跌落伤害 hack");
		setPerspective(Perspective.THIRD_PERSON_BACK);
		runChatCommand("gamemode survival");
		assertOnGround();
		assertPlayerHealth(health -> health == 20);
		
		// Fall 10 blocks with NoFall enabled
		runWurstCommand("t 无跌落伤害 on");
		runChatCommand("tp ~ ~10 ~");
		waitForWorldTicks(5);
		waitUntil("player is on ground", mc -> mc.player.isOnGround());
		waitForWorldTicks(5);
		takeScreenshot("nofall_on_10_blocks", Duration.ZERO);
		assertPlayerHealth(health -> health == 20);
		
		// Fall 10 blocks with NoFall disabled
		runWurstCommand("t 无跌落伤害 off");
		runChatCommand("tp ~ ~10 ~");
		waitForWorldTicks(5);
		waitUntil("player is on ground", mc -> mc.player.isOnGround());
		waitForWorldTicks(5);
		takeScreenshot("nofall_off_10_blocks", Duration.ZERO);
		assertPlayerHealth(health -> Math.abs(health - 13) <= 1);
		
		// Clean up
		submitAndWait(mc -> mc.player.heal(20));
		runChatCommand("gamemode creative");
		setPerspective(Perspective.FIRST_PERSON);
	}
	
	private static void assertOnGround()
	{
		if(!submitAndGet(mc -> mc.player.isOnGround()))
			throw new RuntimeException("Player is not on ground");
	}
	
	private static void assertPlayerHealth(Predicate<Float> healthCheck)
	{
		float health = submitAndGet(mc -> mc.player.getHealth());
		if(!healthCheck.test(health))
			throw new RuntimeException("玩家的生命值有误: " + health);
		
		System.out.println("玩家的生命值是正确的: " + health);
	}
}


