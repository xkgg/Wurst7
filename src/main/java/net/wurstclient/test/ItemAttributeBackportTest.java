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

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.ItemUtils;

public enum ItemAttributeBackportTest
{
	;
	
	public static void testAttributeModifiers()
	{
		submitAndWait(mc -> {
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			assertNear(
				ItemUtils.calculateModifiedAttribute(sword,
					EntityAttributes.GENERIC_ATTACK_DAMAGE,
					EquipmentSlot.MAINHAND).orElseThrow(),
				7, "Vanilla sword damage");
			assertNear(
				ItemUtils.calculateModifiedAttribute(sword,
					EntityAttributes.GENERIC_ATTACK_SPEED,
					EquipmentSlot.MAINHAND).orElseThrow(),
				1.6, "Vanilla sword attack speed");
			assertTrue(
				ItemUtils.calculateModifiedAttribute(sword,
					EntityAttributes.GENERIC_ATTACK_DAMAGE,
					EquipmentSlot.OFFHAND).isEmpty(),
				"Main-hand modifier applied to the off hand");
			assertTrue(
				ItemUtils.calculateModifiedAttribute(new ItemStack(Items.STICK),
					EntityAttributes.GENERIC_ATTACK_DAMAGE,
					EquipmentSlot.MAINHAND).isEmpty(),
				"An unmodified item returned the player's base attribute");
			
			var live = mc.player
				.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
			double originalBase = live.getBaseValue();
			var playerBuff = new EntityAttributeModifier("backport_player_buff",
				100, Operation.ADDITION);
			try
			{
				live.setBaseValue(2);
				live.addTemporaryModifier(playerBuff);
				double liveValue = live.getValue();
				var addition = new EntityAttributeModifier("backport_addition",
					3, Operation.ADDITION);
				sword.addAttributeModifier(
					EntityAttributes.GENERIC_ATTACK_DAMAGE, addition,
					EquipmentSlot.MAINHAND);
				sword.addAttributeModifier(
					EntityAttributes.GENERIC_ATTACK_DAMAGE,
					new EntityAttributeModifier(addition.getId(),
						"backport_duplicate", 999, Operation.ADDITION),
					EquipmentSlot.MAINHAND);
				sword.addAttributeModifier(
					EntityAttributes.GENERIC_ATTACK_DAMAGE,
					new EntityAttributeModifier("backport_base", 0.5,
						Operation.MULTIPLY_BASE),
					EquipmentSlot.MAINHAND);
				sword.addAttributeModifier(
					EntityAttributes.GENERIC_ATTACK_DAMAGE,
					new EntityAttributeModifier("backport_total_a", 0.25,
						Operation.MULTIPLY_TOTAL),
					EquipmentSlot.MAINHAND);
				sword.addAttributeModifier(
					EntityAttributes.GENERIC_ATTACK_DAMAGE,
					new EntityAttributeModifier("backport_total_b", 0.2,
						Operation.MULTIPLY_TOTAL),
					EquipmentSlot.MAINHAND);
				var nbt = sword.getNbt().copy();
				assertNear(
					ItemUtils.calculateModifiedAttribute(sword,
						EntityAttributes.GENERIC_ATTACK_DAMAGE,
						EquipmentSlot.MAINHAND).orElseThrow(),
					11.25, "Stack overrides and attribute operation order");
				assertTrue(sword.getNbt().equals(nbt),
					"Calculating an attribute changed the stack");
				assertNear(live.getValue(), liveValue,
					"Calculating an attribute changed the player's modifiers");
				assertTrue(
					ItemUtils.calculateModifiedAttribute(sword,
						EntityAttributes.GENERIC_ATTACK_SPEED,
						EquipmentSlot.MAINHAND).isEmpty(),
					"NBT overrides retained the item's default attack speed");
				sword.getOrCreateNbt().put("AttributeModifiers", new NbtList());
				assertTrue(
					ItemUtils.calculateModifiedAttribute(sword,
						EntityAttributes.GENERIC_ATTACK_DAMAGE,
						EquipmentSlot.MAINHAND).isEmpty(),
					"An empty stack override retained default attributes");
			}finally
			{
				live.removeModifier(playerBuff);
				live.setBaseValue(originalBase);
			}
			
			ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
			assertNear(ItemUtils.getArmorPoints(chestplate).orElseThrow(), 8,
				"Vanilla chestplate armor");
			assertNear(ItemUtils.getToughness(chestplate).orElseThrow(), 2,
				"Vanilla chestplate toughness");
			chestplate.addAttributeModifier(EntityAttributes.GENERIC_ARMOR,
				new EntityAttributeModifier("backport_clamping", 100,
					Operation.ADDITION),
				EquipmentSlot.CHEST);
			assertNear(ItemUtils.getArmorPoints(chestplate).orElseThrow(), 30,
				"Armor attribute maximum");
			assertTrue(ItemUtils.getToughness(chestplate).isEmpty(),
				"Armor NBT overrides retained default toughness");
			assertTrue(
				ItemUtils.getArmorSlot(
					new ItemStack(Items.PLAYER_HEAD)) == EquipmentSlot.HEAD,
				"A wearable head lost its equipment slot");
			assertTrue(
				ItemUtils.getArmorSlot(
					new ItemStack(Items.ELYTRA)) == EquipmentSlot.CHEST,
				"Elytra lost its equipment slot");
			assertTrue(
				ItemUtils.getArmorSlot(new ItemStack(Items.SHIELD)) == null,
				"A shield was treated as armor");
		});
	}
	
	public static void testAutoSword()
	{
		var hack = WurstClient.INSTANCE.getHax().autoSwordHack;
		submitAndWait(mc -> {
			var inventory = mc.player.getInventory();
			var original = new ArrayList<ItemStack>();
			for(int slot = 0; slot < 9; slot++)
				original.add(inventory.getStack(slot).copy());
			int originalSlot = inventory.selectedSlot;
			var priority = (EnumSetting<?>)hack.getSettings().get("优先级");
			String originalPriority = priority.getSelected().toString();
			boolean enabled = hack.isEnabled();
			hack.setEnabled(false);
			try
			{
				for(int slot = 0; slot < 9; slot++)
					inventory.setStack(slot, ItemStack.EMPTY);
				inventory.setStack(0, new ItemStack(Items.DIAMOND_SWORD));
				ItemStack custom = new ItemStack(Items.WOODEN_SWORD);
				custom.addAttributeModifier(
					EntityAttributes.GENERIC_ATTACK_SPEED,
					new EntityAttributeModifier("backport_speed", 1,
						Operation.ADDITION),
					EquipmentSlot.MAINHAND);
				inventory.setStack(1, custom);
				var cow = EntityType.COW.create(mc.world);
				hack.setEnabled(true);
				priority.setSelected(priority.getValues()[0].toString());
				hack.setSlot(cow);
				assertTrue(inventory.selectedSlot == 1,
					"AutoSword ignored a stack's overridden attack speed");
				
				priority.setSelected(priority.getValues()[1].toString());
				custom = new ItemStack(Items.WOODEN_SWORD);
				custom.addAttributeModifier(
					EntityAttributes.GENERIC_ATTACK_DAMAGE,
					new EntityAttributeModifier("backport_damage", 12,
						Operation.ADDITION),
					EquipmentSlot.MAINHAND);
				inventory.setStack(1, custom);
				hack.setSlot(cow);
				assertTrue(inventory.selectedSlot == 1,
					"AutoSword ignored a stack's overridden attack damage");
				
				ItemStack smite = new ItemStack(Items.WOODEN_SWORD);
				smite.addEnchantment(Enchantments.SMITE, 5);
				inventory.setStack(1, smite);
				hack.setSlot(EntityType.ZOMBIE.create(mc.world));
				assertTrue(inventory.selectedSlot == 1,
					"AutoSword ignored Smite damage against undead targets");
				hack.setSlot(cow);
				assertTrue(inventory.selectedSlot == 0,
					"AutoSword applied Smite damage to a normal target");
				ItemStack sharpness = new ItemStack(Items.IRON_SWORD);
				sharpness.addEnchantment(Enchantments.SHARPNESS, 3);
				inventory.setStack(1, sharpness);
				hack.setSlot(cow);
				assertTrue(inventory.selectedSlot == 1,
					"AutoSword ignored Sharpness damage");
				
				for(int slot = 0; slot < 9; slot++)
					inventory.setStack(slot, ItemStack.EMPTY);
				ItemUtils.addEnchantment(custom, Enchantments.SHARPNESS, 32767);
				custom.getOrCreateNbt().put("AttributeModifiers",
					new NbtList());
				inventory.setStack(0, custom);
				inventory.selectedSlot = 8;
				hack.setSlot(cow);
				assertTrue(inventory.selectedSlot == 8,
					"AutoSword selected a weapon without an attack modifier");
			}finally
			{
				hack.setEnabled(false);
				priority.setSelected(originalPriority);
				for(int slot = 0; slot < 9; slot++)
					inventory.setStack(slot, original.get(slot));
				inventory.selectedSlot = originalSlot;
				hack.setEnabled(enabled);
			}
		});
	}
	
	public static void testAutoArmor()
	{
		var hack = WurstClient.INSTANCE.getHax().autoArmorHack;
		var uuid = submitAndGet(mc -> mc.player.getUuid());
		runChatCommand("clear");
		try
		{
			submitOnServer(server -> {
				var player = server.getPlayerManager().getPlayer(uuid);
				var custom = new ItemStack(Items.LEATHER_CHESTPLATE);
				custom.addAttributeModifier(EntityAttributes.GENERIC_ARMOR,
					new EntityAttributeModifier("backport_armor", 12,
						Operation.ADDITION),
					EquipmentSlot.CHEST);
				player.getInventory().setStack(9, custom);
				player.equipStack(EquipmentSlot.CHEST,
					new ItemStack(Items.DIAMOND_CHESTPLATE));
				player.playerScreenHandler.sendContentUpdates();
				return null;
			});
			waitUntil("attribute armor test inventory synchronized",
				mc -> mc.player.getInventory().getStack(9)
					.isOf(Items.LEATHER_CHESTPLATE)
					&& mc.player.getEquippedStack(EquipmentSlot.CHEST)
						.isOf(Items.DIAMOND_CHESTPLATE));
			submitAndWait(mc -> {
				hack.getSettings().values()
					.forEach(setting -> setting.resetToDefault());
				hack.setEnabled(true);
			});
			waitUntil("AutoArmor equipped the stronger stack",
				mc -> mc.player.getEquippedStack(EquipmentSlot.CHEST)
					.isOf(Items.LEATHER_CHESTPLATE));
			waitUntil("server accepted the stronger armor",
				mc -> mc.getServer()
					.submit(() -> mc.getServer().getPlayerManager()
						.getPlayer(uuid).getEquippedStack(EquipmentSlot.CHEST)
						.isOf(Items.LEATHER_CHESTPLATE))
					.join());
			waitForWorldTicks(5);
			assertTrue(
				submitAndGet(
					mc -> mc.player.getEquippedStack(EquipmentSlot.CHEST)
						.isOf(Items.LEATHER_CHESTPLATE)),
				"AutoArmor ignored the worn stack's custom attributes");
		}finally
		{
			submitAndWait(mc -> hack.setEnabled(false));
			runChatCommand("clear");
		}
	}
	
	private static void assertNear(double actual, double expected,
		String message)
	{
		assertTrue(Math.abs(actual - expected) < 1e-5,
			message + ": expected " + expected + ", got " + actual);
	}
}
