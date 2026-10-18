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
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.telemetry.TelemetryEventType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import net.wurstclient.WurstClient;
import net.wurstclient.events.ChatOutputListener;
import net.wurstclient.events.ConnectionPacketOutputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hacks.NewChunksHack;

public enum UpstreamBackportTest
{
	;
	
	public static void testLocalCollisionIsolation()
	{
		var hax = WurstClient.INSTANCE.getHax();
		BlockPos floor = new BlockPos(0, 99, 0);
		runChatCommand("setblock 0 99 0 soul_sand");
		try
		{
			submitAndWait(mc -> {
				var cow = EntityType.COW.create(mc.world);
				cow.setPosition(mc.player.getPos());
				BlockPos pos = mc.player.getBlockPos();
				BlockState water = Blocks.WATER.getDefaultState();
				hax.jesusHack.setEnabled(true);
				assertTrue(
					!water.getCollisionShape(mc.world, pos,
						ShapeContext.of(mc.player)).isEmpty(),
					"Jesus did not affect the local player");
				assertTrue(
					water.getCollisionShape(mc.world, pos, ShapeContext.of(cow))
						.isEmpty(),
					"Jesus changed a mob's fluid collisions");
				assertTrue(
					water
						.getCollisionShape(mc.world, pos, ShapeContext.absent())
						.isEmpty(),
					"Jesus changed collisions without an entity");
				hax.jesusHack.setEnabled(false);
				
				BlockState stone = Blocks.STONE.getDefaultState();
				hax.handNoClipHack.setEnabled(true);
				assertTrue(
					stone.getOutlineShape(mc.world, floor,
						ShapeContext.of(mc.player)).isEmpty(),
					"HandNoClip did not affect the local player");
				assertTrue(
					!stone
						.getOutlineShape(mc.world, floor, ShapeContext.of(cow))
						.isEmpty(),
					"HandNoClip changed a mob's block targeting");
				assertTrue(
					!stone
						.getOutlineShape(mc.world, floor, ShapeContext.absent())
						.isEmpty(),
					"HandNoClip changed block targeting without an entity");
				var camera = mc.getCameraEntity();
				try
				{
					mc.setCameraEntity(cow);
					assertTrue(
						stone.getOutlineShape(mc.world, floor,
							ShapeContext.of(cow)).isEmpty(),
						"HandNoClip did not support the camera entity");
				}finally
				{
					mc.setCameraEntity(camera);
				}
				hax.handNoClipHack.setEnabled(false);
				
				assertTrue(((Entity)mc.player).getVelocityMultiplier() < 1,
					"Soul sand setup did not slow the player");
				hax.noSlowdownHack.setEnabled(true);
				assertTrue(((Entity)mc.player).getVelocityMultiplier() == 1,
					"NoSlowdown did not remove local block slowdown");
				assertTrue(((Entity)cow).getVelocityMultiplier() < 1,
					"NoSlowdown changed a mob's block slowdown");
				assertTrue(Blocks.SOUL_SAND.getVelocityMultiplier() < 1,
					"NoSlowdown modified the shared block");
				hax.jesusHack.setEnabled(true);
				hax.handNoClipHack.setEnabled(true);
			});
			submitOnServer(server -> {
				var world = server.getOverworld();
				var cow = EntityType.COW.create(world);
				cow.setPosition(0.5, 100, 0.5);
				assertTrue(Blocks.WATER.getDefaultState()
					.getCollisionShape(world, floor, ShapeContext.of(cow))
					.isEmpty(), "Jesus changed server-side collisions");
				assertTrue(!Blocks.STONE.getDefaultState()
					.getOutlineShape(world, floor, ShapeContext.of(cow))
					.isEmpty(), "HandNoClip changed server-side targeting");
				assertTrue(((Entity)cow).getVelocityMultiplier() < 1,
					"NoSlowdown changed server-side block slowdown");
				return null;
			});
		}finally
		{
			submitAndWait(mc -> {
				hax.jesusHack.setEnabled(false);
				hax.handNoClipHack.setEnabled(false);
				hax.noSlowdownHack.setEnabled(false);
			});
			runChatCommand("setblock 0 99 0 stone");
			runChatCommand("tp @s 0.5 100 0.5 0 0");
			waitUntil("collision test restored the player to the platform",
				mc -> mc.player.isOnGround()
					&& Math.abs(mc.player.getY() - 100) < 0.1);
		}
	}
	
	public static void testChat()
	{
		var hack = WurstClient.INSTANCE.getHax().infiniChatHack;
		var events = WurstClient.INSTANCE.getEventManager();
		String longCommand = "/say " + "a".repeat(300);
		List<String> messages = new ArrayList<>();
		List<String> packets = new ArrayList<>();
		ChatOutputListener output = event -> {
			messages.add(event.getMessage());
			if(event.getMessage().equals("cancel backport test"))
				event.cancel();
			else
				event.setMessage("modified backport test");
		};
		PacketOutputListener packetOutput = event -> {
			if(event.getPacket() instanceof ChatMessageC2SPacket packet)
			{
				packets.add(packet.chatMessage());
				event.cancel();
			}
		};
		try
		{
			submitAndWait(mc -> {
				var screen = new ChatScreen(longCommand);
				assertTrue(screen.normalize(longCommand).length() == 256,
					"Disabled InfiniChat changed the vanilla limit");
				hack.setEnabled(true);
				mc.setScreen(screen);
				var field = screen.children().stream()
					.filter(TextFieldWidget.class::isInstance)
					.map(TextFieldWidget.class::cast).findFirst().orElseThrow();
				assertTrue(field.getText().equals(longCommand),
					"InfiniChat truncated the initial command");
				assertTrue(screen.normalize(longCommand).equals(longCommand),
					"InfiniChat truncated a submitted command");
				String wurstCommand = ".help " + "a".repeat(300);
				assertTrue(screen.normalize(wurstCommand).equals(wurstCommand),
					"InfiniChat truncated a Wurst command");
				assertTrue(screen.normalize("a".repeat(300)).length() == 256,
					"InfiniChat removed the normal chat safety limit");
				
				events.add(ChatOutputListener.class, output);
				events.add(PacketOutputListener.class, packetOutput);
				screen.sendMessage("  original   backport test  ", true);
				assertTrue(messages.equals(List.of("original backport test")),
					"Chat output did not fire once after normalization");
				assertTrue(packets.equals(List.of("modified backport test")),
					"The modified chat message was not sent once");
				var history = mc.inGameHud.getChatHud().getMessageHistory();
				assertTrue(
					history.get(history.size() - 1)
						.equals("modified backport test"),
					"Modified chat message was not saved to history");
				screen.sendMessage("cancel backport test", true);
				assertTrue(packets.size() == 1,
					"Cancelled chat message was still sent");
				assertTrue(
					history.get(history.size() - 1)
						.equals("cancel backport test"),
					"Cancelled chat message was not saved to history");
			});
		}finally
		{
			submitAndWait(mc -> {
				events.remove(ChatOutputListener.class, output);
				events.remove(PacketOutputListener.class, packetOutput);
				hack.setEnabled(false);
				mc.setScreen(null);
			});
		}
	}
	
	public static void testNewChunks()
	{
		var hack = WurstClient.INSTANCE.getHax().newChunksHack;
		submitAndWait(mc -> {
			hack.setEnabled(true);
			try
			{
				var blocks = new WorldChunk(mc.world, new ChunkPos(40, 40));
				var section = blocks.getSectionArray()[0];
				for(int y = 0; y < 16; y++)
					for(int z = 0; z < 16; z++)
						for(int x = 0; x < 16; x++)
							section.setBlockState(x, y, z,
								Blocks.STONE.getDefaultState());
				hack.afterLoadChunk(blocks);
				assertTrue(chunks(hack, "newChunks").contains(blocks.getPos()),
					"NewChunks missed unused air in the block palette");
				
				var biomes = new WorldChunk(mc.world, new ChunkPos(41, 40));
				var container = (PalettedContainer<RegistryEntry<Biome>>)biomes
					.getSectionArray()[0].getBiomeContainer();
				var desert = mc.world.getRegistryManager()
					.get(RegistryKeys.BIOME).entryOf(BiomeKeys.DESERT);
				for(int y = 0; y < 4; y++)
					for(int z = 0; z < 4; z++)
						for(int x = 0; x < 4; x++)
							container.set(x, y, z, desert);
				hack.afterLoadChunk(biomes);
				assertTrue(chunks(hack, "newChunks").contains(biomes.getPos()),
					"NewChunks missed unused plains in the biome palette");
				
				var old = new WorldChunk(mc.world, new ChunkPos(42, 40));
				old.getSectionArray()[0].setBlockState(1, 2, 3, flowingWater());
				hack.afterLoadChunk(old);
				assertTrue(chunks(hack, "oldChunks").contains(old.getPos()),
					"NewChunks missed flowing fluid in initial chunk data");
				assertTrue(
					reasons(hack, "oldChunkReasons").contains(new BlockPos(
						old.getPos().getStartX() + 1, old.getBottomY() + 2,
						old.getPos().getStartZ() + 3)),
					"NewChunks recorded the wrong fluid position");
				
				var empty = new WorldChunk(mc.world, new ChunkPos(43, 40));
				hack.afterLoadChunk(empty);
				assertTrue(
					!chunks(hack, "newChunks").contains(empty.getPos())
						&& !chunks(hack, "oldChunks").contains(empty.getPos()),
					"NewChunks classified an empty single-value palette");
				
				BlockPos pos = new BlockPos(8, 100, 0);
				BlockState previous = mc.world.getBlockState(pos);
				try
				{
					mc.world.setBlockState(pos, flowingWater());
					BlockPos.Mutable mutable = pos.mutableCopy();
					hack.afterUpdateBlock(mutable);
					mutable.set(1000, 1000, 1000);
					assertTrue(reasons(hack, "newChunkReasons").contains(pos),
						"NewChunks retained a mutable block update position");
				}finally
				{
					mc.world.setBlockState(pos, previous);
				}
				hack.onWorldChange();
				assertTrue(
					chunks(hack, "newChunks").isEmpty()
						&& chunks(hack, "oldChunks").isEmpty()
						&& reasons(hack, "newChunkReasons").isEmpty(),
					"NewChunks retained data after a world change");
			}finally
			{
				hack.setEnabled(false);
			}
		});
	}
	
	public static void testConnectionPackets()
	{
		var events = WurstClient.INSTANCE.getEventManager();
		var original = new UpdateSelectedSlotC2SPacket(2);
		var nested = new UpdateSelectedSlotC2SPacket(5);
		var cancelled = new UpdateSelectedSlotC2SPacket(6);
		List<Object> observed = new ArrayList<>();
		ConnectionPacketOutputListener listener = event -> {
			if(event.getPacket() == original)
			{
				observed.add(original);
				WurstClient.MC.getNetworkHandler().getConnection().send(nested,
					null);
				event.setPacket(new UpdateSelectedSlotC2SPacket(3));
			}else if(event.getPacket() == nested)
				observed.add(nested);
			else if(event.getPacket() == cancelled)
			{
				observed.add(cancelled);
				event.setPacket(new UpdateSelectedSlotC2SPacket(7));
				event.cancel();
			}
		};
		var uuid = submitAndGet(mc -> mc.player.getUuid());
		try
		{
			submitAndWait(mc -> {
				events.add(ConnectionPacketOutputListener.class, listener);
				mc.getNetworkHandler().getConnection().send(original);
			});
			waitUntil("replacement connection packet received",
				mc -> mc.getServer()
					.submit(() -> mc.getServer().getPlayerManager()
						.getPlayer(uuid).getInventory().selectedSlot == 3)
					.join());
			submitAndWait(mc -> mc.getNetworkHandler().getConnection()
				.send(cancelled, null));
			waitForWorldTicks(3);
			assertTrue(
				submitOnServer(server -> server.getPlayerManager()
					.getPlayer(uuid).getInventory().selectedSlot == 3),
				"Cancelled replacement connection packet reached the server");
			assertTrue(observed.equals(List.of(original, nested, cancelled)),
				"Connection packet events fired more than once or out of order");
		}finally
		{
			submitAndWait(mc -> {
				events.remove(ConnectionPacketOutputListener.class, listener);
				mc.getNetworkHandler().getConnection()
					.send(new UpdateSelectedSlotC2SPacket(
						mc.player.getInventory().selectedSlot));
			});
		}
	}
	
	public static void testIgnoredChunkPacket()
	{
		var packet = submitOnServer(server -> {
			var world = server.getOverworld();
			var chunk = new WorldChunk(world, new ChunkPos(10000, 10000));
			return new ChunkDataS2CPacket(chunk,
				world.getChunkManager().getLightingProvider(), new BitSet(),
				new BitSet());
		});
		submitAndWait(mc -> {
			var hack = WurstClient.INSTANCE.getHax().newChunksHack;
			hack.setEnabled(true);
			try
			{
				mc.getNetworkHandler().onChunkData(packet);
				assertTrue(
					chunks(hack, "newChunks").isEmpty()
						&& chunks(hack, "oldChunks").isEmpty()
						&& chunks(hack, "notOldChunks").isEmpty(),
					"NewChunks processed an ignored out-of-range packet");
			}finally
			{
				hack.setEnabled(false);
			}
		});
	}
	
	public static void testPortalAndFeedAura()
	{
		var hax = WurstClient.INSTANCE.getHax();
		try
		{
			openInventory();
			var inventory = submitAndGet(mc -> mc.currentScreen);
			submitAndWait(mc -> {
				hax.portalGuiHack.setEnabled(true);
				mc.player.setInNetherPortal(mc.player.getBlockPos());
			});
			waitForWorldTicks(2);
			assertTrue(submitAndGet(mc -> mc.currentScreen == inventory),
				"PortalGUI did not preserve the inventory screen");
			submitAndWait(mc -> {
				hax.portalGuiHack.setEnabled(false);
				mc.player.setInNetherPortal(mc.player.getBlockPos());
			});
			waitUntil("vanilla portal closes inventory",
				mc -> mc.currentScreen == null);
			submitAndWait(mc -> {
				hax.triggerBotHack.setEnabled(true);
				hax.feedAuraHack.setEnabled(true);
				assertTrue(hax.triggerBotHack.isEnabled(),
					"FeedAura still disabled a combat aura");
			});
		}finally
		{
			submitAndWait(mc -> {
				hax.portalGuiHack.setEnabled(false);
				hax.feedAuraHack.setEnabled(false);
				hax.triggerBotHack.setEnabled(false);
				mc.setScreen(null);
			});
		}
	}
	
	public static void testTelemetry()
	{
		submitAndWait(mc -> {
			var otf = WurstClient.INSTANCE.getOtfs().noTelemetryOtf;
			boolean wasEnabled = otf.isEnabled();
			boolean apiEnabled = mc.isTelemetryEnabledByApi();
			boolean optionalApiEnabled = mc.isOptionalTelemetryEnabledByApi();
			AtomicInteger sent = new AtomicInteger();
			var sender =
				otf.wrapSender((type, builder) -> sent.incrementAndGet());
			try
			{
				if(!otf.isEnabled())
					otf.doPrimaryAction();
				sender.send(TelemetryEventType.WORLD_LOADED, builder -> {});
				assertTrue(sent.get() == 0,
					"NoTelemetry allowed a cached sender to send");
				otf.doPrimaryAction();
				sender.send(TelemetryEventType.WORLD_LOADED, builder -> {});
				assertTrue(sent.get() == 1,
					"Re-enabled telemetry did not use the original sender");
				otf.doPrimaryAction();
				sender.send(TelemetryEventType.WORLD_LOADED, builder -> {});
				assertTrue(sent.get() == 1,
					"NoTelemetry did not stop an existing sender");
				assertTrue(mc.isTelemetryEnabledByApi() == apiEnabled && mc
					.isOptionalTelemetryEnabledByApi() == optionalApiEnabled,
					"NoTelemetry changed the account's API permissions");
			}finally
			{
				if(otf.isEnabled() != wasEnabled)
					otf.doPrimaryAction();
			}
		});
	}
	
	private static BlockState flowingWater()
	{
		return Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL, 1);
	}
	
	@SuppressWarnings("unchecked")
	private static Set<ChunkPos> chunks(NewChunksHack hack, String name)
	{
		return (Set<ChunkPos>)readField(hack, name);
	}
	
	@SuppressWarnings("unchecked")
	private static Set<BlockPos> reasons(NewChunksHack hack, String name)
	{
		return (Set<BlockPos>)readField(hack, name);
	}
	
	private static Object readField(NewChunksHack hack, String name)
	{
		try
		{
			Field field = NewChunksHack.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(hack);
		}catch(ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}
}
