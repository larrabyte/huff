package dev.larrabyte.huff;

import java.lang.reflect.Field;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class ReachExtender {
    private Timer minecraftTimer = null;

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

    public double getPartialTicks(Minecraft instance) {
        return minecraftTimer.elapsedPartialTicks;
    }

    @SubscribeEvent
    public void onConnectedToServerEvent(ClientConnectedToServerEvent event) throws IllegalArgumentException, IllegalAccessException {
        // Create a reference to Minecraft's timer when we connect to a server.
        Minecraft instance = Minecraft.getMinecraft();
        Class<? extends Minecraft> instanceClass = instance.getClass();

        Field timerField = ReflectionHelper.findField(instanceClass, "timer", "field_71428_T");
        timerField.setAccessible(true);
        minecraftTimer = (Timer) timerField.get(instance);
    }
}
