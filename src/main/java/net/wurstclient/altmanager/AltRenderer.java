/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.altmanager;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.wurstclient.WurstClient;

public final class AltRenderer
{
	private static final ExecutorService BACKGROUND_THREAD =
		Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "Wurst Alt skins");
			thread.setDaemon(true);
			return thread;
		});
	
	private static final HashMap<String, Identifier> loadedSkins =
		new HashMap<>();
	private static final ConcurrentHashMap<String, Identifier> onlineSkins =
		new ConcurrentHashMap<>();
	
	private static Identifier getSkinTexture(String name)
	{
		if(name.isEmpty())
			name = "Steve";
		
		Identifier texture = loadedSkins.get(name);
		if(texture == null)
		{
			UUID uuid = Uuids.getOfflinePlayerUuid(name);
			texture = DefaultSkinHelper.getTexture(uuid);
			loadedSkins.put(name, texture);
			queueOnlineSkinLoading(name);
		}
		
		return onlineSkins.getOrDefault(name, texture);
	}
	
	private static void queueOnlineSkinLoading(String name)
	{
		CompletableFuture.supplyAsync(() -> {
			UUID uuid = SkinStealer.getUUIDOrNull(name);
			if(uuid == null)
				return null;
			
			return WurstClient.MC.getSessionService()
				.fillProfileProperties(new GameProfile(uuid, name), false);
		}, BACKGROUND_THREAD).thenAccept(profile -> {
			if(profile == null)
				return;
			
			WurstClient.MC.execute(() -> WurstClient.MC.getSkinProvider()
				.loadSkin(profile, (type, skin, profileTexture) -> {
					if(type == Type.SKIN)
						onlineSkins.put(name, skin);
				}, false));
		}).exceptionally(error -> {
			System.err.println("[Wurst] Failed to load skin for " + name);
			error.printStackTrace();
			return null;
		});
	}
	
	public static void drawAltFace(DrawContext context, String name, int x,
		int y, int w, int h, boolean selected)
	{
		try
		{
			Identifier texture = getSkinTexture(name);
			
			if(selected)
				RenderSystem.setShaderColor(1, 1, 1, 1);
			else
				RenderSystem.setShaderColor(0.9F, 0.9F, 0.9F, 1);
			
			// Face
			int fw = 192;
			int fh = 192;
			float u = 24;
			float v = 24;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Hat
			fw = 192;
			fh = 192;
			u = 120;
			v = 24;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			RenderSystem.setShaderColor(1, 1, 1, 1);
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void drawAltBody(DrawContext context, String name, int x,
		int y, int width, int height)
	{
		try
		{
			Identifier texture = getSkinTexture(name);
			RenderSystem.setShaderColor(1, 1, 1, 1);
			
			boolean slim = DefaultSkinHelper
				.getModel(Uuids.getOfflinePlayerUuid(name)).equals("slim");
			
			// Face
			x = x + width / 4;
			y = y + 0;
			int w = width / 2;
			int h = height / 4;
			int fw = height * 2;
			int fh = height * 2;
			float u = height / 4;
			float v = height / 4;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Hat
			x = x + 0;
			y = y + 0;
			w = width / 2;
			h = height / 4;
			u = height / 4 * 5;
			v = height / 4;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Chest
			x = x + 0;
			y = y + height / 4;
			w = width / 2;
			h = height / 8 * 3;
			u = height / 4 * 2.5F;
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Jacket
			x = x + 0;
			y = y + 0;
			w = width / 2;
			h = height / 8 * 3;
			u = height / 4 * 2.5F;
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Left Arm
			x = x - width / 16 * (slim ? 3 : 4);
			y = y + (slim ? height / 32 : 0);
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * 5.5F;
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Left Sleeve
			x = x + 0;
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * 5.5F;
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Right Arm
			x = x + width / 16 * (slim ? 11 : 12);
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * 5.5F;
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Right Sleeve
			x = x + 0;
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * 5.5F;
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Left Leg
			x = x - width / 2;
			y = y + height / 32 * (slim ? 11 : 12);
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 0.5F;
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Left Pants
			x = x + 0;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 0.5F;
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Right Leg
			x = x + width / 4;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 0.5F;
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Right Pants
			x = x + 0;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 0.5F;
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void drawAltBack(DrawContext context, String name, int x,
		int y, int width, int height)
	{
		try
		{
			Identifier texture = getSkinTexture(name);
			RenderSystem.setShaderColor(1, 1, 1, 1);
			
			boolean slim = DefaultSkinHelper
				.getModel(Uuids.getOfflinePlayerUuid(name)).equals("slim");
			
			// Face
			x = x + width / 4;
			y = y + 0;
			int w = width / 2;
			int h = height / 4;
			int fw = height * 2;
			int fh = height * 2;
			float u = height / 4 * 3;
			float v = height / 4;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Hat
			x = x + 0;
			y = y + 0;
			w = width / 2;
			h = height / 4;
			u = height / 4 * 7;
			v = height / 4;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Chest
			x = x + 0;
			y = y + height / 4;
			w = width / 2;
			h = height / 8 * 3;
			u = height / 4 * 4;
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Jacket
			x = x + 0;
			y = y + 0;
			w = width / 2;
			h = height / 8 * 3;
			u = height / 4 * 4;
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Left Arm
			x = x - width / 16 * (slim ? 3 : 4);
			y = y + (slim ? height / 32 : 0);
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * (slim ? 6.375F : 6.5F);
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Left Sleeve
			x = x + 0;
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * (slim ? 6.375F : 6.5F);
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Right Arm
			x = x + width / 16 * (slim ? 11 : 12);
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * (slim ? 6.375F : 6.5F);
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Right Sleeve
			x = x + 0;
			y = y + 0;
			w = width / 16 * (slim ? 3 : 4);
			h = height / 8 * 3;
			u = height / 4 * (slim ? 6.375F : 6.5F);
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Left Leg
			x = x - width / 2;
			y = y + height / 32 * (slim ? 11 : 12);
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 1.5F;
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Left Pants
			x = x + 0;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 1.5F;
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Right Leg
			x = x + width / 4;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 1.5F;
			v = height / 4 * 2.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
			// Right Pants
			x = x + 0;
			y = y + 0;
			w = width / 4;
			h = height / 8 * 3;
			u = height / 4 * 1.5F;
			v = height / 4 * 4.5F;
			context.drawTexture(texture, x, y, u, v, w, h, fw, fh);
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}
