package dev.larrabyte.huff;

import java.lang.reflect.Field;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;
import net.minecraft.util.Vec3;

public class ReachExtender {
    // It just works(TM).
    public Entity getEntityFromRaycast(Minecraft instance, double range, double partialTicks) {
        Entity renderViewEntity = instance.getRenderViewEntity();
        Entity pointedEntity = null;

        if(renderViewEntity != null && instance.theWorld != null) {
            Vec3 eyePosition = renderViewEntity.getPositionEyes((float) partialTicks);
            Vec3 entityLook = renderViewEntity.getLook((float) partialTicks);
            Vec3 vector = eyePosition.addVector(entityLook.xCoord * range, entityLook.yCoord * range, entityLook.zCoord * range);

            // Setup arguments for fetching entities.
            Predicate<Entity> predicate = Predicates.and(EntitySelectors.NOT_SPECTATING, Entity :: canBeCollidedWith);
            AxisAlignedBB aaBox = renderViewEntity.getEntityBoundingBox();
            aaBox = aaBox.addCoord(entityLook.xCoord * range, entityLook.yCoord * range, entityLook.zCoord * range);
            aaBox = aaBox.expand(1.0, 1.0, 1.0);

            for(Entity entity : instance.theWorld.getEntitiesInAABBexcluding(renderViewEntity, aaBox, predicate)) {
                float hitboxSize = entity.getCollisionBorderSize();
                AxisAlignedBB axisAlignedBB = entity.getEntityBoundingBox().expand(hitboxSize, hitboxSize, hitboxSize);
                MovingObjectPosition movingObjectPosition = axisAlignedBB.calculateIntercept(eyePosition, vector);

                if(axisAlignedBB.isVecInside(eyePosition)) {
                    if(range >= 0.0D) {
                        pointedEntity = entity;
                        range = 0.0D;
                    }
                }

                else if(movingObjectPosition != null) {
                    double eyeDistance = eyePosition.distanceTo(movingObjectPosition.hitVec);

                    if(eyeDistance < range || range == 0.0D) {
                        if(entity == renderViewEntity.ridingEntity && !renderViewEntity.canRiderInteract()) {
                            if(range == 0.0D) {
                                pointedEntity = entity;
                            }
                        } else {
                            pointedEntity = entity;
                            range = eyeDistance;
                        }
                    }
                }
            }
        }

        return pointedEntity;
    }

    public double getReachDistance(Minecraft instance) {
        boolean moving = instance.thePlayer.movementInput.moveForward == 0 && instance.thePlayer.movementInput.moveStrafe == 0;
        boolean sprinting = instance.thePlayer.isSprinting();

        if(!moving) {
            return Main.rand.nextDouble(3.1, 3.4);
        } else if(sprinting) {
            return Main.rand.nextDouble(3.2, 3.6);
        } else {
            return Main.rand.nextDouble(3.15, 3.4);
        }
    }

    public double getPartialTicks(Minecraft instance) {
        // We need reflection to access Minecraft's timer.
        Class<? extends Minecraft> instanceClass = instance.getClass();
        double partialTicks = 0.0;

        try {
            Field timerField = instanceClass.getDeclaredField("timer");
            timerField.setAccessible(true);
            Timer timer = (Timer) timerField.get(instance);
            partialTicks = (double) timer.elapsedPartialTicks;
        }

        catch(Throwable e) {
            // We shouldn't ever end up here.
            System.out.println(e);
        }

        return partialTicks;
    }
}
