/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

public final class RotationFaker
{
	private boolean shouldFakeRotation;
	private boolean didFakeRotation;
	private float serverYaw;
	private float serverPitch;
	private float realYaw;
	private float realPitch;
	
	public void applyIfNeeded()
	{
		if(!shouldFakeRotation || !WurstClient.INSTANCE.isEnabled())
			return;
		
		ClientPlayerEntity player = WurstClient.MC.player;
		realYaw = player.getYaw();
		realPitch = player.getPitch();
		player.setYaw(serverYaw);
		player.setPitch(serverPitch);
		didFakeRotation = true;
	}
	
	public void restoreIfNeeded()
	{
		if(!didFakeRotation)
			return;
		
		ClientPlayerEntity player = WurstClient.MC.player;
		player.setYaw(realYaw);
		player.setPitch(realPitch);
		shouldFakeRotation = false;
		didFakeRotation = false;
	}
	
	public void faceVectorPacket(Vec3d vec)
	{
		Rotation needed = RotationUtils.getNeededRotations(vec);
		ClientPlayerEntity player = WurstClient.MC.player;
		
		shouldFakeRotation = true;
		serverYaw =
			RotationUtils.limitAngleChange(player.getYaw(), needed.yaw());
		serverPitch = needed.pitch();
	}
	
	public void faceVectorClient(Vec3d vec)
	{
		Rotation needed = RotationUtils.getNeededRotations(vec);
		
		ClientPlayerEntity player = WurstClient.MC.player;
		player.setYaw(
			RotationUtils.limitAngleChange(player.getYaw(), needed.yaw()));
		player.setPitch(needed.pitch());
	}
	
	public void faceVectorClientIgnorePitch(Vec3d vec)
	{
		Rotation needed = RotationUtils.getNeededRotations(vec);
		
		ClientPlayerEntity player = WurstClient.MC.player;
		player.setYaw(
			RotationUtils.limitAngleChange(player.getYaw(), needed.yaw()));
		player.setPitch(0);
	}
	
	public float getServerYaw()
	{
		return shouldFakeRotation ? serverYaw : WurstClient.MC.player.getYaw();
	}
	
	public float getServerPitch()
	{
		return shouldFakeRotation ? serverPitch
			: WurstClient.MC.player.getPitch();
	}
}
