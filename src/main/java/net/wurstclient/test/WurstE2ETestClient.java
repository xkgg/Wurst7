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

import org.spongepowered.asm.mixin.MixinEnvironment;

import net.fabricmc.api.ModInitializer;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;

public final class WurstE2ETestClient implements ModInitializer
{
	@Override
	public void onInitialize()
	{
		if(System.getProperty("wurst.e2eTest") == null)
			return;
		
		Thread thread = new Thread(this::runTests, "Wurst End-to-End Test");
		thread.setUncaughtExceptionHandler((t, e) -> {
			e.printStackTrace();
			System.exit(1);
		});
		thread.start();
	}
	
	private void runTests()
	{
		System.out.println("Starting Wurst End-to-End Tests");
		waitForResourceLoading();
		
		if(submitAndGet(mc -> mc.options.onboardAccessibility))
		{
			System.out.println("Onboarding is enabled. Waiting for it");
			waitForScreen(AccessibilityOnboardingScreen.class);
			System.out.println("Reached onboarding screen");
			clickButton("gui.continue");
		}
		
		waitForScreen(TitleScreen.class);
		waitForTitleScreenFade();
		System.out.println("Reached title screen");
		takeScreenshot("title_screen", Duration.ZERO);
		
		submitAndWait(AltManagerTest::testAltManagerButton);
		// TODO: Test more of AltManager
		
		System.out.println("Clicking singleplayer button");
		clickButton("menu.singleplayer");
		
		if(submitAndGet(mc -> !mc.getLevelStorage().getLevelList().isEmpty()))
		{
			System.out.println("World list is not empty. Waiting for it");
			waitForScreen(SelectWorldScreen.class);
			System.out.println("Reached select world screen");
			takeScreenshot("select_world_screen");
			clickButton("selectWorld.create");
		}
		
		waitForScreen(CreateWorldScreen.class);
		System.out.println("Reached create world screen");
		
		// Set MC version as world name
		setTextFieldText(0,
			"E2E Test " + SharedConstants.getGameVersion().getName());
		// Select creative mode
		clickButton("selectWorld.gameMode");
		clickButton("selectWorld.gameMode");
		takeScreenshot("create_world_screen");
		
		System.out.println("Creating test world");
		clickButton("selectWorld.create");
		
		waitForWorldLoad();
		dismissTutorialToasts();
		waitForWorldTicks(200);
		runChatCommand("seed");
		System.out.println("Reached singleplayer world");
		takeScreenshot("in_game", Duration.ZERO);
		runChatCommand("gamerule doDaylightCycle false");
		runChatCommand("gamerule doWeatherCycle false");
		runChatCommand("gamerule doTraderSpawning false");
		runChatCommand("gamerule doPatrolSpawning false");
		runChatCommand("time set noon");
		clearChat();
		
		System.out.println("Opening debug menu");
		toggleDebugHud();
		takeScreenshot("debug_menu");
		
		System.out.println("Closing debug menu");
		toggleDebugHud();
		
		System.out.println("Checking for broken mixins");
		MixinEnvironment.getCurrentEnvironment().audit();
		
		System.out.println("Opening inventory");
		openInventory();
		takeScreenshot("inventory");
		
		System.out.println("Closing inventory");
		closeScreen();
		
		// TODO: Open ClickGUI and Navigator
		
		// Build a test platform and clear out the space above it
		runChatCommand("fill ~-7 ~-5 ~-7 ~7 ~-1 ~7 stone");
		runChatCommand("fill ~-7 ~ ~-7 ~7 ~30 ~7 air");
		runChatCommand("kill @e[type=!player,distance=..10]");
		
		// Clear inventory and chat before running tests
		runChatCommand("clear");
		clearChat();
		
		// Test Wurst hacks
		if(System.getProperty("wurst.e2eTestFilter", "").isEmpty())
		{
			AutoMineHackTest.testAutoMineHack();
			FreecamHackTest.testFreecamHack();
			NoFallHackTest.testNoFallHack();
			XRayHackTest.testXRayHack();
			
			CopyItemCmdTest.testCopyItemCmd();
			GiveCmdTest.testGiveCmd();
			ModifyCmdTest.testModifyCmd();
			PistonTest.testPistonDoesntCrash();
		}
		
		prepareRegressionScene();
		runScenario("FilterBabies", EntityFilterRegressionTest::testBabies);
		runScenario("FilterCalm", EntityFilterRegressionTest::testCalm);
		runScenario("FilterPassiveWater",
			EntityFilterRegressionTest::testPassiveWater);
		runScenario("FilterPets", EntityFilterRegressionTest::testPets);
		runScenario("MobDisposition",
			EntityFilterRegressionTest::testDisposition);
		runScenario("Translations", MenuAndTranslationTest::testTranslations);
		runScenario("AutoFarm", AutomationRegressionTest::testAutoFarm);
		runScenario("AutoFarmFacing",
			AutomationRegressionTest::testAutoFarmFacing);
		runScenario("AutoFarmBoneMeal",
			AutomationRegressionTest::testAutoFarmBoneMeal);
		runScenario("AutoSteal", AutomationRegressionTest::testAutoSteal);
		runScenario("AutoSprint", MovementRegressionTest::testAutoSprint);
		runScenario("Blink", MovementRegressionTest::testBlink);
		runScenario("Damage", MovementRegressionTest::testDamage);
		runScenario("AttributeSwap", MovementRegressionTest::testAttributeSwap);
		runScenario("FreecamOcclusion", MovementRegressionTest::testOcclusion);
		runScenario("AntiWobble", RenderingRegressionTest::testAntiWobble);
		runScenario("LSD", RenderingRegressionTest::testLsd);
		runScenario("NoShieldOverlay",
			RenderingRegressionTest::testNoShieldOverlay);
		runScenario("NoWeather", RenderingRegressionTest::testNoWeather);
		runScenario("ChestESP", ChestEspRegressionTest::testGroupsAndRendering);
		runScenario("InGameMenu", MenuAndTranslationTest::testMenus);
		
		System.out.println("Opening game menu");
		openGameMenu();
		takeScreenshot("game_menu");
		
		// TODO: Check Wurst Options
		
		System.out.println("Returning to title screen");
		clickButton("menu.returnToMenu");
		waitForScreen(TitleScreen.class);
		
		System.out.println("Stopping the game");
		clickButton("menu.quit");
	}
	
	private void prepareRegressionScene()
	{
		runChatCommand("gamemode creative");
		runChatCommand("gamerule doMobSpawning false");
		runChatCommand("gamerule keepInventory true");
		runChatCommand("weather clear");
		runChatCommand("time set noon");
		runChatCommand("forceload add -16 -16 16 16");
		submitOnServer(server -> {
			for(int x = -1; x <= 1; x++)
				for(int z = -1; z <= 1; z++)
					server.getOverworld().getChunk(x, z);
			return null;
		});
		runChatCommand("fill -16 99 -16 16 99 16 stone");
		runChatCommand("fill -8 100 -8 8 114 10 air");
		runChatCommand("fill -7 100 8 7 109 8 stone");
		runChatCommand("fill -6 101 8 -4 103 8 red_wool");
		runChatCommand("fill 4 101 8 6 103 8 blue_wool");
		runChatCommand("tp @s 0.5 100 0.5 0 0");
		waitUntil("regression scene loaded",
			mc -> Math.abs(mc.player.getY() - 100) < 0.01
				&& mc.player.isOnGround());
		submitAndWait(mc -> {
			mc.options.getBobView().setValue(false);
			mc.player.getInventory().selectedSlot = 0;
		});
		runChatCommand("clear");
		clearChat();
		waitForWorldTicks(10);
	}
	
	private void runScenario(String name, Runnable test)
	{
		String filter = System.getProperty("wurst.e2eTestFilter", "");
		if(!filter.isEmpty() && !filter.equals("*")
			&& !name.toLowerCase().contains(filter.toLowerCase()))
			return;
		
		System.out.println("Running regression scenario: " + name);
		try
		{
			test.run();
			clearChat();
			assertTrue(
				submitAndGet(mc -> net.wurstclient.WurstClient.INSTANCE.getHax()
					.getAllHax().stream().noneMatch(hack -> hack.isEnabled())),
				"Scenario " + name + " left a hack enabled");
		}catch(Throwable failure)
		{
			takeScreenshot(name + "_failure", Duration.ZERO);
			throw failure;
		}
	}
}
