/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.MathUtils;

public final class DamageCmd extends Command
{
	public DamageCmd()
	{
		super("damage", "应用给定的伤害量。",
			".damage <数量>", "注意: 数量以半心为单位。",
			"示例: .damage 7 (应用3.5心伤害)",
			"要应用更多伤害，请多次运行此命令。");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length == 0)
			throw new CmdSyntaxError();
		
		if(MC.player.getAbilities().creativeMode)
			throw new CmdError("无法在创造模式中造成伤害。");
		
		int amount = parseAmount(args[0]);
		applyDamage(amount);
	}
	
	private int parseAmount(String dmgString) throws CmdSyntaxError
	{
		if(!MathUtils.isInteger(dmgString))
			throw new CmdSyntaxError("不是数字: " + dmgString);
		
		int dmg = Integer.parseInt(dmgString);
		
		if(dmg < 1)
			throw new CmdSyntaxError("最小数量为1。");
		
		if(dmg > 7)
			throw new CmdSyntaxError("最大数量为7。");
		
		return dmg;
	}
	
	private void applyDamage(int amount)
	{
		Vec3d pos = MC.player.getPos();
		
		for(int i = 0; i < 80; i++)
		{
			sendPosition(pos.x, pos.y + amount + 2.1, pos.z, false);
			sendPosition(pos.x, pos.y + 0.05, pos.z, false);
		}
		
		sendPosition(pos.x, pos.y, pos.z, true);
	}
	
	private void sendPosition(double x, double y, double z, boolean onGround)
	{
		MC.player.networkHandler.sendPacket(
			new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
	}
}
