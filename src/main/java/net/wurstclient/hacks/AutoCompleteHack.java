/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.function.BiConsumer;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.client.gui.screen.ChatScreen;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autocomplete.MessageCompleter;
import net.wurstclient.hacks.autocomplete.ModelSettings;
import net.wurstclient.hacks.autocomplete.OpenAiMessageCompleter;
import net.wurstclient.hacks.autocomplete.SuggestionHandler;
import net.wurstclient.util.ChatUtils;

@SearchTags({"自动聊天", "auto complete", "Copilot", "ChatGPT", "chat GPT", "GPT-3", "GPT3",
	"GPT 3", "OpenAI", "open ai", "ChatAI", "chat AI", "ChatBot", "chat bot"})
public final class AutoCompleteHack extends Hack
	implements ChatOutputListener, UpdateListener
{
	// 模型配置（API相关参数）
	private final ModelSettings modelSettings = new ModelSettings();
	// 建议处理器（管理聊天补全建议的展示/清理）
	private final SuggestionHandler suggestionHandler = new SuggestionHandler();
	
	// 消息补全器（核心补全逻辑实现）
	private MessageCompleter completer;
	// 草稿消息（聊天框中正在输入的未发送文本）
	private String draftMessage;
	// 建议更新器（用于更新聊天补全建议的回调函数）
	private BiConsumer<SuggestionsBuilder, String> suggestionsUpdater;
	
	// API调用线程（异步执行OpenAI接口请求）
	private Thread apiCallThread;
	// 上次API调用时间（用于限制调用频率）
	private long lastApiCallTime;
	// 上次刷新时间（用于限制建议刷新频率）
	private long lastRefreshTime;
	
	public AutoCompleteHack()
	{
		super("自动聊天");
		setCategory(Category.CHAT);
		
		// 将模型配置和建议处理器的设置项添加到功能中
		modelSettings.forEach(this::addSetting);
		suggestionHandler.getSettings().forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		// 初始化OpenAI消息补全器
		completer = new OpenAiMessageCompleter(modelSettings);
		
		// 检查OpenAI API密钥是否配置
		if(completer instanceof OpenAiMessageCompleter
			&& System.getenv("WURST_OPENAI_KEY") == null)
		{
			ChatUtils.error("未找到API密钥。请配置WURST_OPENAI_KEY环境变量并重启客户端。");
			setEnabled(false);
			return;
		}
		
		// 注册事件监听器
		EVENTS.add(ChatOutputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销事件监听器
		EVENTS.remove(ChatOutputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		
		// 清空所有聊天补全建议
		suggestionHandler.clearSuggestions();
	}
	
	@Override
	public void onSentMessage(ChatOutputEvent event)
	{
		// 发送消息后清空补全建议
		suggestionHandler.clearSuggestions();
	}
	
	@Override
	public void onUpdate()
	{
		// 检查距离上次刷新是否已过300毫秒（限制刷新频率）
		long timeSinceLastRefresh =
			System.currentTimeMillis() - lastRefreshTime;
		if(timeSinceLastRefresh < 300)
			return;
		
		// 检查距离上次API调用是否已过3秒（限制API调用频率）
		long timeSinceLastApiCall =
			System.currentTimeMillis() - lastApiCallTime;
		if(timeSinceLastApiCall < 3000)
			return;
		
		// 检查聊天界面是否打开
		if(!(MC.currentScreen instanceof ChatScreen))
			return;
		
		// 检查是否有草稿消息和建议更新器
		if(draftMessage == null || suggestionsUpdater == null)
			return;
		
		// 避免重复创建线程（如果上次API调用线程仍在运行则跳过）
		if(apiCallThread != null && apiCallThread.isAlive())
			return;
		
		// 检查当前草稿消息是否需要生成补全建议
		int maxSuggestions =
			suggestionHandler.getMaxSuggestionsFor(draftMessage);
		if(maxSuggestions < 1)
			return;
			
		// 复制变量到本地（避免线程运行时变量被修改）
		String draftMessage2 = draftMessage;
		BiConsumer<SuggestionsBuilder, String> suggestionsUpdater2 =
			suggestionsUpdater;
		
		// 构建API调用线程
		apiCallThread = new Thread(() -> {
			
			// 调用补全器生成聊天消息建议
			String[] suggestions =
				completer.completeChatMessage(draftMessage2, maxSuggestions);
			if(suggestions.length < 1)
				return;
			
			// 遍历所有建议并添加到建议处理器
			for(String suggestion : suggestions)
			{
				if(suggestion.isEmpty())
					continue;
				
				// 应用补全建议
				suggestionHandler.addSuggestion(suggestion, draftMessage2,
					suggestionsUpdater2);
			}
		});
		apiCallThread.setName("AutoComplete API Call");
		apiCallThread.setPriority(Thread.MIN_PRIORITY);
		apiCallThread.setDaemon(true);
		
		// 启动API调用线程
		lastApiCallTime = System.currentTimeMillis();
		apiCallThread.start();
	}
	
	/**
	 * 刷新聊天补全建议
	 * @param draftMessage 聊天框中的草稿消息
	 * @param suggestionsUpdater 建议更新回调函数
	 */
	public void onRefresh(String draftMessage,
		BiConsumer<SuggestionsBuilder, String> suggestionsUpdater)
	{
		// 展示当前草稿消息的补全建议
		suggestionHandler.showSuggestions(draftMessage, suggestionsUpdater);
		
		// 更新本地变量并记录刷新时间
		this.draftMessage = draftMessage;
		this.suggestionsUpdater = suggestionsUpdater;
		lastRefreshTime = System.currentTimeMillis();
	}
	
	// 参考 ChatInputSuggestorMixin 实现补全建议的界面展示
}