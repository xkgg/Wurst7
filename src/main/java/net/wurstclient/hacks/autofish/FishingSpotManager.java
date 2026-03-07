/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autofish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

public final class FishingSpotManager
{
	private static final MinecraftClient MC = WurstClient.MC;
	
	private final CheckboxSetting mcmmoMode = new CheckboxSetting("mcMMO模式",
		"如果启用，自动钓鱼将在两个不同的钓鱼点之间循环"
			+ " 以绕过mcMMO的过度钓鱼机制。\n\n"
			+ "如果禁用此选项，所有其他mcMMO设置将无效。",
		false);
	
	private final SliderSetting mcmmoRange = new SliderSetting("mcMMO范围",
		"mcMMO的MoveRange配置选项的值。这是两个钓鱼点之间需要的最小"
			+ " 距离，以避免过度钓鱼。\n\n"
			+ "mcMMO只关心浮标的位置，所以你不需要移动你的角色，除非存在其他防挂机插件。",
		3, 1, 50, 1, ValueDisplay.INTEGER.withSuffix(" 方块"));
	
	private final CheckboxSetting mcmmoRangeBug = 
		new CheckboxSetting("mcMMO范围bug",
			"在撰写本文时，mcMMO的范围计算中存在一个bug，" 
				+ " 这意味着默认的3方块范围实际上只有2方块。\n\n"
				+ "如果他们修复了这个问题，请取消勾选此框。",
		true);
	
	private final SliderSetting mcmmoLimit = new SliderSetting("mcMMO限制",
		"mcMMO的OverFishLimit配置选项的值。过度钓鱼从"
			+ " 这个值开始，所以你实际上只能从同一个点捕获(limit - 1)条鱼。",
		10, 2, 1000, 1, ValueDisplay.INTEGER);
	
	private final ArrayList<FishingSpot> fishingSpots = new ArrayList<>();
	private FishingSpot lastSpot;
	private FishingSpot nextSpot;
	private PositionAndRotation castPosRot;
	private int fishCaughtAtLastSpot;
	private boolean spot1MsgShown;
	private boolean spot2MsgShown;
	private boolean setupDoneMsgShown;
	
	/**
	 * Changes the player's fishing spot if necessary.
	 *
	 * @return true if it's OK to cast the fishing rod
	 */
	public boolean onCast()
	{
		castPosRot = new PositionAndRotation(MC.player);
		if(!mcmmoMode.isChecked())
			return true;
		
		// 允许第一次投掷，告诉用户等待
		if(lastSpot == null)
		{
			if(spot1MsgShown)
				return true;
			
			ChatUtils.message("正在启动自动钓鱼mcMMO模式。");
			ChatUtils.message("请等待第一个钓鱼点被记录。");
			spot1MsgShown = true;
			return true;
		}
		spot1MsgShown = false;
		
		// 必要时设置下一个点，如果需要新点则指导用户
		if(nextSpot == null && (nextSpot = chooseNextSpot()) == null)
		{
			if(spot2MsgShown)
				return false;
			
			ChatUtils
				.message("自动钓鱼mcMMO模式需要另一个钓鱼点。");
			ChatUtils.message("移动你的相机（或玩家，如果必要）"
				+ " 使浮标落在红色框外，然后投掷鱼竿。");
			spot2MsgShown = true;
			setupDoneMsgShown = false;
			return false;
		}
		spot2MsgShown = false;
		
		// 确认设置完成
		if(!setupDoneMsgShown)
		{
			ChatUtils.message("设置完成！自动钓鱼现在将自动运行"
				+ " 并根据需要在钓鱼点之间切换。");
			setupDoneMsgShown = true;
		}
		
		// automatically move to next spot when limit is reached
		if(fishCaughtAtLastSpot >= mcmmoLimit.getValueI() - 1)
		{
			moveToNextSpot();
			return false;
		}
		
		return true;
	}
	
	private void moveToNextSpot()
	{
		IKeyBinding forwardKey = IKeyBinding.get(MC.options.forwardKey);
		IKeyBinding jumpKey = IKeyBinding.get(MC.options.jumpKey);
		
		PositionAndRotation nextPosRot = nextSpot.input();
		forwardKey.resetPressedState();
		jumpKey.resetPressedState();
		
		// match position
		Vec3d nextPos = nextPosRot.pos();
		double distance = nextPos.distanceTo(castPosRot.pos());
		if(distance > 0.1)
		{
			// face next spot
			Rotation needed =
				RotationUtils.getNeededRotations(nextPos).withPitch(0);
			if(!RotationUtils.isAlreadyFacing(needed))
			{
				RotationUtils.slowlyTurnTowards(needed, 5)
					.applyToClientPlayer();
				return;
			}
			
			// jump if necessary
			jumpKey.setPressed(
				MC.player.isTouchingWater() || MC.player.horizontalCollision);
			
			// walk or teleport depending on distance
			if(distance < 0.2)
				MC.player.setPosition(nextPos.x, nextPos.y, nextPos.z);
			else if(distance > 0.7 || MC.player.age % 10 == 0)
				forwardKey.setPressed(true);
			return;
		}
		
		// match rotation
		Rotation nextRot = nextPosRot.rotation();
		if(!RotationUtils.isAlreadyFacing(nextRot))
		{
			RotationUtils.slowlyTurnTowards(nextRot, 5).applyToClientPlayer();
			return;
		}
		
		// update spot and reset counter
		lastSpot = nextSpot;
		nextSpot = null;
		fishCaughtAtLastSpot = 0;
	}
	
	public void onBite(FishingBobberEntity bobber)
	{
		boolean samePlayerInput = lastSpot != null
			&& lastSpot.input().isNearlyIdenticalTo(castPosRot);
		boolean sameBobberPos = lastSpot != null
			&& isInRange(lastSpot.bobberPos(), bobber.getPos());
		
		// update counter based on bobber position
		if(sameBobberPos)
			fishCaughtAtLastSpot++;
		else
			fishCaughtAtLastSpot = 1;
		
		// register new fishing spot if input changed
		if(!samePlayerInput)
		{
			lastSpot = new FishingSpot(castPosRot, bobber);
			fishingSpots.add(lastSpot);
			return;
		}
		
		// update last spot if same input led to different bobber position
		if(!sameBobberPos)
		{
			FishingSpot updatedSpot = new FishingSpot(lastSpot.input(), bobber);
			fishingSpots.remove(lastSpot);
			fishingSpots.add(updatedSpot);
			lastSpot = updatedSpot;
		}
	}
	
	public void reset()
	{
		fishingSpots.clear();
		lastSpot = null;
		nextSpot = null;
		castPosRot = null;
		fishCaughtAtLastSpot = 0;
		spot1MsgShown = false;
		spot2MsgShown = false;
		setupDoneMsgShown = false;
	}
	
	private FishingSpot chooseNextSpot()
	{
		return fishingSpots.stream().filter(spot -> spot != lastSpot)
			.filter(spot -> !isInRange(spot.bobberPos(), lastSpot.bobberPos()))
			.min(Comparator.comparingDouble(
				spot -> spot.input().differenceTo(lastSpot.input())))
			.orElse(null);
	}
	
	private boolean isInRange(Vec3d pos1, Vec3d pos2)
	{
		double dy = Math.abs(pos1.y - pos2.y);
		if(dy > 2)
			return false;
		
		double dx = Math.abs(pos1.x - pos2.x);
		double dz = Math.abs(pos1.z - pos2.z);
		return Math.max(dx, dz) <= getRange();
	}
	
	public int getRange()
	{
		// rounded down to the nearest even number
		if(mcmmoRangeBug.isChecked())
			return mcmmoRange.getValueI() / 2 * 2;
		
		return mcmmoRange.getValueI();
	}
	
	public FishingSpot getLastSpot()
	{
		return lastSpot;
	}
	
	public boolean isSetupDone()
	{
		return lastSpot != null && nextSpot != null;
	}
	
	public boolean isMcmmoMode()
	{
		return mcmmoMode.isChecked();
	}
	
	public Stream<Setting> getSettings()
	{
		return Stream.of(mcmmoMode, mcmmoRange, mcmmoRangeBug, mcmmoLimit);
	}
	
	public List<FishingSpot> getFishingSpots()
	{
		return Collections.unmodifiableList(fishingSpots);
	}
}
