/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.noclip.NoClipRenderer;
import net.wurstclient.hacks.noclip.NoClipRenderer.JumpToRender;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"穿墙", "no clip"})
public final class NoClipHack extends Hack
	implements UpdateListener, RenderListener
{
	// Based on the server's maximum accepted player movement distance.
	public static final double MAX_DISTANCE_WITHOUT_ELYTRA = Math.sqrt(500);
	public static final double MAX_DISTANCE = Math.sqrt(1500);
	
	private final CheckboxSetting showPassableBlocks =
		new CheckboxSetting("显示可穿越方块",
			"description.wurst.setting.noclip.show_passable_blocks", true);
	
	private final Set<JumpToRender> jumpsToRender = new LinkedHashSet<>();
	
	private Vec3d direction = Vec3d.ZERO;
	private int preJumpTicks;
	private int postJumpTicks;
	private boolean needsButtonRelease;
	
	public NoClipHack()
	{
		super("穿墙");
		setCategory(Category.MOVEMENT);
		addSetting(showPassableBlocks);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		jumpsToRender.clear();
		direction = Vec3d.ZERO;
		preJumpTicks = 0;
		postJumpTicks = 0;
		needsButtonRelease = false;
	}
	
	@Override
	public void onUpdate()
	{
		jumpsToRender.clear();
		if(showPassableBlocks.isChecked()
			&& MC.player.raycast(64, 0, false) instanceof BlockHitResult hit)
			jumpsToRender
				.addAll(NoClipRenderer.findJumpsNear(hit.getBlockPos()));
		
		double forward = (MC.options.forwardKey.isPressed() ? 1 : 0)
			- (MC.options.backKey.isPressed() ? 1 : 0);
		double left = (MC.options.leftKey.isPressed() ? 1 : 0)
			- (MC.options.rightKey.isPressed() ? 1 : 0);
		double up = (MC.options.jumpKey.isPressed() ? 1 : 0)
			- (MC.options.sneakKey.isPressed() ? 1 : 0);
		
		direction = new Vec3d(left, up, forward).normalize()
			.rotateY(-MC.player.getYaw() * MathHelper.RADIANS_PER_DEGREE);
		if(direction.lengthSquared() == 0)
			needsButtonRelease = false;
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(showPassableBlocks.isChecked())
			NoClipRenderer.renderJumps(matrixStack, jumpsToRender);
	}
	
	public boolean shouldSendPosition()
	{
		if(!isEnabled() || MC.player == null || MC.world == null)
			return true;
		
		if(postJumpTicks > 0)
		{
			postJumpTicks--;
			return false;
		}
		
		ClientPlayerEntity player = MC.player;
		if(!player.isAlive() || player.hasVehicle() || player.isSpectator()
			|| MC.currentScreen != null
			|| WURST.getHax().freecamHack.isMovingCamera()
			|| MC.world.isSpaceEmpty(player.getBoundingBox().contract(1e-5))
			|| needsButtonRelease || direction.lengthSquared() == 0)
		{
			preJumpTicks = 0;
			return true;
		}
		
		preJumpTicks++;
		if(preJumpTicks <= 2)
			return false;
		preJumpTicks = 0;
		
		needsButtonRelease = true;
		boolean canUseElytra = !player.isFallFlying()
			&& !player.isTouchingWater() && !player.getAbilities().flying
			&& !player.hasStatusEffect(StatusEffects.LEVITATION)
			&& isUsableElytra(player.getEquippedStack(EquipmentSlot.CHEST));
		
		Vec3d destination = findDestination(direction, canUseElytra);
		if(destination == null)
			return true;
		
		for(int i = 0; i < 4; i++)
			player.networkHandler.sendPacket(new OnGroundOnly(false));
		
		if(canUseElytra)
			player.networkHandler.sendPacket(new ClientCommandC2SPacket(player,
				ClientCommandC2SPacket.Mode.START_FALL_FLYING));
			
		// Let vanilla send the final position packet and update its position
		// tracking fields.
		player.setPosition(destination);
		postJumpTicks = 2;
		return true;
	}
	
	private boolean isUsableElytra(ItemStack chest)
	{
		return chest.isOf(Items.ELYTRA) && ElytraItem.isUsable(chest);
	}
	
	private Vec3d findDestination(Vec3d direction, boolean canUseElytra)
	{
		double maxDistance =
			canUseElytra ? MAX_DISTANCE : MAX_DISTANCE_WITHOUT_ELYTRA;
		double distance = 0;
		
		while(distance < maxDistance)
		{
			distance = Math.min(distance + 0.25, maxDistance);
			Vec3d offset = direction.multiply(distance);
			Box box = MC.player.getBoundingBox().offset(offset);
			
			if(!BlockPos.stream(box.contract(1e-7))
				.allMatch(MC.world::isChunkLoaded))
				return null;
			
			if(!MC.world.isSpaceEmpty(box)
				|| MC.world.getOtherEntities(MC.player, box).stream()
					.anyMatch(Entity::isCollidable))
				continue;
			
			return MC.player.getPos().add(offset);
		}
		
		return null;
	}
}
