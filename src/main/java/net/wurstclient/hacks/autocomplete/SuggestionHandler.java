/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
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

import net.minecraft.util.Mth;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class SuggestionHandler
{
	private final ArrayList<String> suggestions = new ArrayList<>();
	
	private final SliderSetting maxSuggestionsPerDraft =
		new SliderSetting("每条草稿最多建议数",
			"AI允许为同一条草稿消息生成的建议数量。",
			3, 1, 10, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting maxSuggestionsKept = new SliderSetting(
		"最大保留建议数", "保留在内存中的最大建议数量。",
		100, 10, 1000, 10, ValueDisplay.INTEGER);
	
	private final SliderSetting maxSuggestionsShown = new SliderSetting(
		"最大显示建议数",
		"聊天框上方可以显示的建议数量。\n\n"
			+ "如果设置得太高，建议会遮挡一些"
			+ "现有的聊天消息。此值可以设置多高取决于"
			+ "您的屏幕分辨率和GUI缩放比例。",
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
			
			return Mth.clamp(maxPerDraft - existing, 0, maxPerDraft);
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
