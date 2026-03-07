/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autolibrarian.BookOffer;
import net.wurstclient.hacks.autolibrarian.UpdateBooksSetting;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.BookOffersSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FacingSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.*;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;

@SearchTags({"自动图书管理员","auto librarian", "AutoVillager", "auto villager",
	"VillagerTrainer", "villager trainer", "LibrarianTrainer",
	"librarian trainer", "AutoHmmm", "auto hmmm"})
public final class AutoLibrarianHack extends Hack
	implements UpdateListener, RenderListener
{
	// 目标附魔书（配置想要的附魔书及最高收购价）
	private final BookOffersSetting wantedBooks = new BookOffersSetting(
		"目标附魔书",
		"你希望图书管理员村民出售的附魔书清单。\n\n"
			+ "一旦检测到村民学会出售清单中的任意一本附魔书，\n"
			+ "将停止对当前村民的刷新操作。\n\n"
			+ "你可为每本附魔书设置最高收购价格。\n\n"
			+ "即使你已有村民出售该附魔书，也可通过此配置\n"
			+ "筛选出售价更低的交易选项。",

		"minecraft:depth_strider", "minecraft:efficiency",
		"minecraft:feather_falling", "minecraft:fortune", "minecraft:looting",
		"minecraft:mending", "minecraft:protection", "minecraft:respiration",
		"minecraft:sharpness", "minecraft:silk_touch", "minecraft:unbreaking");
	
	// 锁定交易（防止村民刷新交易选项）
	private final CheckboxSetting lockInTrade = new CheckboxSetting(
		"锁定交易",
		"当村民学会出售你想要的附魔书后，自动从村民处购买一件物品。\n"
			+ "此操作可防止村民后续刷新交易选项。\n\n"
			+ "使用此功能时，请确保背包中至少有24张纸+9个绿宝石，\n"
			+ "或1本书+64个绿宝石（两种组合任选其一）。",
		false);
	
	// 附魔书更新规则
	private final UpdateBooksSetting updateBooks = new UpdateBooksSetting();
	
	// 检测范围（搜索村民和讲台的最大距离）
	private final SliderSetting range =
		new SliderSetting("检测范围", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	// 朝向设置（控制面向村民/讲台的方式）
	private final FacingSetting facing = FacingSetting.withoutPacketSpam(
		"自动图书管理员面向村民和职业方块的方式：\n\n"
			+ "\u00a7l关闭\u00a7r - 完全不调整朝向，易被反作弊检测。\n\n"
			+ "\u00a7l服务端\u00a7r - 仅在服务端层面调整朝向，客户端视角可自由移动。\n\n"
			+ "\u00a7l客户端\u00a7r - 通过移动客户端视角朝向村民，最接近正常操作，\n"
			+ "但可能导致视角混乱。");
	
	// 挥动手臂设置
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	// 工具耐久保护（防止斧头耐久耗尽）
	private final SliderSetting repairMode = new SliderSetting("工具耐久保护",
		"当斧头剩余耐久次数低于该阈值时，停止使用该斧头破坏讲台，\n"
		+ "以便你在斧头损坏前进行修复。\n"
		+ "取值范围0（关闭）~100（剩余使用次数）。",
		1, 0, 100, 1, ValueDisplay.INTEGER.withLabel(0, "关闭"));
	
	// 进度渲染器（显示破坏讲台的进度）
	private final OverlayRenderer overlay = new OverlayRenderer();
	// 已经验证的村民（无法再刷新交易的村民）
	private final HashSet<VillagerEntity> experiencedVillagers =
		new HashSet<>();
	
	// 当前目标村民
	private VillagerEntity villager;
	// 当前目标讲台（村民的职业方块）
	private BlockPos jobSite;
	
	// 正在放置讲台标记
	private boolean placingJobSite;
	// 正在破坏讲台标记
	private boolean breakingJobSite;
	
	public AutoLibrarianHack()
	{
		super("自动图书管理员");
		setCategory(Category.OTHER);
		addSetting(wantedBooks);
		addSetting(lockInTrade);
		addSetting(updateBooks);
		addSetting(range);
		addSetting(facing);
		addSetting(swingHand);
		addSetting(repairMode);
	}
	
	@Override
	protected void onEnable()
	{
		// 注册事件监听器
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		// 注销事件监听器
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// 若正在破坏讲台，取消破坏操作
		if(breakingJobSite)
		{
			MC.interactionManager.breakingBlock = true;
			MC.interactionManager.cancelBlockBreaking();
			breakingJobSite = false;
		}
		
		// 重置所有状态
		overlay.resetProgress();
		villager = null;
		jobSite = null;
		placingJobSite = false;
		breakingJobSite = false;
		experiencedVillagers.clear();
	}
	
	@Override
	public void onUpdate()
	{
		// 未找到目标村民：重新搜索
		if(villager == null)
		{
			setTargetVillager();
			return;
		}
		
		// 未找到目标讲台：重新搜索
		if(jobSite == null)
		{
			setTargetJobSite();
			return;
		}
		
		// 异常状态：同时进行放置和破坏讲台
		if(placingJobSite && breakingJobSite)
			throw new IllegalStateException(
				"同时执行放置和破坏讲台操作，出现异常。");
		
		// 正在放置讲台：执行放置逻辑
		if(placingJobSite)
		{
			placeJobSite();
			return;
		}
		
		// 正在破坏讲台：执行破坏逻辑
		if(breakingJobSite)
		{
			breakJobSite();
			return;
		}
		
		// 未打开交易界面：打开村民交易界面
		if(!(MC.currentScreen instanceof MerchantScreen tradeScreen))
		{
			openTradeScreen();
			return;
		}
		
		// 检查村民经验值：仅当交易界面打开后才能获取，若经验值>0则无法刷新交易
		int experience = tradeScreen.getScreenHandler().getExperience();
		if(experience > 0)
		{
			ChatUtils.warning("位于 "
				+ villager.getBlockPos().toShortString()
				+ " 的村民已有交易经验，无法继续刷新交易。");
			ChatUtils.message("正在寻找其他图书管理员村民...");
			experiencedVillagers.add(villager);
			villager = null;
			jobSite = null;
			closeTradeScreen();
			return;
		}
		
		// 查找村民出售的附魔书交易项
		BookOffer bookOffer =
			findEnchantedBookOffer(tradeScreen.getScreenHandler().getRecipes());
		
		// 未找到附魔书交易项：破坏讲台重新刷新
		if(bookOffer == null)
		{
			ChatUtils.message("当前村民未出售附魔书，刷新交易中...");
			closeTradeScreen();
			breakingJobSite = true;
			System.out.println("破坏讲台以刷新交易...");
			return;
		}
		
		// 输出当前村民出售的附魔书信息
		ChatUtils.message(
			"检测到村民出售 " + bookOffer.getEnchantmentNameWithLevel()
				+ " 附魔书，售价：" + bookOffer.getFormattedPrice() + "。");
		
		// 不是目标附魔书：破坏讲台重新刷新
		if(!wantedBooks.isWanted(bookOffer))
		{
			breakingJobSite = true;
			System.out.println("非目标附魔书，破坏讲台刷新交易...");
			closeTradeScreen();
			return;
		}
		
		// 启用锁定交易：购买物品锁定交易选项
		if(lockInTrade.isChecked())
		{
			// 选择第一个有效交易项
			tradeScreen.getScreenHandler().setRecipeIndex(0);
			tradeScreen.getScreenHandler().switchTo(0);
			MC.getNetworkHandler()
				.sendPacket(new SelectMerchantTradeC2SPacket(0));
			
			// 购买村民出售的物品（锁定交易）
			MC.interactionManager.clickSlot(
				tradeScreen.getScreenHandler().syncId, 2, 0,
				SlotActionType.PICKUP, MC.player);
			
			// 关闭交易界面
			closeTradeScreen();
		}
		
		// 根据配置更新目标附魔书列表
		updateBooks.getSelected().update(wantedBooks, bookOffer);
		
		// 完成目标：输出提示并关闭功能
		ChatUtils.message("已找到目标附魔书！操作完成。");
		setEnabled(false);
	}
	
	/**
	 * 破坏讲台（刷新村民交易选项）
	 */
	private void breakJobSite()
	{
		if(jobSite == null)
			throw new IllegalStateException("目标讲台位置为空。");
		
		// 获取破坏讲台的参数
		BlockBreakingParams params =
			BlockBreaker.getBlockBreakingParams(jobSite);
		
		// 讲台已被破坏/可替换：切换为放置讲台状态
		if(params == null || BlockUtils.getState(jobSite).isReplaceable())
		{
			System.out.println("讲台已破坏，准备重新放置...");
			breakingJobSite = false;
			placingJobSite = true;
			return;
		}
		
		// 自动装备最优工具（考虑耐久保护）
		WURST.getHax().autoToolHack.equipBestTool(jobSite, false, true,
			repairMode.getValueI());
		
		// 朝向讲台
		facing.getSelected().face(params.hitVec());
		
		// 破坏讲台并挥动手臂
		if(MC.interactionManager.updateBlockBreakingProgress(jobSite,
			params.side()))
			swingHand.swing(Hand.MAIN_HAND);
		
		// 更新破坏进度渲染
		overlay.updateProgress();
	}
	
	/**
	 * 放置讲台（恢复村民职业以继续刷新交易）
	 */
	private void placeJobSite()
	{
		if(jobSite == null)
			throw new IllegalStateException("目标讲台位置为空。");
		
		// 讲台位置已有方块
		if(!BlockUtils.getState(jobSite).isReplaceable())
		{
			// 已有讲台：完成放置
			if(BlockUtils.getBlock(jobSite) == Blocks.LECTERN)
			{
				System.out.println("讲台已重新放置完成。");
				placingJobSite = false;
				
			// 非讲台方块：破坏该方块
			}else
			{
				System.out
					.println("讲台位置检测到错误方块，正在破坏...");
				breakingJobSite = true;
				placingJobSite = false;
			}
			
			return;
		}
		
		// 检查是否手持讲台：未手持则自动选中
		if(!MC.player.isHolding(Items.LECTERN))
		{
			InventoryUtils.selectItem(Items.LECTERN, 36);
			return;
		}
		
		// 确定手持讲台的手（主手/副手）
		Hand hand = MC.player.getMainHandStack().isOf(Items.LECTERN)
			? Hand.MAIN_HAND : Hand.OFF_HAND;
		
		// 按住潜行键放置（避免误触活板门/箱子等）
		IKeyBinding sneakKey = IKeyBinding.get(MC.options.sneakKey);
		sneakKey.setPressed(true);
		if(!MC.player.isSneaking())
			return;
		
		// 获取放置讲台的参数
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(jobSite);
		if(params == null)
		{
			sneakKey.resetPressedState();
			return;
		}
		
		// 朝向放置位置
		facing.getSelected().face(params.hitVec());
		
		// 放置讲台
		ActionResult result = MC.interactionManager.interactBlock(MC.player,
			hand, params.toHitResult());
		
		// 放置成功则挥动手臂
		if(result.isAccepted() && result.shouldSwingHand())
			swingHand.swing(hand);
		
		// 释放潜行键
		sneakKey.resetPressedState();
	}
	
	/**
	 * 打开村民交易界面
	 */
	private void openTradeScreen()
	{
		// 物品使用冷却中：跳过
		if(MC.itemUseCooldown > 0)
			return;
		
		ClientPlayerInteractionManager im = MC.interactionManager;
		ClientPlayerEntity player = MC.player;
		
		// 村民超出检测范围：提示并关闭功能
		if(player.squaredDistanceTo(villager) > range.getValueSq())
		{
			ChatUtils.error("村民超出检测范围！建议将村民困住，防止其走远。");
			setEnabled(false);
			return;
		}
		
		// 构造真实的实体命中结果（模拟正常点击）
		Box box = villager.getBoundingBox();
		Vec3d start = RotationUtils.getEyesPos();
		Vec3d end = box.getCenter();
		Vec3d hitVec = box.raycast(start, end).orElse(start);
		EntityHitResult hitResult = new EntityHitResult(villager, hitVec);
		
		// 朝向村民中心
		facing.getSelected().face(end);
		
		// 点击村民打开交易界面
		Hand hand = Hand.MAIN_HAND;
		ActionResult actionResult =
			im.interactEntityAtLocation(player, villager, hitResult, hand);
		
		// 首次点击失败则重试普通交互
		if(!actionResult.isAccepted())
			im.interactEntity(player, villager, hand);
		
		// 交互成功则挥动手臂
		if(actionResult.isAccepted() && actionResult.shouldSwingHand())
			swingHand.swing(hand);
		
		// 设置物品使用冷却
		MC.itemUseCooldown = 4;
	}
	
	/**
	 * 关闭村民交易界面
	 */
	private void closeTradeScreen()
	{
		MC.player.closeHandledScreen();
		MC.itemUseCooldown = 4;
	}
	
	/**
	 * 从村民交易列表中查找附魔书交易项
	 * @param tradeOffers 村民交易列表
	 * @return 附魔书交易项（null表示未找到）
	 */
	private BookOffer findEnchantedBookOffer(TradeOfferList tradeOffers)
	{
		for(TradeOffer tradeOffer : tradeOffers)
		{
			ItemStack stack = tradeOffer.getSellItem();
			// 过滤非附魔书物品
			if(!(stack.getItem() instanceof EnchantedBookItem))
				continue;
			
			// 过滤无附魔的书
			NbtList enchantmentNbt = EnchantedBookItem.getEnchantmentNbt(stack);
			if(enchantmentNbt.isEmpty())
				continue;
			
			// 解析附魔书NBT数据
			NbtList bookNbt = EnchantedBookItem.getEnchantmentNbt(stack);
			String enchantment = bookNbt.getCompound(0).getString("id");
			int level = bookNbt.getCompound(0).getInt("lvl");
			int price = tradeOffer.getAdjustedFirstBuyItem().getCount();
			BookOffer bookOffer = new BookOffer(enchantment, level, price);
			
			// 过滤无效附魔书
			if(!bookOffer.isValid())
			{
				System.out.println("检测到无效附魔书交易项。\n"
					+ "NBT数据: " + stack.getNbt());
				continue;
			}
			
			return bookOffer;
		}
		
		return null;
	}
	
	/**
	 * 筛选目标图书管理员村民（1级、无交易经验、范围内）
	 */
	private void setTargetVillager()
	{
		ClientPlayerEntity player = MC.player;
		double rangeSq = range.getValueSq();
		
		// 流式筛选符合条件的村民
		Stream<VillagerEntity> stream =
			StreamSupport.stream(MC.world.getEntities().spliterator(), true)
				.filter(e -> !e.isRemoved())					// 未被移除的实体
				.filter(VillagerEntity.class::isInstance)		// 村民实体
				.map(e -> (VillagerEntity)e)
				.filter(e -> e.getHealth() > 0)				// 存活状态
				.filter(e -> player.squaredDistanceTo(e) <= rangeSq) // 范围内
				.filter(e -> e.getVillagerData()
					.getProfession() == VillagerProfession.LIBRARIAN) // 图书管理员
				.filter(e -> e.getVillagerData().getLevel() == 1)	// 1级村民
				.filter(e -> !experiencedVillagers.contains(e));	// 无交易经验
		
		// 选择距离最近的村民
		villager = stream
			.min(Comparator.comparingDouble(e -> player.squaredDistanceTo(e)))
			.orElse(null);
		
		// 未找到符合条件的村民
		if(villager == null)
		{
			String errorMsg = "未找到附近的1级图书管理员村民。";
			int numExperienced = experiencedVillagers.size();
			if(numExperienced > 0)
				errorMsg += " （排除了 " + numExperienced + " 个"
					+ (numExperienced == 1 ? "已" : "均")
					+ "有交易经验的村民）。";
			
			ChatUtils.error(errorMsg);
			ChatUtils.message("请确保图书管理员村民和讲台均在你的可交互范围内。");
			setEnabled(false);
			return;
		}
		
		System.out.println("找到目标村民，位置：" + villager.getBlockPos());
	}
	
	/**
	 * 筛选目标讲台（距离村民最近的讲台）
	 */
	private void setTargetJobSite()
	{
		Vec3d eyesVec = RotationUtils.getEyesPos();
		double rangeSq = range.getValueSq();
		
		// 流式筛选范围内的讲台
		Stream<BlockPos> stream = BlockUtils
			.getAllInBoxStream(BlockPos.ofFloored(eyesVec),
				range.getValueCeil())
			.filter(pos -> eyesVec
				.squaredDistanceTo(Vec3d.ofCenter(pos)) <= rangeSq) // 范围内
			.filter(pos -> BlockUtils.getBlock(pos) == Blocks.LECTERN); // 讲台方块
		
		// 选择距离村民最近的讲台
		jobSite = stream
			.min(Comparator.comparingDouble(
				pos -> villager.squaredDistanceTo(Vec3d.ofCenter(pos))))
			.orElse(null);
		
		// 未找到讲台
		if(jobSite == null)
		{
			ChatUtils.error("未找到该图书管理员的讲台！");
			ChatUtils.message("请确保图书管理员村民和讲台均在你的可交互范围内。");
			setEnabled(false);
			return;
		}
		
		System.out.println("找到目标讲台，位置：" + jobSite);
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		int green = 0xC000FF00; // 绿色（目标村民/讲台）
		int red = 0xC0FF0000;   // 红色（已经验证的村民）
		
		// 渲染目标村民边框
		if(villager != null)
			RenderUtils.drawOutlinedBox(matrixStack, villager.getBoundingBox(),
				green, false);
		
		// 渲染目标讲台边框
		if(jobSite != null)
			RenderUtils.drawOutlinedBox(matrixStack, new Box(jobSite), green,
				false);
		
		// 渲染已经验证的村民边框（红色）
		List<Box> expVilBoxes = experiencedVillagers.stream()
			.map(VillagerEntity::getBoundingBox).toList();
		RenderUtils.drawOutlinedBoxes(matrixStack, expVilBoxes, red, false);
		RenderUtils.drawCrossBoxes(matrixStack, expVilBoxes, red, false);
		
		// 渲染破坏讲台的进度条
		if(breakingJobSite)
			overlay.render(matrixStack, partialTicks, jobSite);
	}
}