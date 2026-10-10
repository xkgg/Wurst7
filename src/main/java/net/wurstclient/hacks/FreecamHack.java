/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;

import net.minecraft.entity.Entity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.CameraTransformViewBobbingListener.CameraTransformViewBobbingEvent;
import net.wurstclient.events.MouseScrollListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.VisGraphListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.freecam.FreecamInitialPosSetting;
import net.wurstclient.hacks.freecam.FreecamInputSetting;
import net.wurstclient.hacks.freecam.FreecamInputSetting.ApplyInputTo;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.InteractFromSetting;
import net.wurstclient.settings.InteractFromSetting.InteractFrom;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

@DontSaveState
@SearchTags({"灵魂出窍", "free camera", "spectator"})
public final class FreecamHack extends Hack
	implements UpdateListener, VisGraphListener,
	CameraTransformViewBobbingListener, RenderListener, MouseScrollListener
{
	private final FreecamInputSetting applyInputTo = new FreecamInputSetting();
	
	private final InteractFromSetting interactFrom = new InteractFromSetting(
		"description.wurst.setting.freecam.interact_from", InteractFrom.CAMERA);
	
	private final SliderSetting horizontalSpeed = new SliderSetting("水平速度",
		"description.wurst.setting.freecam.horizontal_speed", 1, 0.05, 10, 0.05,
		ValueDisplay.DECIMAL);
	
	private final SliderSetting verticalSpeed = new SliderSetting("垂直速度",
		"description.wurst.setting.freecam.vertical_speed", 1, 0.05, 5, 0.05,
		v -> ValueDisplay.DECIMAL.getValueString(getActualVerticalSpeed()));
	
	private final CheckboxSetting scrollToChangeSpeed =
		new CheckboxSetting("滚轮调整速度",
			"description.wurst.setting.freecam.scroll_to_change_speed", true);
	
	private final CheckboxSetting renderSpeed =
		new CheckboxSetting("在 HackList 显示速度",
			"description.wurst.setting.freecam.show_speed_in_hacklist", true);
	
	private final FreecamInitialPosSetting initialPos =
		new FreecamInitialPosSetting();
	
	private final CheckboxSetting tracer =
		new CheckboxSetting("追踪线", "绘制一条线到你角色的实际位置。", false);
	
	private final ColorSetting color = new ColorSetting("追踪线颜色", Color.WHITE);
	
	private final CheckboxSetting hideHand =
		new CheckboxSetting("隐藏手部", "启用 Freecam 时隐藏第一人称手部和手持物。", true);
	
	private final CheckboxSetting disableOnDamage =
		new CheckboxSetting("受伤时关闭", "生命值减少时自动关闭 Freecam。", true);
	
	private final CheckboxSetting reloadChunks =
		new CheckboxSetting("关闭时重载区块", "关闭 Freecam 时重新载入区块。", true);
	
	private Vec3d camPos;
	private Vec3d prevCamPos;
	private float camYaw;
	private float camPitch;
	private float lastHealth = Float.MIN_VALUE;
	
	public FreecamHack()
	{
		super("灵魂出窍");
		setCategory(Category.RENDER);
		addSetting(applyInputTo);
		addSetting(interactFrom);
		addSetting(horizontalSpeed);
		addSetting(verticalSpeed);
		addSetting(scrollToChangeSpeed);
		addSetting(renderSpeed);
		addSetting(initialPos);
		addSetting(tracer);
		addSetting(color);
		addSetting(hideHand);
		addSetting(disableOnDamage);
		addSetting(reloadChunks);
	}
	
	@Override
	public String getRenderName()
	{
		if(!renderSpeed.isChecked())
			return getName();
		
		return getName() + " [" + horizontalSpeed.getValueString() + ", "
			+ verticalSpeed.getValueString() + "]";
	}
	
	@Override
	protected void onEnable()
	{
		Entity cameraEntity =
			MC.getCameraEntity() != null ? MC.getCameraEntity() : MC.player;
		WurstClient.INSTANCE.getHax().remoteViewHack.setEnabled(false);
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(VisGraphListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(MouseScrollListener.class, this);
		
		lastHealth = Float.MIN_VALUE;
		camPos = initialPos.getSelected().getPos(cameraEntity);
		prevCamPos = camPos;
		camYaw = cameraEntity.getYaw();
		camPitch = cameraEntity.getPitch();
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(VisGraphListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(MouseScrollListener.class, this);
		
		lastHealth = Float.MIN_VALUE;
		
		if(reloadChunks.isChecked())
			MC.worldRenderer.reload();
	}
	
	@Override
	public void onUpdate()
	{
		float currentHealth = MC.player.getHealth();
		if(disableOnDamage.isChecked() && currentHealth < lastHealth)
		{
			setEnabled(false);
			return;
		}
		lastHealth = currentHealth;
		
		if(!isMovingCamera() || MC.currentScreen != null)
		{
			prevCamPos = camPos;
			return;
		}
		
		Vec2f moveVector = MC.player.input.getMovementInput();
		double yawRad = MC.gameRenderer.getCamera().getYaw()
			* MathHelper.RADIANS_PER_DEGREE;
		double sinYaw = Math.sin(yawRad);
		double cosYaw = Math.cos(yawRad);
		double offsetX = moveVector.x * cosYaw - moveVector.y * sinYaw;
		double offsetZ = moveVector.x * sinYaw + moveVector.y * cosYaw;
		
		double offsetY = 0;
		double vSpeed = getActualVerticalSpeed();
		if(IKeyBinding.get(MC.options.jumpKey).isActuallyDown())
			offsetY += vSpeed;
		if(IKeyBinding.get(MC.options.sneakKey).isActuallyDown())
			offsetY -= vSpeed;
		
		Vec3d offset = new Vec3d(offsetX, 0, offsetZ)
			.multiply(horizontalSpeed.getValue()).add(0, offsetY, 0);
		prevCamPos = camPos;
		camPos = camPos.add(offset);
	}
	
	private double getActualVerticalSpeed()
	{
		return MathHelper.clamp(
			horizontalSpeed.getValue() * verticalSpeed.getValue(), 0.05, 10);
	}
	
	@Override
	public void onMouseScroll(double amount)
	{
		if(!isControllingScrollEvents())
			return;
		
		if(amount > 0)
			horizontalSpeed.increaseValue();
		else if(amount < 0)
			horizontalSpeed.decreaseValue();
	}
	
	public boolean isControllingScrollEvents()
	{
		return isMovingCamera() && scrollToChangeSpeed.isChecked()
			&& MC.currentScreen == null
			&& !WURST.getOtfs().zoomOtf.isControllingScrollEvents();
	}
	
	public boolean isMovingCamera()
	{
		return isEnabled() && applyInputTo.getSelected() == ApplyInputTo.CAMERA;
	}
	
	public boolean isClickingFromCamera()
	{
		return isEnabled() && interactFrom.getSelected() == InteractFrom.CAMERA;
	}
	
	@Override
	public void onVisGraph(VisGraphEvent event)
	{
		event.cancel();
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		event.cancel();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(!tracer.isChecked())
			return;
		
		int colorI = color.getColorI(0x80);
		double extraSize = 0.05;
		Box rawBox = EntityUtils.getLerpedBox(MC.player, partialTicks);
		Box box = rawBox.offset(0, extraSize, 0).expand(extraSize);
		RenderUtils.drawOutlinedBox(matrixStack, box, colorI, false);
		RenderUtils.drawTracer(matrixStack, partialTicks, rawBox.getCenter(),
			colorI, false);
	}
	
	public boolean shouldHideHand()
	{
		return isEnabled() && hideHand.isChecked();
	}
	
	public Vec3d getCamPos(float partialTicks)
	{
		return new Vec3d(MathHelper.lerp(partialTicks, prevCamPos.x, camPos.x),
			MathHelper.lerp(partialTicks, prevCamPos.y, camPos.y),
			MathHelper.lerp(partialTicks, prevCamPos.z, camPos.z));
	}
	
	public void turn(double deltaYaw, double deltaPitch)
	{
		camYaw += (float)(deltaYaw * 0.15);
		camPitch += (float)(deltaPitch * 0.15);
		camPitch = MathHelper.clamp(camPitch, -90, 90);
	}
	
	public float getCamYaw()
	{
		return camYaw;
	}
	
	public float getCamPitch()
	{
		return camPitch;
	}
	
	public Vec3d getScaledCamDir(double scale)
	{
		return Vec3d.fromPolar(new Vec2f(camPitch, camYaw)).multiply(scale);
	}
}
