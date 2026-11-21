/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.google.gson.JsonPrimitive;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.event.Event;
import net.wurstclient.event.EventManager;
import net.wurstclient.event.Listener;
import net.wurstclient.events.LeftClickListener.LeftClickEvent;
import net.wurstclient.events.MouseUpdateListener.MouseUpdateEvent;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AimAtSetting;
import net.wurstclient.settings.AimAtSetting.AimAt;
import net.wurstclient.settings.AttackPrioritySetting;
import net.wurstclient.settings.AttackPrioritySetting.AttackPriority;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FovSliderSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.MouseAimController;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

public enum Upstream757Test
{
	;
	
	public static void testFov()
	{
		submitAndWait(mc -> {
			float yaw = mc.player.getYaw();
			float pitch = mc.player.getPitch();
			try
			{
				var fov = FovSliderSetting.genericCombat(120);
				mc.player.setYaw(0);
				mc.player.setPitch(0);
				Vec3d eyes = mc.player.getEyePos();
				for(float sign : new float[]{-1, 1})
				{
					assertTrue(
						fov.test(
							eyes.add(new Rotation(sign * 59, 0).toLookVec())),
						"FOV rejected a point inside its horizontal boundary");
					assertTrue(
						!fov.test(
							eyes.add(new Rotation(sign * 61, 0).toLookVec())),
						"FOV accepted a point outside its horizontal boundary");
					assertTrue(
						!fov.test(
							eyes.add(new Rotation(0, sign * 61).toLookVec())),
						"FOV ignored its vertical boundary");
				}
				mc.player.setPitch(80);
				fov.setValue(50);
				assertTrue(
					fov.test(eyes.add(new Rotation(180, 80).toLookVec())),
					"FOV used yaw/pitch distance instead of the viewing cone");
				fov.setValue(30);
				assertTrue(
					!fov.test(eyes.add(new Rotation(180, 80).toLookVec())),
					"FOV accepted a point outside the pitched viewing cone");
				fov.setValue(360);
				assertTrue(
					fov.test(eyes.add(new Rotation(180, -80).toLookVec())),
					"Full FOV rejected a point behind the player");
			}finally
			{
				mc.player.setYaw(yaw);
				mc.player.setPitch(pitch);
			}
		});
	}
	
	public static void testLegacySettings()
	{
		submitAndWait(mc -> {
			var hax = WurstClient.INSTANCE.getHax();
			Hack[] hacks = {hax.bowAimbotHack, hax.clickAuraHack,
				hax.killauraHack, hax.killauraLegitHack, hax.tpAuraHack};
			String[] keys = {"\u4f18\u5148\u7ea7", "\u4f18\u5148\u7ea7",
				"\u4f18\u5148\u6743", "\u653b\u51fb\u4f18\u5148\u7ea7",
				"\u4f18\u5148\u7ea7"};
			for(int i = 0; i < hacks.length; i++)
			{
				Hack hack = hacks[i];
				Object setting = hack.getSettings().get(keys[i]);
				assertTrue(setting instanceof AttackPrioritySetting,
					hack.getName() + " lost its legacy priority setting key");
				var priority = (AttackPrioritySetting)setting;
				AttackPriority previous = priority.getSelected();
				try
				{
					for(String health : new String[]{"\u751f\u547d\u503c",
						"\u8840\u91cf"})
					{
						priority.setSelected(AttackPriority.ANGLE);
						priority.fromJson(new JsonPrimitive(health));
						assertTrue(
							priority.getSelected() == AttackPriority.HEALTH,
							hack.getName()
								+ " did not load a legacy health priority");
					}
					priority.fromJson(new JsonPrimitive("\u8ddd\u79bb"));
					assertTrue(
						priority.getSelected() == AttackPriority.DISTANCE,
						"Legacy distance priority did not load");
					priority.fromJson(new JsonPrimitive("\u89d2\u5ea6"));
					assertTrue(priority.getSelected() == AttackPriority.ANGLE,
						"Legacy angle priority did not load");
					if(hack == hax.bowAimbotHack)
					{
						priority.fromJson(
							new JsonPrimitive("\u89d2\u5ea6+\u8ddd\u79bb"));
						assertTrue(
							priority.getSelected() == AttackPriority.ANGLE_DIST,
							"Legacy combined priority did not load");
					}else
					{
						priority.selectNext();
						assertTrue(
							priority.getSelected() == AttackPriority.HEALTH,
							"Reduced priority choices skipped health");
						priority.selectPrev();
						assertTrue(
							priority.getSelected() == AttackPriority.ANGLE,
							"Reduced priority choices could not leave health");
					}
				}finally
				{
					priority.setSelected(previous);
				}
			}
			Hack[] fovHacks = {hax.aimAssistHack, hax.clickAuraHack,
				hax.killauraHack, hax.killauraLegitHack, hax.multiAuraHack};
			String[] fovKeys = {"FOV", "\u89c6\u91ce",
				"\u89c6\u89d2\u8303\u56f4", "\u89c6\u91ce\u8303\u56f4(FOV)",
				"\u89c6\u89d2\u8303\u56f4"};
			for(int i = 0; i < fovHacks.length; i++)
				assertTrue(
					fovHacks[i].getSettings().get(
						fovKeys[i].toLowerCase()) instanceof FovSliderSetting,
					fovHacks[i].getName() + " lost its legacy FOV setting key");
		});
	}
	
	public static void testLineOfSightSelection()
	{
		submitAndWait(mc -> {
			var hax = WurstClient.INSTANCE.getHax();
			Hack[] hacks =
				{hax.aimAssistHack, hax.killauraHack, hax.killauraLegitHack};
			var hidden = EntityType.ZOMBIE.create(mc.world);
			var visible = EntityType.ZOMBIE.create(mc.world);
			hidden.setPosition(mc.player.getPos().add(0, 0, 3));
			visible.setPosition(mc.player.getPos().add(-3, 0, 2));
			mc.world.addEntity(hidden.getId(), hidden);
			mc.world.addEntity(visible.getId(), visible);
			BlockPos wall = mc.player.getBlockPos().add(0, 1, 2);
			var previousBlock = mc.world.getBlockState(wall);
			float yaw = mc.player.getYaw();
			float pitch = mc.player.getPitch();
			try
			{
				mc.player.setYaw(0);
				mc.player.setPitch(0);
				mc.world.setBlockState(wall, Blocks.STONE.getDefaultState());
				assertTrue(
					!BlockUtils
						.hasLineOfSight(hidden.getBoundingBox().getCenter())
						&& BlockUtils.hasLineOfSight(
							visible.getBoundingBox().getCenter()),
					"The line-of-sight fixture did not separate the targets");
				for(Hack hack : hacks)
				{
					var fov = (FovSliderSetting)readField(hack, "fov");
					double previousFov = fov.getValue();
					CheckboxSetting checkLOS = hack == hax.killauraLegitHack
						? null : (CheckboxSetting)readField(hack, "checkLOS");
					boolean previousLOS =
						checkLOS != null && checkLOS.isChecked();
					try
					{
						fov.setValue(360);
						hack.setEnabled(true);
						if(checkLOS != null)
						{
							checkLOS.setChecked(false);
							((UpdateListener)hack).onUpdate();
							assertTrue(readField(hack, "target") == hidden,
								hack.getName()
									+ " did not prefer the hidden target");
							checkLOS.setChecked(true);
						}
						((UpdateListener)hack).onUpdate();
						assertTrue(readField(hack, "target") == visible,
							hack.getName()
								+ " discarded the visible alternative");
					}finally
					{
						hack.setEnabled(false);
						fov.setValue(previousFov);
						if(checkLOS != null)
							checkLOS.setChecked(previousLOS);
					}
				}
			}finally
			{
				for(Hack hack : hacks)
					hack.setEnabled(false);
				hidden.discard();
				visible.discard();
				mc.world.setBlockState(wall, previousBlock);
				mc.player.setYaw(yaw);
				mc.player.setPitch(pitch);
			}
		});
	}
	
	public static void testAttackTiming()
	{
		submitAndWait(mc -> {
			var wurst = WurstClient.INSTANCE;
			var aura = wurst.getHax().killauraHack;
			var legit = wurst.getHax().killauraLegitHack;
			var cow = EntityType.COW.create(mc.world);
			cow.setPosition(mc.player.getPos().add(0, 0, 3));
			mc.world.addEntity(cow.getId(), cow);
			var auraSpeed = (AttackSpeedSliderSetting)readField(aura, "speed");
			var legitSpeed =
				(AttackSpeedSliderSetting)readField(legit, "speed");
			var auraRand = (SliderSetting)readField(aura, "speedRandMS");
			var legitRand = (SliderSetting)readField(legit, "speedRandMS");
			double[] previous = {auraSpeed.getValue(), legitSpeed.getValue(),
				auraRand.getValue(), legitRand.getValue()};
			int slot = mc.player.getInventory().selectedSlot;
			ItemStack previousStack = mc.player.getInventory().getStack(slot);
			AtomicInteger attacks = new AtomicInteger();
			PacketOutputListener listener = event -> {
				if(event.getPacket() instanceof PlayerInteractEntityC2SPacket)
				{
					attacks.incrementAndGet();
					event.cancel();
				}
			};
			try
			{
				wurst.getEventManager().add(PacketOutputListener.class,
					listener);
				auraSpeed.setValue(10);
				auraRand.setValue(0);
				aura.setEnabled(true);
				aura.onUpdate();
				assertTrue(
					!auraSpeed.isTimeToAttack()
						&& readField(aura, "target") == cow
						&& readField(aura, "renderTarget") == cow,
					"Killaura stopped selecting targets during cooldown");
				auraSpeed.updateTimer();
				aura.onPreMotion();
				aura.onHandleInput();
				assertTrue(attacks.get() == 1,
					"Killaura did not use the cooldown state at pre-motion");
				aura.onUpdate();
				aura.onPreMotion();
				aura.onHandleInput();
				assertTrue(
					attacks.get() == 1 && readField(aura, "target") == null
						&& readField(aura, "renderTarget") == cow,
					"Killaura attacked before its next cooldown elapsed");
				aura.setEnabled(false);
				clearRotation();
				legitSpeed.setValue(20);
				legitRand.setValue(0);
				legit.setEnabled(true);
				legit.onHandleInput();
				assertTrue(legitSpeed.isTimeToAttack(),
					"KillauraLegit stopped its timer without a target");
				legitSpeed.resetTimer();
				writeField(legit, "target", cow);
				mc.player.getInventory().setStack(slot,
					new ItemStack(Items.SHIELD));
				mc.player.setCurrentHand(Hand.MAIN_HAND);
				assertTrue(mc.player.isUsingItem(),
					"The timing fixture did not start using the shield");
				legit.onHandleInput();
				assertTrue(legitSpeed.isTimeToAttack() && attacks.get() == 1,
					"KillauraLegit stopped its timer or attacked while blocking");
			}finally
			{
				aura.setEnabled(false);
				legit.setEnabled(false);
				auraSpeed.setValue(previous[0]);
				legitSpeed.setValue(previous[1]);
				auraRand.setValue(previous[2]);
				legitRand.setValue(previous[3]);
				mc.player.stopUsingItem();
				mc.player.getInventory().setStack(slot, previousStack);
				wurst.getEventManager().remove(PacketOutputListener.class,
					listener);
				cow.discard();
				clearRotation();
			}
		});
	}
	
	public static void testAimAt()
	{
		submitAndWait(mc -> {
			var wurst = WurstClient.INSTANCE;
			var hax = wurst.getHax();
			var cow = EntityType.COW.create(mc.world);
			cow.setPosition(mc.player.getPos().add(0, 0, 3));
			mc.world.addEntity(cow.getId(), cow);
			int slot = mc.player.getInventory().selectedSlot;
			ItemStack previousStack = mc.player.getInventory().getStack(slot);
			var multiSpeed =
				(AttackSpeedSliderSetting)readField(hax.multiAuraHack, "speed");
			var auraSpeed =
				(AttackSpeedSliderSetting)readField(hax.killauraHack, "speed");
			var auraRand =
				(SliderSetting)readField(hax.killauraHack, "speedRandMS");
			double[] previous = {multiSpeed.getValue(), auraSpeed.getValue(),
				auraRand.getValue()};
			List<Rotation> looks = new ArrayList<>();
			List<Vec3d> hitPoints = new ArrayList<>();
			PlayerInteractEntityC2SPacket.Handler handler =
				new PlayerInteractEntityC2SPacket.Handler()
				{
					@Override
					public void interact(Hand hand)
					{}
					
					@Override
					public void interactAt(Hand hand, Vec3d pos)
					{
						hitPoints.add(pos);
					}
					
					@Override
					public void attack()
					{}
				};
			PacketOutputListener listener = event -> {
				if(event
					.getPacket() instanceof PlayerMoveC2SPacket.LookAndOnGround p)
				{
					looks.add(new Rotation(p.getYaw(0), p.getPitch(0)));
					event.cancel();
				}else if(event
					.getPacket() instanceof PlayerInteractEntityC2SPacket p)
				{
					p.handle(handler);
					event.cancel();
				}
			};
			try
			{
				wurst.getEventManager().add(PacketOutputListener.class,
					listener);
				multiSpeed.setValue(20);
				auraSpeed.setValue(20);
				auraRand.setValue(0);
				mc.player.getInventory().setStack(slot,
					new ItemStack(Items.WHEAT));
				checkAimAt(hax.feedAuraHack, cow, () -> {
					hax.feedAuraHack.onPreMotion();
					hax.feedAuraHack.onHandleInput();
				}, Upstream757Test::serverRotation);
				assertTrue(
					hitPoints.size() == 2
						&& hitPoints.get(0).y > hitPoints.get(1).y + 0.5,
					"FeedAura did not apply AimAt to its interaction hit point");
				checkAimAt(hax.clickAuraHack, cow, () -> {
					looks.clear();
					hax.clickAuraHack.onLeftClick(new LeftClickEvent());
				}, () -> lastLook(looks));
				checkAimAt(hax.multiAuraHack, cow, () -> {
					looks.clear();
					hax.multiAuraHack.onUpdate();
				}, () -> lastLook(looks));
				checkAimAt(hax.killauraHack, cow, () -> {
					hax.killauraHack.onUpdate();
					hax.killauraHack.onPreMotion();
				}, Upstream757Test::serverRotation);
			}finally
			{
				multiSpeed.setValue(previous[0]);
				auraSpeed.setValue(previous[1]);
				auraRand.setValue(previous[2]);
				mc.player.getInventory().setStack(slot, previousStack);
				wurst.getEventManager().remove(PacketOutputListener.class,
					listener);
				cow.discard();
				clearRotation();
			}
		});
	}
	
	public static void testMouseAim()
	{
		submitAndWait(mc -> {
			double sensitivity = mc.options.getMouseSensitivity().getValue();
			float yaw = mc.player.getYaw();
			float pitch = mc.player.getPitch();
			boolean wasLocked = mc.mouse.isCursorLocked();
			try
			{
				mc.mouse.lockCursor();
				var controller = new MouseAimController();
				Vec3d target = mc.player.getEyePos()
					.add(new Rotation(30, 20).toLookVec().multiply(3));
				for(double value : new double[]{0.5, 0.05})
				{
					mc.options.getMouseSensitivity().setValue(value);
					mc.player.setYaw(0);
					mc.player.setPitch(0);
					controller.reset();
					// Settle both tick endpoints so frame interpolation is
					// irrelevant.
					controller.tick(target, 1800);
					controller.tick(target, 1800);
					var event = new MouseUpdateEvent(0, 0);
					controller.applyTo(event, 0);
					double degrees = RotationUtils.getDegreesPerMouseUnit();
					assertTrue(
						Math.abs(event.getDeltaX() * degrees - 30) <= degrees
							&& Math.abs(
								event.getDeltaY() * degrees - 20) <= degrees,
						"Mouse aim changed its rotation speed with sensitivity");
				}
				for(double ignore : new double[]{0, 0.5, 1})
				{
					mc.player.setYaw(30);
					mc.player.setPitch(20);
					controller.reset();
					controller.tick(target, 1800);
					controller.tick(target, 1800);
					var event = new MouseUpdateEvent(8, -6);
					event.setDeltaX(100);
					event.setDeltaY(100);
					controller.applyTo(event, ignore);
					assertTrue(
						event.getDeltaX() == 8 * (1 - ignore)
							&& event.getDeltaY() == -6 * (1 - ignore),
						"Ignore mouse input did not scale manual input");
					double degrees = RotationUtils.getDegreesPerMouseUnit();
					mc.player.setYaw((float)(30 + event.getDeltaX() * degrees));
					mc.player
						.setPitch((float)(20 + event.getDeltaY() * degrees));
					var nextFrame = new MouseUpdateEvent(0, 0);
					controller.applyTo(nextFrame, ignore);
					assertTrue(
						nextFrame.getDeltaX() == 0
							&& nextFrame.getDeltaY() == 0,
						"Mouse aim undid manual input on the next frame");
				}
				controller.reset();
				var resetEvent = new MouseUpdateEvent(8, -6);
				controller.applyTo(resetEvent, 1);
				assertTrue(
					resetEvent.getDeltaX() == 8 && resetEvent.getDeltaY() == -6,
					"Reset mouse aim retained a stale target");
			}finally
			{
				mc.options.getMouseSensitivity().setValue(sensitivity);
				mc.player.setYaw(yaw);
				mc.player.setPitch(pitch);
				if(!wasLocked)
					mc.mouse.unlockCursor();
			}
		});
	}
	
	public static void testAutoFishExclusion()
	{
		submitAndWait(mc -> {
			var hax = WurstClient.INSTANCE.getHax();
			Hack[] aiming = {hax.aimAssistHack, hax.killauraLegitHack};
			try
			{
				for(Hack hack : aiming)
				{
					hack.setEnabled(true);
					hax.autoFishHack.setEnabled(true);
					assertTrue(
						!hack.isEnabled() && hax.autoFishHack.isEnabled(),
						"AutoFish left a mouse-aiming hack enabled");
					hack.setEnabled(true);
					assertTrue(
						hack.isEnabled() && !hax.autoFishHack.isEnabled(),
						"A mouse-aiming hack left AutoFish enabled");
					hack.setEnabled(false);
				}
			}finally
			{
				for(Hack hack : aiming)
					hack.setEnabled(false);
				hax.autoFishHack.setEnabled(false);
			}
		});
	}
	
	public static void testEventManager()
	{
		submitAndWait(mc -> {
			var events = WurstClient.INSTANCE.getEventManager();
			List<String> calls = new ArrayList<>();
			SnapshotListener removed = () -> calls.add("removed");
			SnapshotListener added = () -> calls.add("added");
			SnapshotListener mutating = () -> {
				calls.add("mutating");
				events.remove(SnapshotListener.class, removed);
				events.add(SnapshotListener.class, added);
			};
			try
			{
				events.add(SnapshotListener.class, mutating);
				events.add(SnapshotListener.class, mutating);
				events.add(SnapshotListener.class, removed);
				events.add(SnapshotListener.class, removed);
				EventManager.fire(new SnapshotEvent());
				assertTrue(calls.equals(List.of("mutating", "removed")),
					"Listener registration was repeated or changed the active dispatch: "
						+ calls);
				calls.clear();
				EventManager.fire(new SnapshotEvent());
				assertTrue(calls.equals(List.of("mutating", "added")),
					"Listener changes did not take effect on the next dispatch: "
						+ calls);
			}finally
			{
				events.remove(SnapshotListener.class, mutating);
				events.remove(SnapshotListener.class, removed);
				events.remove(SnapshotListener.class, added);
			}
		});
	}
	
	private interface SnapshotListener extends Listener
	{
		void onSnapshot();
	}
	
	private static final class SnapshotEvent extends Event<SnapshotListener>
	{
		@Override
		public void fire(ArrayList<SnapshotListener> listeners)
		{
			for(SnapshotListener listener : listeners)
				listener.onSnapshot();
		}
		
		@Override
		public Class<SnapshotListener> getListenerType()
		{
			return SnapshotListener.class;
		}
	}
	
	private static void checkAimAt(Hack hack, Entity target, Runnable aim,
		Supplier<Rotation> actual)
	{
		var setting = (AimAtSetting)readField(hack, "aimAt");
		AimAt previous = setting.getSelected();
		try
		{
			hack.setEnabled(true);
			for(AimAt position : new AimAt[]{AimAt.HEAD, AimAt.FEET})
			{
				setting.setSelected(position);
				aim.run();
				Rotation expected = RotationUtils
					.getNeededRotations(setting.getAimPoint(target));
				assertTrue(expected.getYawPitchDistanceTo(actual.get()) < 0.01,
					hack.getName() + " did not apply AimAt " + position);
			}
		}finally
		{
			hack.setEnabled(false);
			setting.setSelected(previous);
			clearRotation();
		}
	}
	
	private static Rotation serverRotation()
	{
		var faker = WurstClient.INSTANCE.getRotationFaker();
		return new Rotation(faker.getServerYaw(), faker.getServerPitch());
	}
	
	private static Rotation lastLook(List<Rotation> looks)
	{
		assertTrue(!looks.isEmpty(),
			"The combat hack did not send a look packet");
		return looks.get(looks.size() - 1);
	}
	
	private static void clearRotation()
	{
		var faker = WurstClient.INSTANCE.getRotationFaker();
		faker.applyIfNeeded();
		faker.restoreIfNeeded();
	}
	
	private static Object readField(Object object, String name)
	{
		try
		{
			Field field = object.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(object);
		}catch(ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}
	
	private static void writeField(Object object, String name, Object value)
	{
		try
		{
			Field field = object.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(object, value);
		}catch(ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}
}
