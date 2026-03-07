/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autocomplete;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

public final class ModelSettings
{
	public final EnumSetting<OpenAiModel> openAiModel = new EnumSetting<>(
		"OpenAI模型", "用于OpenAI API调用的模型。",
		OpenAiModel.values(), OpenAiModel.GPT_4O_2024_08_06);
	
	public enum OpenAiModel
	{
		GPT_4O_2024_08_06("gpt-4o-2024-08-06", true),
		GPT_4O_2024_05_13("gpt-4o-2024-05-13", true),
		GPT_4O_MINI_2024_07_18("gpt-4o-mini-2024-07-18", true),
		GPT_4_TURBO_2024_04_09("gpt-4-turbo-2024-04-09", true),
		GPT_4_0125_PREVIEW("gpt-4-0125-preview", true),
		GPT_4_1106_PREVIEW("gpt-4-1106-preview", true),
		GPT_4_0613("gpt-4-0613", true),
		GPT_3_5_TURBO_0125("gpt-3.5-turbo-0125", true),
		GPT_3_5_TURBO_1106("gpt-3.5-turbo-1106", true),
		GPT_3_5_TURBO_INSTRUCT("gpt-3.5-turbo-instruct", false),
		DAVINCI_002("davinci-002", false),
		BABBAGE_002("babbage-002", false);
		
		private final String name;
		private final boolean chat;
		
		private OpenAiModel(String name, boolean chat)
		{
			this.name = name;
			this.chat = chat;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
		
		public boolean isChatModel()
		{
			return chat;
		}
	}
	
	public final SliderSetting maxTokens = new SliderSetting("最大 tokens",
		"模型可以生成的最大token数量。\n\n"
			+ "更高的值允许模型预测更长的聊天消息，"
			+ " 但也会增加生成预测所需的时间。\n\n"
			+ "默认值16适用于大多数用例。",
		16, 1, 100, 1, ValueDisplay.INTEGER);
	
	public final SliderSetting temperature = new SliderSetting("温度",
		"控制模型的创造力和随机性。更高的值会"
			+ " 导致更具创造力且有时毫无意义的完成，"
			+ " 而较低的值会导致更无聊的完成。",
		1, 0, 2, 0.01, ValueDisplay.DECIMAL);
	
	public final SliderSetting topP = new SliderSetting("Top P",
		"温度的替代方案。通过只让模型从最可能的token中选择，"
			+ " 使模型减少随机性。\n\n"
			+ "100%的值通过让模型从所有token中选择来禁用此功能。",
		1, 0, 1, 0.01, ValueDisplay.PERCENTAGE);
	
	public final SliderSetting presencePenalty =
		new SliderSetting("存在惩罚",
			"对选择已出现在聊天历史中的token的惩罚。\n\n"
				+ "正值鼓励模型使用同义词并谈论不同的话题。负值鼓励模型"
				+ " 一遍又一遍地重复同一个词。",
		0, -2, 2, 0.01, ValueDisplay.DECIMAL);
	
	public final SliderSetting frequencyPenalty =
		new SliderSetting("频率惩罚",
			"与存在惩罚类似，但基于token在聊天历史中出现的频率。\n\n"
				+ "正值鼓励模型使用同义词并谈论不同的话题。负值鼓励模型"
				+ " 重复现有的聊天消息。",
		0, -2, 2, 0.01, ValueDisplay.DECIMAL);
	
	public final EnumSetting<StopSequence> stopSequence = new EnumSetting<>(
		"停止序列",
		"控制AutoComplete如何检测聊天消息的结束。\n\n"
			+ "\u00a7l换行\u00a7r是默认值，推荐用于大多数语言模型。\n\n"
			+ "\u00a7l下一条消息\u00a7r在某些代码优化的语言模型上效果更好，"
			+ " 这些模型倾向于在聊天消息中间插入换行。",
		StopSequence.values(), StopSequence.LINE_BREAK);
	
	public enum StopSequence
	{
		LINE_BREAK("换行", "\n"),
		NEXT_MESSAGE("下一条消息", "\n<");
		
		private final String name;
		private final String sequence;
		
		private StopSequence(String name, String sequence)
		{
			this.name = name;
			this.sequence = sequence;
		}
		
		public String getSequence()
		{
			return sequence;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	public final SliderSetting contextLength = new SliderSetting(
		"上下文长度",
		"控制从聊天历史中使用多少消息来生成预测。\n\n"
			+ "更高的值会提高预测质量，但也会增加生成它们所需的时间，"
			+ " 以及成本（对于OpenAI等API）或内存使用（对于自托管模型）。",
		10, 0, 100, 1, ValueDisplay.INTEGER);
	
	public final CheckboxSetting filterServerMessages =
		new CheckboxSetting("过滤服务器消息",
			"只向模型显示玩家制作的聊天消息。\n\n"
				+ "这可以帮助你节省token并从低上下文长度中获得更多，"
				+ " 但也意味着模型将不知道玩家加入、离开、死亡等事件。",
		false);
	
	public final TextFieldSetting customModel = new TextFieldSetting(
		"自定义模型",
		"如果设置，将使用此模型而不是在\"OpenAI模型\"设置中指定的模型。\n\n"
			+ "如果你有微调的OpenAI模型，或者你使用的是与OpenAI兼容但提供不同模型的自定义端点，请使用此选项。",
		"");
	
	public final EnumSetting<CustomModelType> customModelType =
		new EnumSetting<>("自定义模型类型", "自定义模型应该使用聊天端点还是传统端点。\n\n"
			+ "如果\"自定义模型\"留空，此设置将被忽略。",
			CustomModelType.values(), CustomModelType.CHAT);
	
	public enum CustomModelType
	{
		CHAT("聊天", true),
		LEGACY("传统", false);
		
		private final String name;
		private final boolean chat;
		
		private CustomModelType(String name, boolean chat)
		{
			this.name = name;
			this.chat = chat;
		}
		
		public boolean isChat()
		{
			return chat;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	public final TextFieldSetting openaiChatEndpoint = new TextFieldSetting(
		"OpenAI聊天端点", "OpenAI聊天完成API的端点。",
		"https://api.openai.com/v1/chat/completions");
	
	public final TextFieldSetting openaiLegacyEndpoint =
		new TextFieldSetting("OpenAI传统端点",
			"OpenAI传统完成API的端点。",
			"https://api.openai.com/v1/completions");
	
	private final List<Setting> settings =
		Collections.unmodifiableList(Arrays.asList(openAiModel, maxTokens,
			temperature, topP, presencePenalty, frequencyPenalty, stopSequence,
			contextLength, filterServerMessages, customModel, customModelType,
			openaiChatEndpoint, openaiLegacyEndpoint));
	
	public void forEach(Consumer<Setting> action)
	{
		settings.forEach(action);
	}
}
