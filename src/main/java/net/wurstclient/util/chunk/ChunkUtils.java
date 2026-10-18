/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.chunk;

import java.util.Objects;
import java.util.stream.Stream;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.wurstclient.WurstClient;

public enum ChunkUtils
{
	;
	
	private static final MinecraftClient MC = WurstClient.MC;
	
	public static Stream<BlockEntity> getLoadedBlockEntities()
	{
		return getLoadedChunks()
			.flatMap(chunk -> chunk.getBlockEntities().values().stream());
	}
	
	public static int getManhattanDistance(ChunkPos a, ChunkPos b)
	{
		return Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
	}
	
	/**
	 * Returns the position of the chunk affected by the given
	 * {@link BlockUpdateS2CPacket}, {@link ChunkDeltaUpdateS2CPacket}, or
	 * {@link ChunkDataS2CPacket}.
	 *
	 * <p>
	 * Returns <code>null</code> if the given packet is of a different type than
	 * the ones listed above.
	 */
	public static ChunkPos getAffectedChunk(Packet<?> packet)
	{
		if(packet instanceof BlockUpdateS2CPacket p)
			return new ChunkPos(p.getPos());
		if(packet instanceof ChunkDeltaUpdateS2CPacket p)
			return p.sectionPos.toChunkPos();
		if(packet instanceof ChunkDataS2CPacket p)
			return new ChunkPos(p.getX(), p.getZ());
		
		return null;
	}
	
	public static Stream<WorldChunk> getLoadedChunks()
	{
		int radius = Math.max(2, MC.options.getClampedViewDistance()) + 3;
		return ChunkPos.stream(MC.player.getChunkPos(), radius)
			.filter(c -> MC.world.isChunkLoaded(c.x, c.z))
			.map(c -> MC.world.getChunk(c.x, c.z)).filter(Objects::nonNull);
	}
	
	/**
	 * Returns the y-coordinate of the highest non-empty section in the chunk.
	 *
	 * <p>
	 * This is a re-implementation of
	 * {@link Chunk#getHighestNonEmptySectionYOffset()}, which has been
	 * deprecated and marked for removal in 23w17a with no apparent replacement
	 * provided by Mojang.
	 */
	public static int getHighestNonEmptySectionYOffset(Chunk chunk)
	{
		int i = chunk.getHighestNonEmptySection();
		if(i == -1)
			return chunk.getBottomY();
		
		return ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(i));
	}
}
