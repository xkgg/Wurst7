/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FaceTargetSetting;
import net.wurstclient.settings.FaceTargetSetting.FaceTarget;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InteractionSimulator;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"build random", "RandomBuild", "random build", "PlaceRandom",
	"place random", "RandomPlace", "random place"})
public final class BuildRandomHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("范围", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private SliderSetting maxAttempts = new SliderSetting("最大尝试次数",
		"BuildRandom在一个tick中尝试放置方块的最大随机位置数。\n\n" + "更高的值以增加延迟为代价加快建筑速度。", 128,
		1, 1024, 1, ValueDisplay.INTEGER);
	
	private final CheckboxSetting checkItem = new CheckboxSetting("检查手持物品",
		"只有当你实际拿着方块时才建筑。\n" + "关闭此选项可以使用火把、水、熔岩、生成蛋进行建筑，或者如果你只是想用空手在随机位置右键点击。",
		true);
	
	private final CheckboxSetting checkLOS =
		new CheckboxSetting("检查视线", "确保BuildRandom不会尝试在墙壁后面放置方块。", false);
	
	private final FaceTargetSetting faceTarget =
		FaceTargetSetting.withoutPacketSpam(this, FaceTarget.SERVER);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final CheckboxSetting fastPlace =
		new CheckboxSetting("Always FastPlace",
			"Builds as if FastPlace was enabled, even if it's not.", false);
	
	private final CheckboxSetting placeWhileBreaking = new CheckboxSetting(
		"Place while breaking",
		"Builds even while you are breaking a block.\n"
			+ "Possible with hacks, but wouldn't work in vanilla. May look suspicious.",
		false);
	
	private final CheckboxSetting placeWhileRiding = new CheckboxSetting(
		"Place while riding",
		"Builds even while you are riding a vehicle.\n"
			+ "Possible with hacks, but wouldn't work in vanilla. May look suspicious.",
		false);
	
	private final CheckboxSetting indicator =
		new CheckboxSetting("指示器", "显示BuildRandom正在放置方块的位置。", true);
	
	private final Random random = new Random();
	private BlockPos lastPos;
	
	public BuildRandomHack()
	{
		super("BuildRandom");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(maxAttempts);
		addSetting(checkItem);
		addSetting(checkLOS);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(fastPlace);
		addSetting(placeWhileBreaking);
		addSetting(placeWhileRiding);
		addSetting(indicator);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		lastPos = null;
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		lastPos = null;
		
		if(!fastPlace.isChecked() && MC.rightClickDelay > 0)
			return;
		
		if(checkItem.isChecked() && !MC.player.isHolding(
			stack -> !stack.isEmpty() && stack.getItem() instanceof BlockItem))
			return;
		
		if(!placeWhileBreaking.isChecked() && MC.gameMode.isDestroying())
			return;
		
		if(!placeWhileRiding.isChecked() && MC.player.isHandsBusy())
			return;
		
		int maxAttempts = this.maxAttempts.getValueI();
		int blockRange = range.getValueCeil();
		int bound = blockRange * 2 + 1;
		BlockPos pos;
		int attempts = 0;
		
		do
		{
			// generate random position
			pos = BlockPos.containing(RotationUtils.getEyesPos()).offset(
				random.nextInt(bound) - blockRange,
				random.nextInt(bound) - blockRange,
				random.nextInt(bound) - blockRange);
			attempts++;
			
		}while(attempts < maxAttempts && !tryToPlaceBlock(pos));
	}
	
	private boolean tryToPlaceBlock(BlockPos pos)
	{
		if(!BlockUtils.getState(pos).canBeReplaced())
			return false;
		
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
		if(params == null || params.distanceSq() > range.getValueSq()
			|| params.requiresSneaking())
			return false;
		if(checkLOS.isChecked() && !params.lineOfSight())
			return false;
		
		MC.rightClickDelay = 4;
		faceTarget.face(params.hitVec());
		lastPos = pos;
		
		InteractionSimulator.rightClickBlock(params.toHitResult(),
			swingHand.getSelected());
		return true;
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(lastPos == null || !indicator.isChecked())
			return;
		
		// Get colors
		float red = partialTicks * 2F;
		float green = 2 - red;
		float[] rgb = {red, green, 0};
		int quadColor = RenderUtils.toIntColor(rgb, 0.25F);
		int lineColor = RenderUtils.toIntColor(rgb, 0.5F);
		
		// Draw box
		AABB box = new AABB(lastPos);
		RenderUtils.drawSolidBox(matrixStack, box, quadColor, false);
		RenderUtils.drawOutlinedBox(matrixStack, box, lineColor, false);
	}
}
