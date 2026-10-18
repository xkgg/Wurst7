/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IdListPalette;
import net.minecraft.world.chunk.Palette;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.chunk.SingularPalette;
import net.minecraft.world.chunk.WorldChunk;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.newchunks.NewChunksChunkRenderer;
import net.wurstclient.hacks.newchunks.NewChunksReasonsRenderer;
import net.wurstclient.hacks.newchunks.NewChunksRenderer;
import net.wurstclient.hacks.newchunks.NewChunksShowSetting;
import net.wurstclient.hacks.newchunks.NewChunksShowSetting.Show;
import net.wurstclient.hacks.newchunks.NewChunksStyleSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;

@SearchTags({"新区块", "new chunks"})
public final class NewChunksHack extends Hack
	implements UpdateListener, RenderListener
{
	private final NewChunksStyleSetting style = new NewChunksStyleSetting();
	
	private final NewChunksShowSetting show = new NewChunksShowSetting();
	
	private final CheckboxSetting showReasons = new CheckboxSetting("显示原因",
		"高亮显示导致区块被标记为新/旧的流体方块。\n\n" + "根据生物群系或方块调色板标记的区块没有可高亮的原因方块。", false);
	
	private final CheckboxSetting showCounter =
		new CheckboxSetting("显示计数器", "显示到目前为止找到的新/旧区块数量。", false);
	
	private final SliderSetting altitude =
		new SliderSetting("高度", 0, -64, 320, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting drawDistance =
		new SliderSetting("绘制距离", 32, 8, 64, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting opacity =
		new SliderSetting("不透明度", 0.75, 0.1, 1, 0.01, ValueDisplay.PERCENTAGE);
	
	private final ColorSetting newChunksColor =
		new ColorSetting("新区块颜色", Color.RED);
	
	private final ColorSetting oldChunksColor =
		new ColorSetting("旧区块颜色", Color.BLUE);
	
	private final CheckboxSetting logChunks =
		new CheckboxSetting("记录区块", "当找到新/旧区块时写入日志文件。", false);
	
	private final Set<ChunkPos> newChunks = new HashSet<>();
	private final Set<ChunkPos> oldChunks = new HashSet<>();
	private final Set<ChunkPos> notOldChunks = new HashSet<>();
	
	private final Set<BlockPos> newChunkReasons = new HashSet<>();
	private final Set<BlockPos> oldChunkReasons = new HashSet<>();
	
	private final NewChunksRenderer renderer = new NewChunksRenderer(altitude,
		opacity, newChunksColor, oldChunksColor);
	private final NewChunksReasonsRenderer reasonsRenderer =
		new NewChunksReasonsRenderer(drawDistance);
	
	private RegionPos lastRegion;
	
	public NewChunksHack()
	{
		super("新区块");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(show);
		addSetting(showReasons);
		addSetting(showCounter);
		addSetting(altitude);
		addSetting(drawDistance);
		addSetting(opacity);
		addSetting(newChunksColor);
		addSetting(oldChunksColor);
		addSetting(logChunks);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		reset();
	}
	
	private void reset()
	{
		newChunks.clear();
		oldChunks.clear();
		notOldChunks.clear();
		newChunkReasons.clear();
		oldChunkReasons.clear();
		lastRegion = null;
	}
	
	public void onWorldChange()
	{
		reset();
		renderer.closeBuffers();
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		renderer.closeBuffers();
	}
	
	@Override
	public String getRenderName()
	{
		if(!showCounter.isChecked())
			return getName();
		
		return String.format("%s [%d/%d]", getName(), newChunks.size(),
			oldChunks.size());
	}
	
	@Override
	public void onUpdate()
	{
		renderer.closeBuffers();
		
		Show showSetting = show.getSelected();
		int dd = drawDistance.getValueI();
		NewChunksChunkRenderer chunkRenderer =
			style.getSelected().getChunkRenderer();
		
		if(showSetting.includesNew())
		{
			renderer.updateBuffer(0, chunkRenderer.getLayer(),
				buffer -> chunkRenderer.buildBuffer(buffer, newChunks, dd));
			
			if(showReasons.isChecked())
				renderer.updateBuffer(1, reasonsRenderer.getLayer(),
					buffer -> reasonsRenderer.buildBuffer(buffer,
						List.copyOf(newChunkReasons)));
		}
		
		if(showSetting.includesOld())
		{
			renderer.updateBuffer(2, chunkRenderer.getLayer(),
				buffer -> chunkRenderer.buildBuffer(buffer, oldChunks, dd));
			
			if(showReasons.isChecked())
				renderer.updateBuffer(3, reasonsRenderer.getLayer(),
					buffer -> reasonsRenderer.buildBuffer(buffer,
						List.copyOf(oldChunkReasons)));
		}
	}
	
	public void afterLoadChunk(WorldChunk chunk)
	{
		if(!isEnabled())
			return;
		
		ChunkPos chunkPos = chunk.getPos();
		if(newChunks.contains(chunkPos) || oldChunks.contains(chunkPos)
			|| notOldChunks.contains(chunkPos))
			return;
		
		// Generation can leave unused palette entries until the chunk is saved.
		for(ChunkSection section : chunk.getSectionArray())
			if(hasUnusedEntry(section.getBiomeContainer(),
				biome -> biome.matchesKey(BiomeKeys.PLAINS))
				|| hasUnusedEntry(section.getBlockStateContainer(),
					state -> state.isOf(Blocks.AIR)))
			{
				newChunks.add(chunkPos);
				if(logChunks.isChecked())
					System.out.println("new chunk (palette) at " + chunkPos);
				return;
			}
			
		// Flowing fluid in the initial packet indicates a previously loaded
		// chunk.
		BlockPos reasonForOld = findFlowingFluid(chunk);
		if(reasonForOld == null)
		{
			notOldChunks.add(chunkPos);
			return;
		}
		
		oldChunks.add(chunkPos);
		oldChunkReasons.add(reasonForOld);
		if(logChunks.isChecked())
			System.out.println("old chunk (fluid) at " + chunkPos);
	}
	
	private static <T> boolean hasUnusedEntry(ReadableContainer<T> container,
		Predicate<T> predicate)
	{
		Palette<T> palette = ((PalettedContainer<T>)container).data.palette();
		
		if(palette instanceof IdListPalette<?>
			|| palette instanceof SingularPalette<?>
			|| !palette.hasAny(predicate))
			return false;
		
		Set<T> usedEntries = new HashSet<>();
		container.count((entry, count) -> usedEntries.add(entry));
		return usedEntries.stream().noneMatch(predicate);
	}
	
	private BlockPos findFlowingFluid(WorldChunk chunk)
	{
		ChunkSection[] sections = chunk.getSectionArray();
		for(int i = 0; i < sections.length; i++)
		{
			ChunkSection section = sections[i];
			if(section.isEmpty()
				|| !section.hasAny(state -> isFlowing(state.getFluidState())))
				continue;
			
			for(int y = 0; y < 16; y++)
				for(int z = 0; z < 16; z++)
					for(int x = 0; x < 16; x++)
						if(isFlowing(section.getFluidState(x, y, z)))
							return new BlockPos(chunk.getPos().getStartX() + x,
								chunk.getBottomY() + i * 16 + y,
								chunk.getPos().getStartZ() + z);
		}
		
		return null;
	}
	
	private boolean isFlowing(FluidState fluidState)
	{
		return !fluidState.isEmpty() && !fluidState.isStill();
	}
	
	public void afterUpdateBlock(BlockPos pos)
	{
		if(!isEnabled())
			return;
		
		// Liquid starts flowing -> probably a new chunk
		FluidState fluidState = BlockUtils.getState(pos).getFluidState();
		if(!isFlowing(fluidState))
			return;
		
		ChunkPos chunkPos = new ChunkPos(pos);
		if(newChunks.contains(chunkPos) || oldChunks.contains(chunkPos))
			return;
		
		newChunks.add(chunkPos);
		newChunkReasons.add(pos.toImmutable());
		if(logChunks.isChecked())
			System.out.println("new chunk (fluid) at " + chunkPos);
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		RegionPos region = RenderUtils.getCameraRegion();
		if(!region.equals(lastRegion))
		{
			onUpdate();
			lastRegion = region;
		}
		
		renderer.render(matrixStack, partialTicks);
	}
}
