/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import net.minecraft.block.*;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FacingSetting;
import net.wurstclient.settings.FacingSetting.Facing;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.TakeItemsFromSetting;
import net.wurstclient.settings.TakeItemsFromSetting.TakeItemsFrom;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InteractionSimulator;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.text.WText;

@SearchTags({"骨粉光环", "bonemeal aura", "bone meal aura", "AutoBonemeal",
	"auto bonemeal", "auto bone meal", "fertilizer", "bmaura"})
public final class BonemealAuraHack extends Hack implements HandleInputListener
{
	private final SliderSetting range =
		new SliderSetting("范围", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting multiMeal = new CheckboxSetting("多目标施肥",
		"description.wurst.setting.bonemealaura.multimeal", false);
	
	private final CheckboxSetting checkLOS = new CheckboxSetting("检查视线",
		"description.wurst.setting.bonemealaura.check_los", true);
	
	private final FacingSetting faceTarget = FacingSetting.withPacketSpam(
		"朝向目标", "description.wurst.setting.bonemealaura.face_target",
		Facing.SERVER);
	
	private final SwingHandSetting swingHand = new SwingHandSetting(
		WText.translated("description.wurst.setting.bonemealaura.swing_hand"),
		SwingHand.CLIENT);
	
	private final CheckboxSetting fastPlace = new CheckboxSetting("始终快速使用",
		"description.wurst.setting.bonemealaura.always_fastplace", true);
	
	private final CheckboxSetting useWhileBreaking =
		new CheckboxSetting("挖掘时使用",
			"description.wurst.setting.bonemealaura.use_while_breaking", false);
	
	private final CheckboxSetting useWhileRiding = new CheckboxSetting("骑乘时使用",
		"description.wurst.setting.bonemealaura.use_while_riding", false);
	
	private final TakeItemsFromSetting takeItemsFrom =
		TakeItemsFromSetting.withHands(
			WText.translated(
				"description.wurst.setting.bonemealaura.take_items_from"),
			TakeItemsFrom.HANDS);
	
	private final CheckboxSetting saplings = new CheckboxSetting("树苗", true);
	
	private final CheckboxSetting crops =
		new CheckboxSetting("农作物", "小麦、胡萝卜、马铃薯和甜菜根。", true);
	
	private final CheckboxSetting stems =
		new CheckboxSetting("茎", "南瓜和西瓜。", true);
	
	private final CheckboxSetting cocoa = new CheckboxSetting("可可豆", true);
	
	private final CheckboxSetting seaPickles = new CheckboxSetting("海泡菜", true);
	
	private final CheckboxSetting other = new CheckboxSetting("其他", false);
	
	public BonemealAuraHack()
	{
		super("骨粉光环");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(multiMeal);
		addSetting(checkLOS);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(fastPlace);
		addSetting(useWhileBreaking);
		addSetting(useWhileRiding);
		addSetting(takeItemsFrom);
		addSetting(saplings);
		addSetting(crops);
		addSetting(stems);
		addSetting(cocoa);
		addSetting(seaPickles);
		addSetting(other);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(HandleInputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(HandleInputListener.class, this);
	}
	
	@Override
	public void onHandleInput()
	{
		if(!fastPlace.isChecked() && MC.itemUseCooldown > 0)
			return;
		
		if(!useWhileBreaking.isChecked()
			&& MC.interactionManager.isBreakingBlock())
			return;
		
		if(!useWhileRiding.isChecked() && MC.player.isRiding())
			return;
		
		if(WURST.getHax().autoFarmHack.isBusy())
			return;
		
		boolean holdingBoneMeal = MC.player.isHolding(Items.BONE_MEAL);
		int boneMealSlot = InventoryUtils.indexOf(Items.BONE_MEAL,
			takeItemsFrom.getMaxInvSlot());
		if(!holdingBoneMeal && boneMealSlot < 0)
			return;
		
		List<BlockBreakingParams> validBlocks = getValidBlocks();
		if(validBlocks.isEmpty())
			return;
		
		if(!holdingBoneMeal)
		{
			InventoryUtils.selectItem(boneMealSlot);
			return;
		}
		
		if(multiMeal.isChecked())
		{
			for(BlockBreakingParams params : validBlocks)
			{
				faceTarget.getSelected().face(params.hitVec());
				InteractionSimulator.rightClickBlock(params.toHitResult(),
					SwingHand.OFF);
			}
			
			swingHand.swing(Hand.MAIN_HAND);
			return;
		}
		
		BlockBreakingParams params = validBlocks.get(0);
		MC.itemUseCooldown = 4;
		faceTarget.getSelected().face(params.hitVec());
		InteractionSimulator.rightClickBlock(params.toHitResult(),
			swingHand.getSelected());
	}
	
	private List<BlockBreakingParams> getValidBlocks()
	{
		BlockPos eyesBlock = BlockPos.ofFloored(MC.player.getEyePos());
		double rangeSq = range.getValueSq();
		
		Stream<BlockBreakingParams> stream = BlockUtils
			.getAllInBoxStream(eyesBlock, range.getValueCeil())
			.filter(this::isCorrectBlock)
			.map(BlockBreaker::getBlockBreakingParams).filter(Objects::nonNull)
			.filter(params -> params.distanceSq() <= rangeSq);
		
		if(checkLOS.isChecked())
			stream = stream.filter(BlockBreakingParams::lineOfSight);
			
		// Bone-mealed plants can block line of sight to other plants, so
		// process
		// the farthest targets first.
		return stream.sorted(Comparator
			.comparingDouble(BlockBreakingParams::distanceSq).reversed())
			.toList();
	}
	
	private boolean isCorrectBlock(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos);
		BlockState state = BlockUtils.getState(pos);
		
		if(!(block instanceof Fertilizable fertilizable)
			|| !fertilizable.isFertilizable(MC.world, pos, state, true))
			return false;
		
		if(block instanceof GrassBlock)
			return false;
		
		if(block instanceof SaplingBlock)
			return saplings.isChecked();
		
		if(block instanceof CropBlock)
			return crops.isChecked();
		
		if(block instanceof StemBlock)
			return stems.isChecked();
		
		if(block instanceof CocoaBlock)
			return cocoa.isChecked();
		
		if(block instanceof SeaPickleBlock)
			return seaPickles.isChecked();
		
		return other.isChecked();
	}
}
