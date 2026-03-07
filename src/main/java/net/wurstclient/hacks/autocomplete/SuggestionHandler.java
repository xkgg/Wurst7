/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autocomplete;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.util.math.MathHelper;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class SuggestionHandler
{
	private final ArrayList<String> suggestions = new ArrayList<>();
	
	private final SliderSetting maxSuggestionsPerDraft =
		new SliderSetting("每草稿最大建议数",
			"AI可以为同一条草稿消息生成多少个建议。",
			3, 1, 10, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting maxSuggestionsKept = new SliderSetting(
		"保留的最大建议数", "在内存中保留的最大建议数量。",
		100, 10, 1000, 10, ValueDisplay.INTEGER);
	
	private final SliderSetting maxSuggestionsShown = new SliderSetting(
		"显示的最大建议数",
		"可以在聊天框上方显示多少个建议。\n\n"
			+ "如果设置得太高，建议会遮挡一些现有的聊天消息。你可以设置多高"
			+ " 取决于你的屏幕分辨率和GUI缩放。",
		5, 1, 10, 1, ValueDisplay.INTEGER);
	
	private final List<Setting> settings = Arrays.asList(maxSuggestionsPerDraft,
		maxSuggestionsKept, maxSuggestionsShown);
	
	public List<Setting> getSettings()
	{
		return settings;
	}
	
	public int getMaxSuggestionsFor(String draftMessage)
	{
		synchronized(suggestions)
		{
			int existing = (int)suggestions.stream().map(String::toLowerCase)
				.filter(s -> s.startsWith(draftMessage.toLowerCase())).count();
			int maxPerDraft = maxSuggestionsPerDraft.getValueI();
			
			return MathHelper.clamp(maxPerDraft - existing, 0, maxPerDraft);
		}
	}
	
	public void addSuggestion(String suggestion, String draftMessage,
		BiConsumer<SuggestionsBuilder, String> suggestionsUpdater)
	{
		synchronized(suggestions)
		{
			String completedMessage = draftMessage + suggestion;
			
			if(!suggestions.contains(completedMessage))
			{
				suggestions.add(completedMessage);
				
				if(suggestions.size() > maxSuggestionsKept.getValue())
					suggestions.remove(0);
			}
			
			showSuggestionsImpl(draftMessage, suggestionsUpdater);
		}
	}
	
	public void showSuggestions(String draftMessage,
		BiConsumer<SuggestionsBuilder, String> suggestionsUpdater)
	{
		synchronized(suggestions)
		{
			showSuggestionsImpl(draftMessage, suggestionsUpdater);
		}
	}
	
	private void showSuggestionsImpl(String draftMessage,
		BiConsumer<SuggestionsBuilder, String> suggestionsUpdater)
	{
		SuggestionsBuilder builder = new SuggestionsBuilder(draftMessage, 0);
		String inlineSuggestion = null;
		
		int shownSuggestions = 0;
		for(int i = suggestions.size() - 1; i >= 0; i--)
		{
			String s = suggestions.get(i);
			if(!s.toLowerCase().startsWith(draftMessage.toLowerCase()))
				continue;
			
			if(shownSuggestions >= maxSuggestionsShown.getValue())
				break;
			
			builder.suggest(s);
			inlineSuggestion = s;
			shownSuggestions++;
		}
		
		suggestionsUpdater.accept(builder, inlineSuggestion);
	}
	
	public void clearSuggestions()
	{
		synchronized(suggestions)
		{
			suggestions.clear();
		}
	}
}
