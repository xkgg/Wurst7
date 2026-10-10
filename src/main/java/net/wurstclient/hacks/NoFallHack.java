/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"无跌落伤害", "no fall"})
public final class NoFallHack extends Hack implements UpdateListener
{
	private final CheckboxSetting allowElytra = new CheckboxSetting("允许鞘翅",
		"description.wurst.setting.nofall.allow_elytra", false);
	
	private final SliderSetting minFallDistance = new SliderSetting("最小坠落距离",
		"description.wurst.setting.nofall.min_fall_distance", 1, 0, 10, 0.1,
		ValueDisplay.DECIMAL.withSuffix("米").withLabel(0, "关闭"));
	
	private final SliderSetting minFallDistanceElytra = new SliderSetting(
		"鞘翅最小坠落距离", "description.wurst.setting.nofall.min_elytra_fall_distance",
		2, 0, 10, 0.1, ValueDisplay.DECIMAL.withSuffix("米").withLabel(0, "关闭"));
	
	public NoFallHack()
	{
		super("无跌落伤害");
		setCategory(Category.MOVEMENT);
		addSetting(allowElytra);
		addSetting(minFallDistance);
		addSetting(minFallDistanceElytra);
	}
	
	@Override
	public String getRenderName()
	{
		if(MC.player != null && isPaused())
			return getName() + " (paused)";
		
		return getName();
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().antiHungerHack.setEnabled(false);
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		if(isPaused())
			return;
		
		// send packet to stop fall damage
		MC.player.networkHandler.sendPacket(new OnGroundOnly(true));
	}
	
	private boolean isPaused()
	{
		// do nothing in creative mode, since there is no fall damage anyway
		ClientPlayerEntity player = MC.player;
		if(player.isCreative())
			return true;
		
		// pause when flying with elytra, unless allowed
		boolean fallFlying = player.isFallFlying();
		if(fallFlying && !allowElytra.isChecked())
			return true;
			
		// ignore small falls that can't cause damage, unless CreativeFlight
		// is enabled in survival mode
		boolean creativeFlying = WURST.getHax().creativeFlightHack.isEnabled()
			&& player.getAbilities().flying;
		if(!creativeFlying && player.fallDistance <= (fallFlying
			? minFallDistanceElytra.getValue() : minFallDistance.getValue()))
			return true;
		
		// attempt to fix elytra weirdness, if allowed
		if(fallFlying && player.isSneaking()
			&& !isFallingFastEnoughToCauseDamage(player))
			return true;
		
		return false;
	}
	
	private boolean isFallingFastEnoughToCauseDamage(ClientPlayerEntity player)
	{
		return player.getVelocity().y < -0.5;
	}
}
