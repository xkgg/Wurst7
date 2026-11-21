/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import org.joml.Quaternionf;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;

public record Rotation(float yaw, float pitch)
{
	private static final MinecraftClient MC = WurstClient.MC;
	
	public void applyToClientPlayer()
	{
		float adjustedYaw =
			RotationUtils.limitAngleChange(MC.player.getYaw(), yaw);
		MC.player.setYaw(adjustedYaw);
		MC.player.setPitch(pitch);
	}
	
	public void sendPlayerLookPacket()
	{
		sendPlayerLookPacket(MC.player.isOnGround());
	}
	
	public void sendPlayerLookPacket(boolean onGround)
	{
		MC.player.networkHandler.sendPacket(
			new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, onGround));
	}
	
	public double getAngleTo(Rotation other)
	{
		Vec3d lookVec = toLookVec();
		Vec3d otherLookVec = other.toLookVec();
		return Math
			.toDegrees(Math.atan2(lookVec.crossProduct(otherLookVec).length(),
				lookVec.dotProduct(otherLookVec)));
	}
	
	public double getYawPitchDistanceTo(Rotation other)
	{
		float yaw1 = MathHelper.wrapDegrees(yaw);
		float yaw2 = MathHelper.wrapDegrees(other.yaw);
		float diffYaw = MathHelper.wrapDegrees(yaw1 - yaw2);
		
		float pitch1 = MathHelper.wrapDegrees(pitch);
		float pitch2 = MathHelper.wrapDegrees(other.pitch);
		float diffPitch = MathHelper.wrapDegrees(pitch1 - pitch2);
		
		return Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
	}
	
	public Rotation slowlyTurnTowards(Rotation end, float maxChange)
	{
		float yawChange = Math.abs(MathHelper.wrapDegrees(end.yaw - yaw));
		float pitchChange = Math.abs(MathHelper.wrapDegrees(end.pitch - pitch));
		
		float maxChangeYaw = pitchChange == 0 ? maxChange
			: Math.min(maxChange, maxChange * yawChange / pitchChange);
		float maxChangePitch = yawChange == 0 ? maxChange
			: Math.min(maxChange, maxChange * pitchChange / yawChange);
		
		float nextYaw =
			RotationUtils.limitAngleChange(yaw, end.yaw, maxChangeYaw);
		float nextPitch =
			RotationUtils.limitAngleChange(pitch, end.pitch, maxChangePitch);
		return new Rotation(nextYaw, nextPitch);
	}
	
	public Rotation add(float yawOffset, float pitchOffset)
	{
		return new Rotation(yaw + yawOffset, pitch + pitchOffset);
	}
	
	public Rotation clampPitch()
	{
		return withPitch(MathHelper.clamp(pitch, -90, 90));
	}
	
	public Rotation withYaw(float yaw)
	{
		return new Rotation(yaw, pitch);
	}
	
	public Rotation withPitch(float pitch)
	{
		return new Rotation(yaw, pitch);
	}
	
	public Vec3d toLookVec()
	{
		double yawRad = Math.toRadians(MathHelper.wrapDegrees(yaw));
		double pitchRad = Math.toRadians(MathHelper.wrapDegrees(pitch));
		double cosYaw = Math.cos(yawRad);
		double sinYaw = Math.sin(yawRad);
		double cosPitch = Math.cos(pitchRad);
		double sinPitch = Math.sin(pitchRad);
		
		return new Vec3d(-sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
	}
	
	public Quaternionf toQuaternion()
	{
		double yawRad = -Math.toRadians(MathHelper.wrapDegrees(yaw));
		double pitchRad = Math.toRadians(MathHelper.wrapDegrees(pitch));
		
		double sinYaw = Math.sin(yawRad / 2);
		double cosYaw = Math.cos(yawRad / 2);
		double sinPitch = Math.sin(pitchRad / 2);
		double cosPitch = Math.cos(pitchRad / 2);
		
		float x = (float)(sinPitch * cosYaw);
		float y = (float)(cosPitch * sinYaw);
		float z = (float)(-sinPitch * sinYaw);
		float w = (float)(cosPitch * cosYaw);
		
		return new Quaternionf(x, y, z, w);
	}
	
	public static Rotation lerp(float partialTicks, Rotation start,
		Rotation end)
	{
		return new Rotation(MathHelper.lerp(partialTicks, start.yaw, end.yaw),
			MathHelper.lerp(partialTicks, start.pitch, end.pitch));
	}
	
	public static Rotation wrapped(float yaw, float pitch)
	{
		return new Rotation(MathHelper.wrapDegrees(yaw),
			MathHelper.wrapDegrees(pitch));
	}
}
