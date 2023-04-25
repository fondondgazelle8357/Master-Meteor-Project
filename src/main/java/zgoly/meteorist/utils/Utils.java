package zgoly.meteorist.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Utils {
    /** Checks if {@link Box} has collisions with entities. */
    public static boolean isCollidesEntity(Box b) {
        if (mc.world == null) return false;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity)) return false;
            if (b.intersects(entity.getBoundingBox())) return true;
        }
        return false;
    }

    /** Checks if {@link BlockPos} has collisions with entities. */
    public static boolean isCollidesEntity(BlockPos b) {
        return isCollidesEntity(new Box(b));
    }
}