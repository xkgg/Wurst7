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
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.wurstclient.RotationFaker;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.PreMotionListener.PreMotionEvent;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.Rotation;

public enum CombatBackportTest
{
	;
	
	public static void testRotationFaker()
	{
		submitAndWait(mc -> {
			var faker = new RotationFaker();
			float yaw = mc.player.getYaw();
			float pitch = mc.player.getPitch();
			try
			{
				mc.player.setYaw(17);
				mc.player.setPitch(-13);
				faker.faceVectorPacket(mc.player.getEyePos().add(4, 2, -3));
				float serverYaw = faker.getServerYaw();
				float serverPitch = faker.getServerPitch();
				faker.restoreIfNeeded();
				assertTrue(
					mc.player.getYaw() == 17 && mc.player.getPitch() == -13,
					"A pending rotation changed the player's camera");
				faker.applyIfNeeded();
				assertTrue(
					mc.player.getYaw() == serverYaw
						&& mc.player.getPitch() == serverPitch,
					"The requested server rotation was not applied");
				faker.restoreIfNeeded();
				assertTrue(
					mc.player.getYaw() == 17 && mc.player.getPitch() == -13,
					"The real camera rotation was not restored");
				mc.player.setYaw(42);
				mc.player.setPitch(-7);
				faker.restoreIfNeeded();
				assertTrue(
					faker.getServerYaw() == 42 && faker.getServerPitch() == -7,
					"A completed rotation retained stale camera angles");
			}finally
			{
				faker.restoreIfNeeded();
				mc.player.setYaw(yaw);
				mc.player.setPitch(pitch);
			}
		});
	}
	
	public static void testMovingTargets()
	{
		submitAndWait(mc -> {
			var wurst = WurstClient.INSTANCE;
			var hax = wurst.getHax();
			Hack[] hacks =
				{hax.aimAssistHack, hax.killauraLegitHack, hax.killauraHack};
			var target = EntityType.COW.create(mc.world);
			float yaw = mc.player.getYaw();
			float pitch = mc.player.getPitch();
			var speed = (AttackSpeedSliderSetting)hax.killauraHack.getSettings()
				.get("速度");
			double previousSpeed = speed.getValue();
			try
			{
				speed.setValue(20);
				for(Hack hack : hacks)
				{
					hack.setEnabled(true);
					speed.resetTimer();
					speed.updateTimer();
					mc.player.setYaw(0);
					mc.player.setPitch(0);
					target.setPosition(mc.player.getPos().add(0, 0, 3));
					writeField(hack, "target", target);
					target.setPosition(mc.player.getPos().add(-3, 0, 0));
					EventManager.fire(PreMotionEvent.INSTANCE);
					float nextYaw = hack == hax.killauraHack
						? wurst.getRotationFaker().getServerYaw()
						: ((Rotation)readField(readField(hack, "mouseAim"),
							"nextTickRot")).yaw();
					assertTrue(nextYaw > 10, hack.getName()
						+ " did not aim at the target's new position");
					hack.setEnabled(false);
					wurst.getRotationFaker().applyIfNeeded();
					wurst.getRotationFaker().restoreIfNeeded();
				}
			}finally
			{
				for(Hack hack : hacks)
					hack.setEnabled(false);
				speed.setValue(previousSpeed);
				wurst.getRotationFaker().applyIfNeeded();
				wurst.getRotationFaker().restoreIfNeeded();
				mc.player.setYaw(yaw);
				mc.player.setPitch(pitch);
			}
		});
	}
	
	public static void testFeedAura()
	{
		submitAndWait(mc -> {
			var wurst = WurstClient.INSTANCE;
			var hack = wurst.getHax().feedAuraHack;
			var filter = (CheckboxSetting)hack.getSettings().get("过滤婴儿");
			boolean wasChecked = filter.isChecked();
			int slot = mc.player.getInventory().selectedSlot;
			ItemStack previous = mc.player.getInventory().getStack(slot);
			var cow = EntityType.COW.create(mc.world);
			cow.setBaby(true);
			cow.setPosition(mc.player.getPos().add(0, 0, 3));
			mc.world.addEntity(cow.getId(), cow);
			try
			{
				mc.player.getInventory().setStack(slot,
					new ItemStack(Items.WHEAT));
				hack.setEnabled(true);
				filter.setChecked(true);
				EventManager.fire(PreMotionEvent.INSTANCE);
				assertTrue(readField(hack, "clickTarget") == null,
					"FeedAura selected a filtered baby");
				filter.setChecked(false);
				EventManager.fire(PreMotionEvent.INSTANCE);
				assertTrue(readField(hack, "clickTarget") == cow,
					"FeedAura still filtered babies with the filter disabled");
				cow.setPosition(mc.player.getPos().add(3, 0, 0));
				EventManager.fire(PreMotionEvent.INSTANCE);
				assertTrue(wurst.getRotationFaker().getServerYaw() < -20,
					"FeedAura did not aim at the animal's new position");
				mc.player.getInventory().setStack(slot, ItemStack.EMPTY);
				EventManager.fire(PreMotionEvent.INSTANCE);
				assertTrue(
					readField(hack, "clickTarget") == null
						&& readField(hack, "renderTarget") == null,
					"FeedAura retained a target after the food was removed");
			}finally
			{
				hack.setEnabled(false);
				filter.setChecked(wasChecked);
				cow.discard();
				mc.player.getInventory().setStack(slot, previous);
				wurst.getRotationFaker().applyIfNeeded();
				wurst.getRotationFaker().restoreIfNeeded();
			}
		});
	}
	
	public static void testTpAuraCooldown()
	{
		submitAndWait(mc -> {
			var wurst = WurstClient.INSTANCE;
			var hack = wurst.getHax().tpAuraHack;
			var speed = (AttackSpeedSliderSetting)hack.getSettings().get("速度");
			double previousSpeed = speed.getValue();
			var position = mc.player.getPos();
			var cow = EntityType.COW.create(mc.world);
			cow.setPosition(position.add(0, 0, 2));
			mc.world.addEntity(cow.getId(), cow);
			AtomicInteger attacks = new AtomicInteger();
			PacketOutputListener listener = event -> {
				if(event.getPacket() instanceof PlayerInteractEntityC2SPacket)
				{
					attacks.incrementAndGet();
					event.cancel();
				}else if(event.getPacket() instanceof PlayerMoveC2SPacket)
					event.cancel();
			};
			try
			{
				wurst.getEventManager().add(PacketOutputListener.class,
					listener);
				speed.setValue(20);
				hack.setEnabled(true);
				mc.player.resetLastAttackedTicks();
				assertTrue(mc.player.getAttackCooldownProgress(0) < 1,
					"TpAura test did not start during attack cooldown");
				hack.onUpdate();
				assertTrue(attacks.get() == 1,
					"Custom TpAura speed was blocked by vanilla cooldown");
				mc.player.setPosition(position);
				attacks.set(0);
				speed.setValue(0);
				speed.resetTimer();
				mc.player.resetLastAttackedTicks();
				hack.onUpdate();
				assertTrue(attacks.get() == 0,
					"Automatic TpAura speed ignored vanilla cooldown");
			}finally
			{
				hack.setEnabled(false);
				speed.setValue(previousSpeed);
				wurst.getEventManager().remove(PacketOutputListener.class,
					listener);
				cow.discard();
				mc.player.setPosition(position);
			}
		});
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
