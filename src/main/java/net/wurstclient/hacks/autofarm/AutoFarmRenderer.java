/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autofarm;

import java.util.List;
import java.util.Set;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.Setting;
import net.wurstclient.util.EasyVertexBuffer;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;

public final class AutoFarmRenderer
{
	private static final Box BLOCK_BOX =
		new Box(BlockPos.ORIGIN).contract(1 / 16.0);
	private static final Box NODE_BOX = new Box(BlockPos.ORIGIN).contract(0.25);
	
	public final CheckboxSetting drawPlants =
		new CheckboxSetting("显示已识别作物", true);
	public final CheckboxSetting drawBlocksToHarvest =
		new CheckboxSetting("显示待收获方块", true);
	public final CheckboxSetting drawBlocksToReplant =
		new CheckboxSetting("显示待补种位置", true);
	
	private EasyVertexBuffer vertexBuffer;
	private RegionPos region;
	
	public java.util.stream.Stream<Setting> getSettings()
	{
		return java.util.stream.Stream.of(drawPlants, drawBlocksToHarvest,
			drawBlocksToReplant);
	}
	
	public void reset()
	{
		if(vertexBuffer != null)
		{
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
	
	public void render(MatrixStack matrixStack)
	{
		if(vertexBuffer == null || region == null)
			return;
		
		matrixStack.push();
		RenderUtils.applyRegionalRenderOffset(matrixStack, region);
		
		vertexBuffer.draw(matrixStack, WurstRenderLayers.ESP_LINES);
		
		matrixStack.pop();
	}
	
	public void updateVertexBuffers(List<BlockPos> blocksToHarvest,
		Set<BlockPos> plants, List<BlockPos> blocksToReplant)
	{
		reset();
		
		if(blocksToHarvest.isEmpty() && plants.isEmpty()
			&& blocksToReplant.isEmpty())
			return;
		
		vertexBuffer = EasyVertexBuffer.createAndUpload(DrawMode.LINES,
			VertexFormats.LINES, buffer -> buildBuffer(buffer, blocksToHarvest,
				plants, blocksToReplant));
	}
	
	private void buildBuffer(VertexConsumer buffer,
		List<BlockPos> blocksToHarvest, Set<BlockPos> plants,
		List<BlockPos> blocksToReplant)
	{
		region = RenderUtils.getCameraRegion();
		Vec3d regionOffset = region.negate().toVec3d();
		
		if(drawBlocksToHarvest.isChecked())
		{
			for(BlockPos pos : blocksToHarvest)
			{
				Box box = BLOCK_BOX.offset(pos).offset(regionOffset);
				RenderUtils.drawOutlinedBox(buffer, box, 0x8000FF00);
			}
		}
		
		if(drawPlants.isChecked())
		{
			for(BlockPos pos : plants)
			{
				Box renderNode = NODE_BOX.offset(pos).offset(regionOffset);
				RenderUtils.drawNode(buffer, renderNode, 0x8000FFFF);
			}
		}
		
		if(drawBlocksToReplant.isChecked())
		{
			for(BlockPos pos : blocksToReplant)
			{
				Box renderBox = BLOCK_BOX.offset(pos).offset(regionOffset);
				RenderUtils.drawOutlinedBox(buffer, renderBox, 0x80FF0000);
			}
		}
	}
}
