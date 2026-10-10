/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CactusCollisionShapeListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"反仙人掌", "NoCactus", "anti cactus", "no cactus"})
public final class AntiCactusHack extends Hack
	implements CactusCollisionShapeListener, PreMotionListener
{
	private final CheckboxSetting preventCactusTopDamage = new CheckboxSetting(
		"仙人掌顶部伤害保护",
		"description.wurst.setting.anticactus.prevent_cactus-top_damage", true);
	
	public AntiCactusHack()
	{
		super("反仙人掌");
		setCategory(Category.BLOCKS);
		addSetting(preventCactusTopDamage);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(CactusCollisionShapeListener.class, this);
		EVENTS.add(PreMotionListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(CactusCollisionShapeListener.class, this);
		EVENTS.remove(PreMotionListener.class, this);
	}
	
	@Override
	public void onCactusCollisionShape(CactusCollisionShapeEvent event)
	{
		if(preventCactusTopDamage.isChecked())
			event.setCollisionShape(VoxelShapes.fullCube());
	}
	
	@Override
	public void onPreMotion()
	{
		if(!preventCactusTopDamage.isChecked() || MC.player == null
			|| MC.world == null || MC.player.networkHandler == null
			|| MC.player.getVelocity().y > 0 || !isAboveCactus())
			return;
		
		Vec3d pos = MC.player.getPos();
		MC.player.networkHandler.getConnection()
			.send(new PositionAndOnGround(pos.x, pos.y, pos.z, true));
		MC.player.networkHandler.getConnection()
			.send(new PositionAndOnGround(pos.x, pos.y + 1e-6, pos.z, false));
		MC.player.networkHandler.getConnection()
			.send(new PositionAndOnGround(pos.x, pos.y, pos.z, true));
	}
	
	private boolean isAboveCactus()
	{
		Box below = MC.player.getBoundingBox().offset(0, -2, 0);
		BlockPos min = BlockPos.ofFloored(below.minX, below.minY, below.minZ);
		BlockPos max =
			BlockPos.ofFloored(below.maxX, MC.player.getY(), below.maxZ);
		
		return BlockPos.stream(min, max)
			.anyMatch(pos -> MC.world.getBlockState(pos).isOf(Blocks.CACTUS)
				&& MC.player.getY() >= pos.getY() + 1);
	}
}
