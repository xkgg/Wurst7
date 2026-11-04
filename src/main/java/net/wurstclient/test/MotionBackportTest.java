/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.Input;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.events.KnockbackListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;

public enum MotionBackportTest
{
	;
	
	public static void testMotionEvents()
	{
		submitAndWait(mc -> {
			var player = mc.player;
			assertTrue(!player.hasVehicle(),
				"Motion test requires an unmounted player");
			var events = WurstClient.INSTANCE.getEventManager();
			var rotations = WurstClient.INSTANCE.getRotationFaker();
			var position = player.getPos();
			var velocity = player.getVelocity();
			var input = player.input;
			float yaw = player.getYaw();
			float pitch = player.getPitch();
			float lastYaw = player.lastYaw;
			float lastPitch = player.lastPitch;
			boolean noGravity = player.hasNoGravity();
			List<String> order = new ArrayList<>();
			List<PlayerMoveC2SPacket> packets = new ArrayList<>();
			boolean[] fakeRotation = {true};
			float[] expectedRotation = new float[2];
			PreMotionListener preMotion = () -> {
				order.add("pre");
				if(fakeRotation[0])
				{
					rotations.faceVectorPacket(player.getEyePos().add(4, 1, 0));
					expectedRotation[0] = rotations.getServerYaw();
					expectedRotation[1] = rotations.getServerPitch();
				}
			};
			PostMotionListener postMotion = () -> {
				order.add("post");
				assertTrue(player.getYaw() == 19 && player.getPitch() == 11,
					"PostMotion observed a fake client rotation");
			};
			PacketOutputListener output = event -> {
				if(event.getPacket() instanceof PlayerMoveC2SPacket packet)
				{
					order.add("packet");
					packets.add(packet);
				}
				event.cancel();
			};
			var boat = EntityType.BOAT.create(mc.world);
			try
			{
				player.input = new Input();
				player.setNoGravity(true);
				player.setVelocity(Vec3d.ZERO);
				player.setYaw(19);
				player.setPitch(11);
				player.lastYaw = 199;
				events.add(PreMotionListener.class, preMotion);
				events.add(PostMotionListener.class, postMotion);
				events.add(PacketOutputListener.class, output);
				player.tick();
				assertTrue(order.equals(List.of("pre", "packet", "post")),
					"Walking motion events were missing, repeated or unordered: "
						+ order);
				var packet = packets.get(0);
				assertTrue(
					packet.getYaw(0) == expectedRotation[0]
						&& packet.getPitch(0) == expectedRotation[1],
					"A PreMotion rotation request missed the movement packet");
				
				fakeRotation[0] = false;
				order.clear();
				packets.clear();
				boat.setPosition(position);
				assertTrue(player.startRiding(boat, true),
					"Could not mount the motion test boat");
				player.tickRiding();
				assertTrue(order.equals(List.of("pre", "packet", "post")),
					"Riding motion events were missing, repeated or unordered: "
						+ order);
				assertTrue(
					packets.get(0).getYaw(0) == 19
						&& packets.get(0).getPitch(0) == 11,
					"Riding reused a previous fake rotation");
			}finally
			{
				rotations.restoreIfNeeded();
				events.remove(PreMotionListener.class, preMotion);
				events.remove(PostMotionListener.class, postMotion);
				events.remove(PacketOutputListener.class, output);
				player.stopRiding();
				boat.discard();
				player.input = input;
				player.setNoGravity(noGravity);
				player.setPosition(position);
				player.setVelocity(velocity);
				player.setYaw(yaw);
				player.setPitch(pitch);
				player.lastYaw = lastYaw;
				player.lastPitch = lastPitch;
			}
		});
	}
	
	public static void testHighJumpAccuracy()
	{
		submitAndWait(mc -> {
			var hack = WurstClient.INSTANCE.getHax().highJumpHack;
			var height =
				(SliderSetting)hack.getSettings().values().iterator().next();
			double previousHeight = height.getValue();
			boolean enabled = hack.isEnabled();
			var velocity = mc.player.getVelocity();
			boolean sprinting = mc.player.isSprinting();
			var cow = new CowEntity(EntityType.COW, mc.world)
			{
				@Override
				public void jump()
				{
					super.jump();
				}
			};
			try
			{
				mc.player.setSprinting(false);
				hack.setEnabled(false);
				cow.jump();
				double normalCowJump = cow.getVelocity().y;
				hack.setEnabled(true);
				for(int target : new int[]{1, 6, 20, 100})
				{
					height.setValue(target);
					mc.player.jump();
					double actualHeight = 0;
					for(double motion =
						mc.player.getVelocity().y; motion >= 0.003; motion =
							(motion - 0.08) * 0.98F)
						actualHeight += motion;
					assertTrue(
						actualHeight >= target && actualHeight - target < 0.001,
						"HighJump missed " + target + " blocks: "
							+ actualHeight);
					cow.jump();
					assertTrue(cow.getVelocity().y == normalCowJump,
						"HighJump changed a mob's jump");
				}
			}finally
			{
				height.setValue(previousHeight);
				hack.setEnabled(enabled);
				mc.player.setVelocity(velocity);
				mc.player.setSprinting(sprinting);
			}
		});
	}
	
	public static void testMixinCompatibility()
	{
		submitAndWait(mc -> {
			var hax = WurstClient.INSTANCE.getHax();
			var events = WurstClient.INSTANCE.getEventManager();
			var step = hax.stepHack;
			var mode = (EnumSetting<?>)step.getSettings().values().stream()
				.filter(EnumSetting.class::isInstance).findFirst()
				.orElseThrow();
			var height = (SliderSetting)step.getSettings().values().stream()
				.filter(SliderSetting.class::isInstance).findFirst()
				.orElseThrow();
			String previousMode = mode.getSelected().toString();
			double previousHeight = height.getValue();
			boolean enabled = step.isEnabled();
			float stepHeight = mc.player.stepHeight;
			var velocity = mc.player.getVelocity();
			var cow = EntityType.COW.create(mc.world);
			KnockbackListener knockback = event -> {
				event.setX(event.getX() * 2);
				event.setY(event.getY() * 0.5);
				event.setZ(0);
			};
			try
			{
				mode.setSelected(mode.getValues()[0].toString());
				height.setValue(3);
				mc.player.stepHeight = 0.875F;
				step.setEnabled(true);
				assertTrue(mc.player.getStepHeight() == 3,
					"Simple Step did not adjust the local step height");
				assertTrue(mc.player.stepHeight == 0.875F,
					"Step overwrote another mod's base step height");
				assertTrue(cow.getStepHeight() != 3,
					"Step changed a mob's step height");
				step.setEnabled(false);
				assertTrue(mc.player.getStepHeight() == 0.875F,
					"Disabling Step did not preserve the base step height");
				
				events.add(KnockbackListener.class, knockback);
				mc.player.setVelocityClient(1, 2, 3);
				cow.setVelocityClient(1, 2, 3);
				assertTrue(mc.player.getVelocity().equals(new Vec3d(2, 1, 0)),
					"The local knockback hook did not modify velocity");
				assertTrue(cow.getVelocity().equals(new Vec3d(1, 2, 3)),
					"The local knockback hook changed a mob's velocity");
			}finally
			{
				events.remove(KnockbackListener.class, knockback);
				step.setEnabled(false);
				mode.setSelected(previousMode);
				height.setValue(previousHeight);
				mc.player.stepHeight = stepHeight;
				mc.player.setVelocity(velocity);
				step.setEnabled(enabled);
			}
		});
		testContainerButtons();
	}
	
	private static void testContainerButtons()
	{
		submitAndWait(mc -> {
			var hack = WurstClient.INSTANCE.getHax().autoStealHack;
			var buttons = (CheckboxSetting)hack.getSettings().values().stream()
				.filter(CheckboxSetting.class::isInstance).findFirst()
				.orElseThrow();
			boolean visible = buttons.isChecked();
			boolean enabled = hack.isEnabled();
			try
			{
				hack.setEnabled(false);
				buttons.setChecked(true);
				var chest = new GenericContainerScreen(
					GenericContainerScreenHandler.createGeneric9x3(0,
						mc.player.getInventory()),
					mc.player.getInventory(), Text.empty());
				var shulker = new ShulkerBoxScreen(
					new ShulkerBoxScreenHandler(0, mc.player.getInventory()),
					mc.player.getInventory(), Text.empty());
				for(var screen : List.of(chest, shulker))
				{
					mc.setScreen(screen);
					assertTrue(screen.children().stream()
						.filter(ButtonWidget.class::isInstance).count() == 2,
						"AutoSteal did not add its two container buttons");
					screen.resize(mc, screen.width, screen.height);
					assertTrue(screen.children().stream()
						.filter(ButtonWidget.class::isInstance).count() == 2,
						"AutoSteal duplicated its buttons after resizing");
				}
				mc.setScreen(new InventoryScreen(mc.player));
				assertTrue(
					mc.currentScreen.children().stream()
						.filter(ButtonWidget.class::isInstance)
						.map(ButtonWidget.class::cast)
						.noneMatch(button -> List.of("Steal", "Store")
							.contains(button.getMessage().getString())),
					"AutoSteal added buttons to the player inventory");
			}finally
			{
				buttons.setChecked(visible);
				mc.setScreen(null);
				hack.setEnabled(enabled);
			}
		});
	}
}
