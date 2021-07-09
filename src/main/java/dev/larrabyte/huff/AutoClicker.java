package dev.larrabyte.huff;

import net.minecraft.init.Items;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.client.Minecraft;

import net.minecraft.block.material.Material;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;

public class AutoClicker {
    // AutoClicker constants (ask boggle).
    private static final long MAX_CPS = 14;
    private static final long MIN_CPS = 8;
    private static final long WINDOW_SIZE = 2;
    private static final long GRANULARITY = 1000;

    // EPIC(TM) random number generator.
    private static final long SEED = 68954012663L;
    private static MersenneTwister rand = new MersenneTwister(SEED);
    private static WaitTimer timer = new WaitTimer();

    private static long rangeCap = (MAX_CPS + MIN_CPS + WINDOW_SIZE) * (GRANULARITY / 2);
    private static long rangeFloor = (MAX_CPS + MIN_CPS - WINDOW_SIZE) * (GRANULARITY / 2);

    private long computeDelay() {
        long cps = rand.nextLong(rangeFloor, rangeCap + 1) / GRANULARITY;

        // Convert CPS to delay (in nanoseconds).
        double adjustmentFactor = rand.nextDouble(0.8, 1.2);
        long delay = (long) ((1000000000 / cps) * adjustmentFactor);

        // Randomly shift the values of rangeCap and rangeFloor.
        boolean shifting = rand.nextBoolean();
        long rawShiftValue = rand.nextLong(GRANULARITY + 1);
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

    private boolean shouldClick(Minecraft instance) {
        boolean pressed = instance.gameSettings.keyBindAttack.isKeyDown();
        boolean nonNull = instance.thePlayer.getCurrentEquippedItem() != null;

        if(nonNull) {
            boolean isSword = instance.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemSword;
            boolean isStick = instance.thePlayer.inventory.getCurrentItem().getItem() == Items.stick;
            return pressed && nonNull && (isSword || isStick);
        }

        return false;
    }

    private void clickMouseImplementation(Minecraft instance) {
        instance.thePlayer.swingItem();

        switch (instance.objectMouseOver.typeOfHit) {
            case ENTITY: {
                instance.playerController.attackEntity(instance.thePlayer, instance.objectMouseOver.entityHit);
                break;
            }

            case BLOCK: {
                BlockPos blockpos = instance.objectMouseOver.getBlockPos();
                Material blockmat = instance.theWorld.getBlockState(blockpos).getBlock().getMaterial();

                if (blockmat != Material.air) {
                    instance.playerController.clickBlock(blockpos, instance.objectMouseOver.sideHit);
                    break;
                }
            }

            case MISS: {
                break;
            }
        }
    }

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        Minecraft instance = Minecraft.getMinecraft();

        // If clicking and scrolling, cancel this event.
        if(this.shouldClick(instance) && event.dwheel != 0) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onTickEvent(PlayerTickEvent event) {
        Minecraft instance = Minecraft.getMinecraft();

        if(this.shouldClick(instance)) {
            long delay = this.computeDelay();

            // If enough nanoseconds have passed, click and reset.
            if(timer.hasTimeElapsed(delay)) {
                this.clickMouseImplementation(instance);
                timer.reset();
            }
        }

        else {
            timer.reset();
        }
    }
}
