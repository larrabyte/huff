package dev.larrabyte.huff;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.block.material.Material;

import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class AutoClicker {
    // AutoClicker constants (ask boggle).
    private final long MAX_CPS = 14;
    private final long MIN_CPS = 8;
    private final long WINDOW_SIZE = 2;
    private final long GRANULARITY = 1000;

    // Left-click related fields.
    private long rangeCap = (MAX_CPS + MIN_CPS + WINDOW_SIZE) * (GRANULARITY / 2);
    private long rangeFloor = (MAX_CPS + MIN_CPS - WINDOW_SIZE) * (GRANULARITY / 2);
    private WaitTimer timer = new WaitTimer();

    // Right-click related fields.
    private Field rightClickDelayTimerField = null;
    private boolean rightClickerEnabled = false;

    public boolean shouldLeftClick(Minecraft instance) {
        boolean pressed = instance.gameSettings.keyBindAttack.isKeyDown();
        boolean nonNull = instance.thePlayer.getCurrentEquippedItem() != null;

        if(nonNull) {
            boolean isSword = instance.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemSword;
            boolean isStick = instance.thePlayer.inventory.getCurrentItem().getItem() == Items.stick;
            return pressed && nonNull && (isSword || isStick);
        }

        return false;
    }

    public boolean shouldRightClick(Minecraft instance) {
        boolean pressed = instance.gameSettings.keyBindUseItem.isKeyDown();
        boolean nonNull = instance.thePlayer.getCurrentEquippedItem() != null;

        if(nonNull) {
            Item itemInHand = instance.thePlayer.inventory.getCurrentItem().getItem();
            List<Item> throwables = Arrays.asList(Items.experience_bottle, Items.snowball, Items.egg);

            boolean isThrowable = throwables.contains(itemInHand);
            boolean isBlock = itemInHand instanceof ItemBlock;
            boolean isSplashPotion = false;

            if(itemInHand instanceof ItemPotion) {
                int potionMetadata = instance.thePlayer.inventory.getCurrentItem().getMetadata();
                isSplashPotion = ItemPotion.isSplash(potionMetadata);
            }

            return rightClickerEnabled && pressed && (isBlock || isThrowable || isSplashPotion);
        }

        return false;
    }

    public long computeDelay() {
        long cps = Main.rand.nextLong(rangeFloor, rangeCap + 1) / GRANULARITY;

        // Convert CPS to delay (in nanoseconds).
        double adjustmentFactor = Main.rand.nextDouble(0.8, 1.2);
        long delay = (long) ((1000000000 / cps) * adjustmentFactor);

        // Randomly shift the values of rangeCap and rangeFloor.
        boolean shifting = Main.rand.nextBoolean();
        long rawShiftValue = Main.rand.nextLong(GRANULARITY + 1);
        long shiftAmount = shifting ? rawShiftValue : -rawShiftValue;

        boolean under = rangeFloor + shiftAmount < MIN_CPS * GRANULARITY;
        boolean over = rangeCap + shiftAmount > MAX_CPS * GRANULARITY;

        if(under || over) {
            // Do the opposite operation (move left -> move right, etc).
            rangeFloor -= shiftAmount;
            rangeCap -= shiftAmount;
        } else {
            // Otherwise, shift the window normally.
            rangeFloor += shiftAmount;
            rangeCap += shiftAmount;
        }

        return delay;
    }

    public void clickMouse(Minecraft instance) {
        instance.thePlayer.swingItem();

        // Use the ReachExtender instead of Minecraft's.
        double reachDistance = Main.reachExtender.getReachDistance(instance);
        double partialTicks = Main.reachExtender.getPartialTicks(instance);
        Entity entity = Main.reachExtender.getEntityFromRaycast(instance, reachDistance, partialTicks);

        if(entity != null) {
            instance.playerController.attackEntity(instance.thePlayer, entity);
        }

        else if(instance.objectMouseOver != null) {
            if(instance.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                BlockPos blockPos = instance.objectMouseOver.getBlockPos();
                Material blockMat = instance.theWorld.getBlockState(blockPos).getBlock().getMaterial();

                if(blockMat != Material.air) {
                    instance.playerController.clickBlock(blockPos, instance.objectMouseOver.sideHit);
                }
            }
        }
    }

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        Minecraft instance = Minecraft.getMinecraft();

        // If middle mouse is pressed, flip the right click boolean.
        if(event.button == 2 && event.buttonstate == true) {
            rightClickerEnabled = !rightClickerEnabled;
        }

        // If clicking and scrolling, cancel this event.
        if(shouldLeftClick(instance) && event.dwheel != 0) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onTickEvent(PlayerTickEvent event) throws IllegalArgumentException, IllegalAccessException {
        Minecraft instance = Minecraft.getMinecraft();

        if(shouldLeftClick(instance)) {
            long clickDelay = computeDelay();
            if(timer.hasTimeElapsed(clickDelay)) {
                clickMouse(instance);
                timer.reset();
            }
        }

        else {
            // No left click, reset the timer.
            timer.reset();
        }

        if(shouldRightClick(instance)) {
            rightClickDelayTimerField.setInt(instance, 0);
        }
    }

    @SubscribeEvent
    public void onConnectToServerEvent(ClientConnectedToServerEvent event) throws Exception {
        // Create a reference to the rightClickDelayTimer field when we connect.
        Minecraft instance = Minecraft.getMinecraft();
        Class<? extends Minecraft> instanceClass = instance.getClass();

        rightClickDelayTimerField = ReflectionHelper.findField(instanceClass, "rightClickDelayTimer", "field_71467_ac");
        rightClickDelayTimerField.setAccessible(true);
    }
}
