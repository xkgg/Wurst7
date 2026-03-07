/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.events.MouseScrollListener;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.MathUtils;

@SearchTags({"望远镜", "telescope", "optifine"})
@DontBlock
public final class ZoomOtf extends OtherFeature implements MouseScrollListener
{
	private final SliderSetting level = new SliderSetting("缩放级别", 3, 1,
		50, 0.1, ValueDisplay.DECIMAL.withSuffix("x"));
	
	private final CheckboxSetting scroll = new CheckboxSetting(
		"使用鼠标滚轮", "如果启用，你可以在缩放时使用鼠标滚轮进一步放大。",
		true);
	
	private final CheckboxSetting zoomInScreens = new CheckboxSetting(
		"在屏幕中缩放", "如果启用，你也可以在屏幕（聊天、物品栏等）打开时进行缩放。",
		false);
	
	private final TextFieldSetting keybind = new TextFieldSetting("按键绑定",
		"确定缩放按键绑定。\n\n"
			+ "不要手动编辑此值，你应该前往Wurst选项 -> 缩放并在那里设置。",
		"key.keyboard.v", this::isValidKeybind);
	
	private Double currentLevel;
	private Double defaultMouseSensitivity;
	
	public ZoomOtf()
	{
		super("缩放", "允许你放大。\n"
			+ "默认情况下，按\u00a7lV\u00a7r键激活缩放。\n"
			+ "前往Wurst选项 -> 缩放来更改此按键绑定。");
		addSetting(level);
		addSetting(scroll);
		addSetting(zoomInScreens);
		addSetting(keybind);
		EVENTS.add(MouseScrollListener.class, this);
	}
	
	public double changeFovBasedOnZoom(double fov)
	{
		SimpleOption<Double> mouseSensitivitySetting =
			MC.options.getMouseSensitivity();
		
		if(currentLevel == null)
			currentLevel = level.getValue();
		
		if(!isZoomKeyPressed())
		{
			currentLevel = level.getValue();
			
			if(defaultMouseSensitivity != null)
			{
				mouseSensitivitySetting.setValue(defaultMouseSensitivity);
				defaultMouseSensitivity = null;
			}
			
			return fov;
		}
		
		if(defaultMouseSensitivity == null)
			defaultMouseSensitivity = mouseSensitivitySetting.getValue();
			
		// Adjust mouse sensitivity in relation to zoom level.
		// 1.0 / currentLevel is a value between 0.02 (50x zoom)
		// and 1 (no zoom).
		mouseSensitivitySetting
			.setValue(defaultMouseSensitivity * (1.0 / currentLevel));
		
		return fov / currentLevel;
	}
	
	@Override
	public void onMouseScroll(double amount)
	{
		if(!isZoomKeyPressed() || !scroll.isChecked())
			return;
		
		if(currentLevel == null)
			currentLevel = level.getValue();
		
		if(amount > 0)
			currentLevel *= 1.1;
		else if(amount < 0)
			currentLevel *= 0.9;
		
		currentLevel = MathUtils.clamp(currentLevel, level.getMinimum(),
			level.getMaximum());
	}
	
	public boolean shouldPreventHotbarScrolling()
	{
		return isZoomKeyPressed() && scroll.isChecked();
	}
	
	public Text getTranslatedKeybindName()
	{
		return InputUtil.fromTranslationKey(keybind.getValue())
			.getLocalizedText();
	}
	
	public void setBoundKey(String translationKey)
	{
		keybind.setValue(translationKey);
	}
	
	private boolean isZoomKeyPressed()
	{
		if(MC.currentScreen != null && !zoomInScreens.isChecked())
			return false;
		
		return InputUtil.isKeyPressed(MC.getWindow().getHandle(),
			InputUtil.fromTranslationKey(keybind.getValue()).getCode());
	}
	
	private boolean isValidKeybind(String keybind)
	{
		try
		{
			return InputUtil.fromTranslationKey(keybind) != null;
			
		}catch(IllegalArgumentException e)
		{
			return false;
		}
	}
	
	public SliderSetting getLevelSetting()
	{
		return level;
	}
	
	public CheckboxSetting getScrollSetting()
	{
		return scroll;
	}
}
