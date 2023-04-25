package higtools.modules.borers;

import higtools.HIGTools;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.stream.IntStream;

import static higtools.utils.HIGUtils.*;

public abstract class BorerModule extends Module {
    // preserve 2 block tall tunnel for speed bypass
    protected final ArrayList<BlockPos> blackList = new ArrayList<>();
    /**
     * last time packets were sent
     */
    protected long lastUpdateTime = 0;
    /**
     * floored block position of player
     */
    protected BlockPos playerPos = BlockPos.ORIGIN;
    protected int packets = 0;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    protected final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("Shape")
        .description("The shape to dig.")
        .defaultValue(Mode.HIGHWAY)
        .build()
    );
    protected final Setting<Integer> extForward;
    protected final Setting<Integer> extBackward;
    protected final Setting<Integer> xOffset;
    protected final Setting<Integer> zOffset;
    protected final Setting<Integer> keepY = sgGeneral.add(new IntSetting.Builder()
        .name("KeepY")
        .description("Keeps a specific Y level when digging.")
        .defaultValue(119)
        .range(-1, 255)
        .sliderRange(-1, 255)
        .build()
    );

    private final Setting<Boolean> disable = sgGeneral.add(new BoolSetting.Builder()
        .name("Disable")
        .description("Disable the jumping block feature")
        .defaultValue(false)
        .build()
    );

    protected final Setting<Boolean> jumping = sgGeneral.add(new BoolSetting.Builder()
        .name("Jumping")
        .description("Send more or less packs")
        .defaultValue(false)
        .build()
    );

    protected BorerModule(String name, String description, int extForwards, int extBackwards, int xOffset, int zOffset) {
        super(HIGTools.BORERS, name, description);

        extForward = sgGeneral.add(new IntSetting.Builder()
            .name("ExtForward")
            .description("How many blocks to dig forwards.")
            .defaultValue(extForwards)
            .range(1, 6)
            .build()
        );

        extBackward = sgGeneral.add(new IntSetting.Builder()
            .name("ExtBackward")
            .description("How many blocks to dig backwards.")
            .defaultValue(extBackwards)
            .range(1, 6)
            .build()
        );

        this.xOffset = sgGeneral.add(new IntSetting.Builder()
            .name("XOffset")
            .description("How many blocks to dig on the x axis.")
            .defaultValue(xOffset)
            .range(-2, 2)
            .sliderRange(-2, 2)
            .build()
        );

        this.zOffset = sgGeneral.add(new IntSetting.Builder()
            .name("ZOffset")
            .description("How many blocks to dig on the z axis.")
            .defaultValue(zOffset)
            .range(-2, 2)
            .sliderRange(-2, 2)
            .build()
        );
    }

    @EventHandler
    public abstract void tick(TickEvent.Pre event);

    protected void getBlacklistedBlockPoses() {
        blackList.clear();
        if (getHighway() >= 1 && getHighway() <= 4) {
            blackList.add(playerPos.up(2));
            blackList.add(backward(playerPos.up(2), 1));
            blackList.add(backward(playerPos.up(2), 2));
            blackList.add(forward(playerPos, 1).up(2));
            blackList.add(forward(playerPos, 2).up(2));
            blackList.add(forward(playerPos, 3).up(2));
            blackList.add(forward(playerPos, 4).up(2));
            blackList.add(forward(playerPos, 5).up(2));
        } else {
            float f = MathHelper.sin(mc.player.getYaw() * 0.017453292f);
            float g = MathHelper.cos(mc.player.getYaw() * 0.017453292f);
            IntStream.rangeClosed(-2, 5).forEach(i -> {
                Vec3d pos = mc.player.getPos().add(-f * i, 20., g * i);
                blackList.add(BlockPos.ofFloored(pos));
                blackList.add(left(BlockPos.ofFloored(pos), 1));
                blackList.add(left(BlockPos.ofFloored(pos), 2));
                blackList.add(right(BlockPos.ofFloored(pos), 1));
            });
        }
    }

    protected void do2x3(BlockPos playerPos) {
        IntStream.rangeClosed(-extBackward.get(), extForward.get()).forEach(i -> {
                breakBlock(forward(playerPos, i));
                breakBlock(forward(playerPos, i).up());
                breakBlock(forward(playerPos, i).up(2));
                breakBlock(left(forward(playerPos, i), 1));
                breakBlock(left(forward(playerPos, i), 1).up());
                breakBlock(left(forward(playerPos, i), 1).up(2));
            }
        );
    }

    protected void doHighway4(BlockPos playerPos) {
        IntStream.rangeClosed(-extBackward.get(), extForward.get()).forEach(i -> {
            breakBlock(forward(playerPos, i));
            breakBlock(forward(playerPos, i).up());
            breakBlock(forward(playerPos, i).up(2));
            breakBlock(forward(playerPos, i).up(3));

            breakBlock(right(forward(playerPos, i), 1));
            breakBlock(right(forward(playerPos, i), 1).up());
            breakBlock(right(forward(playerPos, i), 1).up(2));
            breakBlock(right(forward(playerPos, i), 1).up(3));

            breakBlock(right(forward(playerPos, i), 2).up());
            breakBlock(right(forward(playerPos, i), 2).up(2));
            breakBlock(right(forward(playerPos, i), 2).up(3));

            breakBlock(left(forward(playerPos, i), 1));
            breakBlock(left(forward(playerPos, i), 1).up());
            breakBlock(left(forward(playerPos, i), 1).up(2));
            breakBlock(left(forward(playerPos, i), 1).up(3));

            breakBlock(left(forward(playerPos, i), 2));
            breakBlock(left(forward(playerPos, i), 2).up());
            breakBlock(left(forward(playerPos, i), 2).up(2));
            breakBlock(left(forward(playerPos, i), 2).up(3));

            breakBlock(left(forward(playerPos, i), 3).up());
            breakBlock(left(forward(playerPos, i), 3).up(2));
            breakBlock(left(forward(playerPos, i), 3).up(3));
        });
    }

    protected void breakBlock(BlockPos blockPos) {
        if (packets >= 130 || mc.world.getBlockState(blockPos).getMaterial().isReplaceable() || (blackList.contains(blockPos) && disable.get())) {
            return;
        }

        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, Direction.UP));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, Direction.UP));
        packets += 2;
    }

    public enum Mode {
        THIN,
        HIGHWAY
    }
}
