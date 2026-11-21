/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.List;
import java.util.Random;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AimAtSetting;
import net.wurstclient.settings.AimAtSetting.AimAt;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filters.FilterBabiesSetting;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"喂养光环", "feed aura", "BreedAura", "breed aura", "AutoBreeder",
	"auto breeder"})
public final class FeedAuraHack extends Hack
	implements PreMotionListener, HandleInputListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("范围", "决定喂养光环可以喂食动物的距离。\n" + "任何超出指定值的动物都不会被喂食。", 5,
			1, 10, 0.05, ValueDisplay.DECIMAL);
	
	private final FilterBabiesSetting filterBabies =
		new FilterBabiesSetting("不会喂食幼年动物。\n" + "节省食物，但不会加速幼年动物的生长。", true);
	
	private final AimAtSetting aimAt =
		new AimAtSetting(AimAtSetting.genericDescription(this), AimAt.AUTO);
	
	private final CheckboxSetting filterUntamed =
		new CheckboxSetting("过滤未驯服", "不会喂食尚未被驯服的可驯服动物。", false);
	
	private final CheckboxSetting filterHorses = new CheckboxSetting("过滤马类动物",
		"不会喂食马、羊驼、驴等。\n" + "推荐使用，因为Minecraft bug MC-233276会导致这些动物无限消耗物品。",
		true);
	
	private final Random random = new Random();
	private AnimalEntity clickTarget;
	private AnimalEntity renderTarget;
	
	public FeedAuraHack()
	{
		super("喂养光环");
		setCategory(Category.OTHER);
		addSetting(range);
		addSetting(aimAt);
		addSetting(filterBabies);
		addSetting(filterUntamed);
		addSetting(filterHorses);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PreMotionListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		clickTarget = null;
		renderTarget = null;
	}
	
	@Override
	public void onPreMotion()
	{
		ItemStack heldStack = MC.player.getInventory().getMainHandStack();
		double rangeSq = range.getValueSq();
		
		List<AnimalEntity> targets = EntityUtils.getValidAnimals()
			.filter(e -> EntityUtils.distanceToHitboxSq(e) <= rangeSq)
			.filter(e -> e.isBreedingItem(heldStack))
			.filter(AnimalEntity::canEat)
			.filter(e -> !filterBabies.isChecked() || filterBabies.test(e))
			.filter(e -> !filterUntamed.isChecked() || !isUntamed(e))
			.filter(e -> !filterHorses.isChecked()
				|| !(e instanceof AbstractHorseEntity))
			.toList();
		
		if(targets.isEmpty())
		{
			clickTarget = null;
			renderTarget = null;
			return;
		}
		
		clickTarget = targets.get(random.nextInt(targets.size()));
		renderTarget = clickTarget;
		WURST.getRotationFaker()
			.faceVectorPacket(aimAt.getAimPoint(clickTarget));
	}
	
	@Override
	public void onHandleInput()
	{
		if(clickTarget == null)
			return;
		
		ClientPlayerInteractionManager im = MC.interactionManager;
		ClientPlayerEntity player = MC.player;
		Hand hand = Hand.MAIN_HAND;
		
		if(im.isBreakingBlock() || player.isRiding())
			return;
		
		EntityHitResult hitResult = EntityUtils.createHitResult(clickTarget,
			aimAt.getAimPoint(clickTarget));
		
		ActionResult actionResult =
			im.interactEntityAtLocation(player, clickTarget, hitResult, hand);
		
		if(!actionResult.isAccepted())
			actionResult = im.interactEntity(player, clickTarget, hand);
		
		if(actionResult.isAccepted() && actionResult.shouldSwingHand())
			player.swingHand(hand);
		
		clickTarget = null;
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(renderTarget == null)
			return;
		
		float p = 1;
		if(renderTarget.getMaxHealth() > 1e-5)
			p = renderTarget.getHealth() / renderTarget.getMaxHealth();
		float green = p * 2F;
		float red = 2 - green;
		float[] rgb = {red, green, 0};
		int quadColor = RenderUtils.toIntColor(rgb, 0.25F);
		int lineColor = RenderUtils.toIntColor(rgb, 0.5F);
		
		Box box = EntityUtils.getLerpedBox(renderTarget, partialTicks);
		if(p < 1)
			box = box.contract((1 - p) * 0.5 * box.getXLength(),
				(1 - p) * 0.5 * box.getYLength(),
				(1 - p) * 0.5 * box.getZLength());
		
		RenderUtils.drawSolidBox(matrixStack, box, quadColor, false);
		RenderUtils.drawOutlinedBox(matrixStack, box, lineColor, false);
	}
	
	private boolean isUntamed(AnimalEntity e)
	{
		return e instanceof AbstractHorseEntity horse && !horse.isTame()
			|| e instanceof TameableEntity tame && !tame.isTamed();
	}
}
