/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.test;

import static net.wurstclient.test.WurstClientTestHelper.*;
import static net.wurstclient.util.MobDisposition.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Bucketable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Saddleable;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.wurstclient.settings.filterlists.EntityFilterList.EntityFilter;
import net.wurstclient.settings.filters.AttackDetectingEntityFilter.Mode;
import net.wurstclient.settings.filters.FilterBabiesSetting;
import net.wurstclient.settings.filters.FilterEndermenSetting;
import net.wurstclient.settings.filters.FilterNeutralSetting;
import net.wurstclient.settings.filters.FilterPassiveWaterSetting;
import net.wurstclient.settings.filters.FilterPetsSetting;
import net.wurstclient.settings.filters.FilterZombiePiglinsSetting;
import net.wurstclient.util.MobDisposition;

public enum EntityFilterRegressionTest
{
	;
	
	public static void testBabies()
	{
		submitAndWait(mc -> {
			EntityFilter filter = FilterBabiesSetting.genericCombat(true);
			for(EntityType<? extends MobEntity> type : List.of(EntityType.COW,
				EntityType.HOGLIN, EntityType.HORSE, EntityType.SKELETON_HORSE,
				EntityType.ZOMBIE_HORSE, EntityType.VILLAGER))
			{
				MobEntity mob = type.create(mc.world);
				assertFilter(filter, mob, true);
				mob.setBaby(true);
				assertFilter(filter, mob, false);
			}
			for(EntityType<? extends MobEntity> type : List
				.of(EntityType.PIGLIN, EntityType.ZOMBIE, EntityType.ZOGLIN))
			{
				MobEntity mob = type.create(mc.world);
				mob.setBaby(true);
				assertFilter(filter, mob, true);
			}
			assertFilter(filter, EntityType.TADPOLE.create(mc.world), false);
			assertFilter(filter, EntityType.FROG.create(mc.world), true);
		});
	}
	
	public static void testCalm()
	{
		submitAndWait(mc -> {
			EntityFilter neutral =
				FilterNeutralSetting.genericCombat(Mode.IF_CALM);
			for(EntityFilter filter : List.of(neutral,
				FilterEndermenSetting.genericCombat(Mode.IF_CALM)))
			{
				var enderman = EntityType.ENDERMAN.create(mc.world);
				assertFilter(filter, enderman, false);
				enderman.setTarget(mc.player);
				assertFilter(filter, enderman, true);
			}
			var panda = EntityType.PANDA.create(mc.world);
			panda.setMainGene(PandaEntity.Gene.AGGRESSIVE);
			panda.setHiddenGene(PandaEntity.Gene.AGGRESSIVE);
			assertFilter(neutral, panda, false);
			panda.setAttacking(true);
			assertFilter(neutral, panda, true);
			
			var goat = EntityType.GOAT.create(mc.world);
			assertFilter(neutral, goat, false);
			goat.handleStatus((byte)58);
			assertFilter(neutral, goat, true);
			goat.handleStatus((byte)59);
			assertFilter(neutral, goat, false);
			
			for(EntityType<? extends MobEntity> type : List.of(EntityType.WOLF,
				EntityType.BEE))
			{
				MobEntity mob = type.create(mc.world);
				assertFilter(neutral, mob, false);
				((net.minecraft.entity.mob.Angerable)mob).setAngerTime(200);
				assertFilter(neutral, mob, true);
				((net.minecraft.entity.mob.Angerable)mob).setAngerTime(0);
				assertFilter(neutral, mob, false);
			}
			var piglin = EntityType.ZOMBIFIED_PIGLIN.create(mc.world);
			EntityFilter piglinFilter =
				FilterZombiePiglinsSetting.genericCombat(Mode.IF_CALM);
			for(EntityFilter filter : List.of(neutral, piglinFilter))
				assertFilter(filter, piglin, false);
			piglin.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
				.addTemporaryModifier(new EntityAttributeModifier(
					ZombifiedPiglinEntity.ATTACKING_SPEED_BOOST_ID,
					"Attacking speed boost", 0.05,
					EntityAttributeModifier.Operation.ADDITION));
			for(EntityFilter filter : List.of(neutral, piglinFilter))
				assertFilter(filter, piglin, true);
		});
	}
	
	public static void testPassiveWater()
	{
		submitAndWait(mc -> {
			EntityFilter filter = new FilterPassiveWaterSetting("", true);
			for(EntityType<?> type : List.of(EntityType.AXOLOTL, EntityType.COD,
				EntityType.DOLPHIN, EntityType.GLOW_SQUID, EntityType.SALMON,
				EntityType.SQUID, EntityType.TADPOLE, EntityType.TROPICAL_FISH,
				EntityType.TURTLE))
				assertFilter(filter, type.create(mc.world), false);
			for(EntityType<?> type : List.of(EntityType.DROWNED,
				EntityType.ELDER_GUARDIAN, EntityType.GUARDIAN,
				EntityType.PUFFERFISH, EntityType.FROG))
				assertFilter(filter, type.create(mc.world), true);
		});
	}
	
	public static void testPets()
	{
		submitAndWait(mc -> {
			EntityFilter filter = FilterPetsSetting.genericCombat(true);
			for(EntityType<? extends TameableEntity> type : List
				.of(EntityType.WOLF, EntityType.CAT, EntityType.PARROT))
			{
				TameableEntity mob = type.create(mc.world);
				assertFilter(filter, mob, true);
				mob.setTamed(true);
				assertFilter(filter, mob, false);
			}
			for(EntityType<? extends AbstractHorseEntity> type : List.of(
				EntityType.HORSE, EntityType.SKELETON_HORSE,
				EntityType.ZOMBIE_HORSE))
			{
				AbstractHorseEntity mob = type.create(mc.world);
				assertFilter(filter, mob, true);
				mob.setTame(true);
				assertFilter(filter, mob, false);
			}
			var golem = EntityType.IRON_GOLEM.create(mc.world);
			assertFilter(filter, golem, true);
			golem.setPlayerCreated(true);
			assertFilter(filter, golem, false);
			assertFilter(filter, EntityType.TADPOLE.create(mc.world), true);
			for(EntityType<?> type : List.of(EntityType.COD,
				EntityType.AXOLOTL))
			{
				Entity mob = type.create(mc.world);
				assertFilter(filter, mob, true);
				((Bucketable)mob).setFromBucket(true);
				assertFilter(filter, mob, false);
			}
			var ocelot = EntityType.OCELOT.create(mc.world);
			assertFilter(filter, ocelot, true);
			NbtCompound trust = new NbtCompound();
			trust.putBoolean("Trusting", true);
			ocelot.readCustomDataFromNbt(trust);
			assertFilter(filter, ocelot, false);
			var fox = EntityType.FOX.create(mc.world);
			assertFilter(filter, fox, true);
			NbtList trusted = new NbtList();
			trusted.add(NbtHelper.fromUuid(UUID.randomUUID()));
			trust.put("Trusted", trusted);
			fox.readCustomDataFromNbt(trust);
			assertFilter(filter, fox, false);
		});
		submitOnServer(server -> {
			EntityFilter filter = FilterPetsSetting.genericCombat(true);
			for(EntityType<?> type : List.of(EntityType.CAMEL, EntityType.PIG,
				EntityType.STRIDER))
			{
				Entity mob = type.create(server.getOverworld());
				assertFilter(filter, mob, true);
				((Saddleable)mob).saddle(SoundCategory.NEUTRAL);
				assertFilter(filter, mob, false);
			}
			return null;
		});
	}
	
	public static void testDisposition()
	{
		submitAndWait(mc -> {
			Set<EntityType<?>> checked = new HashSet<>();
			checkTypes(checked, PASSIVE, EntityType.ALLAY, EntityType.AXOLOTL,
				EntityType.BAT, EntityType.CAMEL, EntityType.CAT,
				EntityType.CHICKEN, EntityType.COD, EntityType.COW,
				EntityType.DOLPHIN, EntityType.DONKEY, EntityType.FOX,
				EntityType.FROG, EntityType.GLOW_SQUID, EntityType.HORSE,
				EntityType.LLAMA, EntityType.MOOSHROOM, EntityType.MULE,
				EntityType.OCELOT, EntityType.PANDA, EntityType.PARROT,
				EntityType.PIG, EntityType.RABBIT, EntityType.SALMON,
				EntityType.SHEEP, EntityType.SKELETON_HORSE, EntityType.SLIME,
				EntityType.SNIFFER, EntityType.SNOW_GOLEM, EntityType.SQUID,
				EntityType.STRIDER, EntityType.TADPOLE, EntityType.TRADER_LLAMA,
				EntityType.TROPICAL_FISH, EntityType.TURTLE,
				EntityType.VILLAGER, EntityType.WANDERING_TRADER,
				EntityType.ZOMBIE_HORSE);
			checkTypes(checked, NEUTRAL, EntityType.BEE, EntityType.ENDERMAN,
				EntityType.GOAT, EntityType.IRON_GOLEM, EntityType.PIGLIN,
				EntityType.POLAR_BEAR, EntityType.PUFFERFISH, EntityType.WOLF,
				EntityType.ZOMBIFIED_PIGLIN);
			checkTypes(checked, HOSTILE, EntityType.BLAZE,
				EntityType.CAVE_SPIDER, EntityType.CREEPER, EntityType.DROWNED,
				EntityType.ELDER_GUARDIAN, EntityType.ENDERMITE,
				EntityType.ENDER_DRAGON, EntityType.EVOKER, EntityType.GHAST,
				EntityType.GIANT, EntityType.GUARDIAN, EntityType.HOGLIN,
				EntityType.HUSK, EntityType.ILLUSIONER, EntityType.MAGMA_CUBE,
				EntityType.PHANTOM, EntityType.PIGLIN_BRUTE,
				EntityType.PILLAGER, EntityType.RAVAGER, EntityType.SHULKER,
				EntityType.SILVERFISH, EntityType.SKELETON, EntityType.SPIDER,
				EntityType.STRAY, EntityType.VEX, EntityType.VINDICATOR,
				EntityType.WARDEN, EntityType.WITCH, EntityType.WITHER,
				EntityType.WITHER_SKELETON, EntityType.ZOGLIN,
				EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER);
			for(EntityType<?> type : Registries.ENTITY_TYPE)
				if(!checked.contains(type)
					&& Registries.ENTITY_TYPE.getId(type).getNamespace()
						.equals("minecraft")
					&& type.create(mc.world) instanceof MobEntity)
					throw new AssertionError("Unclassified mob: " + type);
				
			IronGolemEntity golem = EntityType.IRON_GOLEM.create(mc.world);
			golem.setPlayerCreated(true);
			assertGroup(PASSIVE, golem);
			PandaEntity panda = EntityType.PANDA.create(mc.world);
			panda.setMainGene(PandaEntity.Gene.AGGRESSIVE);
			assertGroup(NEUTRAL, panda);
			RabbitEntity rabbit = EntityType.RABBIT.create(mc.world);
			rabbit.setVariant(RabbitEntity.RabbitType.EVIL);
			assertGroup(HOSTILE, rabbit);
			PiglinEntity piglin = EntityType.PIGLIN.create(mc.world);
			piglin.setBaby(true);
			assertGroup(PASSIVE, piglin);
			SlimeEntity slime = EntityType.SLIME.create(mc.world);
			slime.setSize(2, true);
			assertGroup(HOSTILE, slime);
		});
	}
	
	@SafeVarargs
	private static void checkTypes(Set<EntityType<?>> checked,
		MobDisposition expected, EntityType<? extends MobEntity>... types)
	{
		for(EntityType<? extends MobEntity> type : types)
		{
			checked.add(type);
			assertGroup(expected,
				type.create(net.wurstclient.WurstClient.MC.world));
		}
	}
	
	private static void assertGroup(MobDisposition expected, MobEntity mob)
	{
		assertTrue(MobDisposition.of(mob) == expected, "Expected " + expected
			+ " for " + mob.getType() + ", got " + MobDisposition.of(mob));
	}
	
	private static void assertFilter(EntityFilter filter, Entity entity,
		boolean allowed)
	{
		assertTrue(filter.test(entity) == allowed,
			filter.getSetting().getName() + " incorrectly "
				+ (allowed ? "filtered" : "allowed") + " " + entity.getType());
	}
}
