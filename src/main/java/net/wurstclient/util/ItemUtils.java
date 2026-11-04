/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.OptionalDouble;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.wurstclient.WurstClient;

public enum ItemUtils
{
	;
	
	/**
	 * @param nameOrId
	 *            a String containing the item's name ({@link Identifier}) or
	 *            numeric ID.
	 * @return the requested item, or null if the item doesn't exist.
	 */
	public static Item getItemFromNameOrID(String nameOrId)
	{
		if(MathUtils.isInteger(nameOrId))
		{
			// There is no getOrEmpty() for raw IDs, so this detects when the
			// Registry defaults and returns null instead
			int id = Integer.parseInt(nameOrId);
			Item item = Registries.ITEM.get(id);
			if(id != 0 && Registries.ITEM.getRawId(item) == 0)
				return null;
			
			return item;
		}
		
		try
		{
			return Registries.ITEM.getOrEmpty(new Identifier(nameOrId))
				.orElse(null);
			
		}catch(InvalidIdentifierException e)
		{
			return null;
		}
	}
	
	public static OptionalDouble calculateModifiedAttribute(ItemStack stack,
		EntityAttribute attribute, EquipmentSlot slot)
	{
		EntityAttributeInstance instance =
			new EntityAttributeInstance(attribute, ignored -> {});
		instance.setBaseValue(
			WurstClient.MC.player.getAttributeBaseValue(attribute));
		
		stack.getAttributeModifiers(slot).get(attribute).forEach(modifier -> {
			instance.removeModifier(modifier.getId());
			instance.addTemporaryModifier(modifier);
		});
		
		return instance.getModifiers().isEmpty() ? OptionalDouble.empty()
			: OptionalDouble.of(instance.getValue());
	}
	
	public static OptionalDouble getArmorAttribute(ItemStack stack,
		EntityAttribute attribute)
	{
		EquipmentSlot slot = getArmorSlot(stack);
		return slot == null ? OptionalDouble.empty()
			: calculateModifiedAttribute(stack, attribute, slot);
	}
	
	public static OptionalDouble getArmorPoints(ItemStack stack)
	{
		return getArmorAttribute(stack, EntityAttributes.GENERIC_ARMOR);
	}
	
	public static OptionalDouble getToughness(ItemStack stack)
	{
		return getArmorAttribute(stack,
			EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
	}
	
	public static EquipmentSlot getArmorSlot(ItemStack stack)
	{
		Equipment equipment = Equipment.fromStack(stack);
		if(equipment == null || !equipment.getSlotType().isArmorSlot())
			return null;
		
		return equipment.getSlotType();
	}
	
	/**
	 * Adds the specified enchantment to the specified item stack. Unlike
	 * {@link ItemStack#addEnchantment(Enchantment, int)}, this method doesn't
	 * limit the level to 127.
	 */
	public static void addEnchantment(ItemStack stack, Enchantment enchantment,
		int level)
	{
		Identifier id = EnchantmentHelper.getEnchantmentId(enchantment);
		NbtList nbt = getOrCreateNbtList(stack, ItemStack.ENCHANTMENTS_KEY);
		nbt.add(EnchantmentHelper.createNbt(id, level));
	}
	
	public static NbtList getOrCreateNbtList(ItemStack stack, String key)
	{
		NbtCompound nbt = stack.getOrCreateNbt();
		if(!nbt.contains(key, NbtElement.LIST_TYPE))
			nbt.put(key, new NbtList());
		
		return nbt.getList(key, NbtElement.COMPOUND_TYPE);
	}
}
