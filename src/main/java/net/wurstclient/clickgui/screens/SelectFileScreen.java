/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui.screens;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.wurstclient.settings.FileSetting;
import net.wurstclient.util.text.WText;

public final class SelectFileScreen extends Screen
{
	private final Screen prevScreen;
	private final FileSetting setting;
	
	private ListGui listGui;
	private ButtonWidget doneButton;
	
	public SelectFileScreen(Screen prevScreen, FileSetting blockList)
	{
		super(Text.literal(""));
		this.prevScreen = prevScreen;
		setting = blockList;
	}
	
	@Override
	public void init()
	{
		listGui = new ListGui(client, this, setting.listFiles());
		addSelectableChild(listGui);
		
		addDrawableChild(ButtonWidget
			.builder(translatedText("gui.wurst.select_file.open_folder"),
				b -> openFolder())
			.dimensions(8, 8, 100, 20).build());
		
		addDrawableChild(ButtonWidget
			.builder(translatedText("gui.wurst.select_file.reset_to_defaults"),
				b -> askToConfirmReset())
			.dimensions(width - 108, 8, 100, 20).build());
		
		doneButton = addDrawableChild(ButtonWidget
			.builder(translatedText("gui.wurst.select_file.done"), b -> done())
			.dimensions(width / 2 - 102, height - 48, 100, 20).build());
		
		addDrawableChild(ButtonWidget
			.builder(translatedText("gui.wurst.select_file.cancel"),
				b -> openPrevScreen())
			.dimensions(width / 2 + 2, height - 48, 100, 20).build());
	}
	
	private static Text translatedText(String key, Object... args)
	{
		return Text.literal(WText.translated(key, args).toString());
	}
	
	private void openFolder()
	{
		Util.getOperatingSystem().open(setting.getFolder().toFile());
	}
	
	private void openPrevScreen()
	{
		client.setScreen(prevScreen);
	}
	
	private void done()
	{
		Path path = listGui.getSelectedPath();
		if(path != null)
		{
			String fileName = "" + path.getFileName();
			setting.setSelectedFile(fileName);
		}
		
		openPrevScreen();
	}
	
	private void askToConfirmReset()
	{
		Text title =
			translatedText("gui.wurst.select_file.reset_confirm.title");
		Text message =
			translatedText("gui.wurst.select_file.reset_confirm.message",
				setting.getFolder().getFileName());
		
		client.setScreen(new ConfirmScreen(this::confirmReset, title, message));
	}
	
	private void confirmReset(boolean confirmed)
	{
		if(confirmed)
			setting.resetFolder();
		
		client.setScreen(SelectFileScreen.this);
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode == GLFW.GLFW_KEY_ENTER)
			done();
		else if(keyCode == GLFW.GLFW_KEY_ESCAPE)
			openPrevScreen();
		
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	
	@Override
	public void tick()
	{
		doneButton.active = listGui.getSelectedOrNull() != null;
	}
	
	@Override
	public void render(DrawContext context, int mouseX, int mouseY,
		float partialTicks)
	{
		renderBackground(context);
		listGui.render(context, mouseX, mouseY, partialTicks);
		
		context.drawCenteredTextWithShadow(client.textRenderer,
			setting.getName(), width / 2, 12, 0xffffff);
		
		super.render(context, mouseX, mouseY, partialTicks);
		
		if(doneButton.isSelected() && !doneButton.active)
			context.drawTooltip(textRenderer,
				Arrays.asList(
					translatedText("gui.wurst.select_file.no_file_selected")),
				mouseX, mouseY);
	}
	
	@Override
	public boolean shouldPause()
	{
		return false;
	}
	
	@Override
	public boolean shouldCloseOnEsc()
	{
		return false;
	}
	
	private final class Entry
		extends AlwaysSelectedEntryListWidget.Entry<SelectFileScreen.Entry>
	{
		private final Path path;
		
		public Entry(Path path)
		{
			this.path = Objects.requireNonNull(path);
		}
		
		@Override
		public Text getNarration()
		{
			return Text.translatable("narrator.select",
				WText.translated("gui.wurst.select_file.entry_narration",
					path.getFileName()).toString());
		}
		
		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button)
		{
			return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		}
		
		@Override
		public void render(DrawContext context, int index, int y, int x,
			int entryWidth, int entryHeight, int mouseX, int mouseY,
			boolean hovered, float tickDelta)
		{
			TextRenderer tr = client.textRenderer;
			
			String fileName = "" + path.getFileName();
			context.drawTextWithShadow(tr, fileName, x + 28, y, 0xF0F0F0);
			
			String relPath = "" + client.runDirectory.toPath().relativize(path);
			context.drawTextWithShadow(tr, relPath, x + 28, y + 9, 0xA0A0A0);
		}
	}
	
	private final class ListGui
		extends AlwaysSelectedEntryListWidget<SelectFileScreen.Entry>
	{
		public ListGui(MinecraftClient mc, SelectFileScreen screen,
			List<Path> list)
		{
			super(mc, screen.width, screen.height, 36, screen.height - 64, 20);
			
			list.stream().map(SelectFileScreen.Entry::new)
				.forEach(this::addEntry);
		}
		
		public Path getSelectedPath()
		{
			SelectFileScreen.Entry selected = getSelectedOrNull();
			return selected != null ? selected.path : null;
		}
	}
}
