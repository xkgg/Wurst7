/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.noclip;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.hacks.NoClipHack;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;

public enum NoClipRenderer
{
	;
	
	private static final MinecraftClient MC = WurstClient.MC;
	
	public static Set<JumpToRender> findJumpsNear(BlockPos center)
	{
		Set<JumpToRender> jumps = new LinkedHashSet<>();
		for(BlockPos pos : BlockUtils.getAllInBox(center, 3))
		{
			if(pos.getManhattanDistance(center) > 3)
				continue;
			
			if(!MC.world.isChunkLoaded(pos)
				|| !MC.world.getBlockState(pos).isSolidBlock(MC.world, pos))
				continue;
			
			for(Direction direction : Direction.values())
			{
				JumpToRender jump = findJump(pos, direction);
				if(jump != null)
					jumps.add(jump);
			}
		}
		
		return jumps;
	}
	
	private static JumpToRender findJump(BlockPos entryPos, Direction direction)
	{
		BlockPos preEntryPos = entryPos.offset(direction.getOpposite());
		if(!MC.world.isChunkLoaded(preEntryPos) || MC.world
			.getBlockState(preEntryPos).isSolidBlock(MC.world, preEntryPos))
			return null;
		
		for(int blocks = 1; blocks + 1 <= NoClipHack.MAX_DISTANCE; blocks++)
		{
			BlockPos exitPos = entryPos.offset(direction, blocks - 1);
			BlockPos postExitPos = exitPos.offset(direction);
			if(!MC.world.isChunkLoaded(postExitPos))
				return null;
			if(MC.world.getBlockState(postExitPos).isSolidBlock(MC.world,
				postExitPos))
				continue;
			
			BlockPos minPos = direction.getDirection() == AxisDirection.POSITIVE
				? entryPos : exitPos;
			return new JumpToRender(minPos, direction.getAxis(), blocks,
				blocks + 1 > NoClipHack.MAX_DISTANCE_WITHOUT_ELYTRA);
		}
		
		return null;
	}
	
	public static void renderJumps(MatrixStack matrixStack,
		Collection<JumpToRender> jumps)
	{
		if(jumps.isEmpty())
			return;
		
		VertexConsumerProvider.Immediate vcp = RenderUtils.getVCP();
		var buffer = vcp.getBuffer(WurstRenderLayers.ESP_LINES);
		Vec3d cameraPos = RenderUtils.getCameraPos();
		for(JumpToRender jump : jumps)
			drawJump(matrixStack, buffer, jump, cameraPos);
		vcp.draw(WurstRenderLayers.ESP_LINES);
	}
	
	private static void drawJump(MatrixStack matrixStack,
		net.minecraft.client.render.VertexConsumer buffer, JumpToRender jump,
		Vec3d cameraPos)
	{
		int color = jump.needsElytra() ? 0x8000FFFF : 0x8000FF00;
		Axis axis = jump.axis();
		BlockPos pos = jump.minPos();
		double coordinate = switch(axis)
		{
			case X -> pos.getX();
			case Y -> pos.getY();
			case Z -> pos.getZ();
		};
		Vec3d center = Vec3d.ofCenter(pos);
		Vec3d minFaceCenter =
			center.withAxis(axis, coordinate).subtract(cameraPos);
		Vec3d maxFaceCenter = center.withAxis(axis, coordinate + jump.blocks())
			.subtract(cameraPos);
		Vec3d halfWidth =
			(axis == Axis.X ? new Vec3d(0, 0, 1) : new Vec3d(1, 0, 0))
				.multiply(7 / 16.0);
		Vec3d halfHeight =
			(axis == Axis.Y ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0))
				.multiply(7 / 16.0);
		
		for(Vec3d faceCenter : new Vec3d[]{minFaceCenter, maxFaceCenter})
		{
			Vec3d a = faceCenter.subtract(halfWidth).subtract(halfHeight);
			Vec3d b = faceCenter.add(halfWidth).subtract(halfHeight);
			Vec3d c = faceCenter.add(halfWidth).add(halfHeight);
			Vec3d d = faceCenter.subtract(halfWidth).add(halfHeight);
			RenderUtils.drawLine(matrixStack, buffer, a, b, color);
			RenderUtils.drawLine(matrixStack, buffer, b, c, color);
			RenderUtils.drawLine(matrixStack, buffer, c, d, color);
			RenderUtils.drawLine(matrixStack, buffer, d, a, color);
		}
	}
	
	public record JumpToRender(BlockPos minPos, Axis axis, int blocks,
		boolean needsElytra)
	{}
}
