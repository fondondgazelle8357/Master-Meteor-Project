package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import pwn.noobs.trouserstreak.Trouser;


/**
 * @Author majorsopa
 * https://github.com/majorsopa
 * @Author evaan
 * https://github.com/evaan
 * @Author etianll
 * https://github.com/etianl
 */
public class AutoMountain extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBuild = settings.createGroup("Build Options");
    private final SettingGroup sgTimings = settings.createGroup("Timings");

    private final SettingGroup sgRender = settings.createGroup("Render");

    public final Setting<Boolean> startPaused = sgGeneral.add(new BoolSetting.Builder()
            .name("Start Paused")
            .description("AutoMountain is Paused when module activated, for more control.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Integer> spcoffset = sgBuild.add(new IntSetting.Builder()
            .name("OnDemandSpacing")
            .description("Amount of space in blocks between stairs when pressing jumpKey.")
            .defaultValue(1)
            .min(1)
            .max(2)
            .build());

    public final Setting<Double> StairTimer = sgTimings.add(new DoubleSetting.Builder()
            .name("TimerMultiplier")
            .description("The multiplier value for Timer.")
            .defaultValue(1)
            .sliderRange(0.1, 10)
            .build()
    );
    private final Setting<Integer> spd = sgTimings.add(new IntSetting.Builder()
            .name("PlacementTickDelay")
            .description("Delay block placement to slow down the builder and to help SwapStackOnRunOut option.")
            .sliderRange(1, 5)
            .defaultValue(1)
            .build());
    private final Setting<Integer> munscher = sgTimings.add(new IntSetting.Builder()
            .name("DiagonalSwitchDelay")
            .description("Delays switching direction by this many ticks when building diagonally.")
            .sliderRange(1,10)
            .defaultValue(1)
            .build());
    public final Setting<Boolean> delayakick = sgTimings.add(new BoolSetting.Builder()
            .name("PauseBasedAntiKick")
            .description("Helps if you're flying, or sending too many packets.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Integer> delay = sgTimings.add(new IntSetting.Builder()
            .name("PauseForThisAmountOfTicks")
            .description("The amount of delay in ticks, when pausing. Useful if you're flying, or sending too many packets.")
            .defaultValue(5)
            .sliderRange(0, 100)
            .visible(() -> delayakick.get())
            .build()
    );
    private final Setting<Integer> offTime = sgTimings.add(new IntSetting.Builder()
            .name("TicksBetweenPause")
            .description("The amount of delay, in ticks, between pauses.")
            .defaultValue(20)
            .sliderRange(1, 200)
            .visible(() -> delayakick.get())
            .build()
    );

    private final Setting<Integer> limit = sgBuild.add(new IntSetting.Builder()
            .name("UpwardBuildLimit")
            .description("sets the Y level at which the stairs stop going up")
            .sliderRange(-64, 318)
            .defaultValue(318)
            .build());
    private final Setting<Integer> downlimit = sgBuild.add(new IntSetting.Builder()
            .name("DownwardBuildLimit")
            .description("sets the Y level at which the stairs stop going down")
            .sliderRange(-64, 318)
            .defaultValue(-64)
            .build());
    public final Setting<Boolean> InvertUpDir = sgBuild.add(new BoolSetting.Builder()
            .name("InvertDir@UpwardLimitOrCeiling")
            .description("Inverts Direction from up to down, shortly before you reach your set limit or a ceiling.")
            .defaultValue(false)
            .build()
    );
    public final Setting<Boolean> InvertDownDir = sgBuild.add(new BoolSetting.Builder()
            .name("InvertDir@DownwardLimitOrFloor")
            .description("Inverts Direction from down to up, shortly before you reach your set limit or a floor.")
            .defaultValue(false)
            .build()
    );
    public final Setting<Boolean> swap = sgGeneral.add(new BoolSetting.Builder()
            .name("SwapStackonRunOut")
            .description("Swaps to another stack of blocks in your hotbar when you run out")
            .defaultValue(true)
            .build()
    );
    public final Setting<Boolean> disabledisconnect = sgGeneral.add(new BoolSetting.Builder()
            .name("Disable On Disconnect")
            .description("Toggles the Module off when you disconnect.")
            .defaultValue(false)
            .build()
    );
    public final Setting<Boolean> lagpause = sgTimings.add(new BoolSetting.Builder()
            .name("Pause if Server Lagging")
            .description("Pause Builder if server is lagging")
            .defaultValue(true)
            .build()
    );
    private final Setting<Double> lag = sgTimings.add(new DoubleSetting.Builder()
            .name("How many seconds until pause")
            .description("Pause Builder if server is lagging for this many seconds.")
            .sliderRange(0, 10)
            .defaultValue(1)
            .visible(() -> lagpause.get())
            .build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Renders a block overlay where the next stair will be placed.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(() -> render.get())
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The color of the sides of the blocks being rendered.")
            .defaultValue(new SettingColor(255, 0, 255, 15))
            .visible(() -> render.get())
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The color of the lines of the blocks being rendered.")
            .defaultValue(new SettingColor(255, 0, 255, 255))
            .visible(() -> render.get())
            .build()
    );

    public AutoMountain() {
        super(Trouser.Main, "AutoMountain", "Make Mountains!");
    }
    private boolean pause = true;
    private boolean resetTimer;
    private float timeSinceLastTick;
    private int delayLeft = delay.get();
    private int offLeft = offTime.get();
    private BlockPos playerPos;
    private int cookie=0;
    private int speed=0;
    private boolean go=true;
    private float cookieyaw;

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        //Buttons in options that can control your character
        // North
        WButton north = table.add(theme.button("North")).expandX().minWidth(100).widget();
        north.action = () -> {
        if (mc.world.isChunkLoaded(mc.player.getChunkPos().getCenterX(),mc.player.getChunkPos().getCenterZ())){
            mc.player.setYaw(180);
            mc.player.setVelocity(0,0,0);
            PlayerUtils.centerPlayer();}};
        table.row();

        // East
        WButton east = table.add(theme.button("East")).expandX().minWidth(100).widget();
        east.action = () ->{
        if (mc.world.isChunkLoaded(mc.player.getChunkPos().getCenterX(),mc.player.getChunkPos().getCenterZ())){
            mc.player.setYaw(270);
            mc.player.setVelocity(0,0,0);
            PlayerUtils.centerPlayer();}};
        table.row();

        // South
        WButton south = table.add(theme.button("South")).expandX().minWidth(100).widget();
        south.action = () ->{
        if (mc.world.isChunkLoaded(mc.player.getChunkPos().getCenterX(),mc.player.getChunkPos().getCenterZ())){
            mc.player.setYaw(360);
            mc.player.setVelocity(0,0,0);
            PlayerUtils.centerPlayer();}};
        table.row();

        // West
        WButton west = table.add(theme.button("West")).expandX().minWidth(100).widget();
        west.action = () ->{
        if (mc.world.isChunkLoaded(mc.player.getChunkPos().getCenterX(),mc.player.getChunkPos().getCenterZ())){
            mc.player.setYaw(90);
            mc.player.setVelocity(0,0,0);
            PlayerUtils.centerPlayer();}};
        table.row();

        // Up
        WButton up = table.add(theme.button("Up")).expandX().minWidth(100).widget();
        up.action = () -> {
        if (mc.world.isChunkLoaded(mc.player.getChunkPos().getCenterX(),mc.player.getChunkPos().getCenterZ())){
            mc.player.setPitch(35);
            mc.player.setVelocity(0,0,0);
            PlayerUtils.centerPlayer();}};
        table.row();

        // Down
        WButton down = table.add(theme.button("Down")).expandX().minWidth(100).widget();
        down.action = () -> {
        if (mc.world.isChunkLoaded(mc.player.getChunkPos().getCenterX(),mc.player.getChunkPos().getCenterZ())){
            mc.player.setPitch(75);
            mc.player.setVelocity(0,0,0);
            PlayerUtils.centerPlayer();}};
        table.row();

        return table;
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen && disabledisconnect.get()) toggle();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (disabledisconnect.get()) toggle();
    }

    @Override
    public void onActivate() {
        mc.player.setPos(mc.player.getX(),Math.round(mc.player.getY()),mc.player.getZ());
        if (startPaused.get() == true){
        pause = false;
        error("Press UseKey (RightClick) to Build Stairs!");
        } else if (startPaused.get() == false){
            mc.player.setVelocity(0,0,0);
            pause = true;
        }
        resetTimer = false;
        playerPos = mc.player.getBlockPos();
        if (swap.get() && !startPaused.get()){
            cascadingpileof();
        }
        if (startPaused.get() || !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock) return;
        BlockPos pos = playerPos.add(new Vec3i(0,-1,0));
        if (mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);}
    }

    @Override
    public void onDeactivate() {
        speed=0;
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
        resetTimer = true;
        if (pause==false || !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock) return;
        BlockPos pos = playerPos.add(new Vec3i(0,-1,0));
        if (mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);}
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get()) {
            if (mc.options.jumpKey.isPressed()){
                if (mc.player.getPitch() <= 40){            //UP
                    switch (mc.player.getMovementDirection()) {
                        case NORTH -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(0, +spcoffset.get(), -1));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case SOUTH -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(0, +spcoffset.get(), 1));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case EAST -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(1, +spcoffset.get(), 0));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case WEST -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(-1, +spcoffset.get(), 0));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        default -> {
                        }
                    }
                }
                else if (mc.player.getPitch() >= 40){            //DOWN
                    switch (mc.player.getMovementDirection()) {
                        case NORTH -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(0, -spcoffset.get()-2, -1));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case SOUTH -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(0, -spcoffset.get()-2, 1));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case EAST -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(1, -spcoffset.get()-2, 0));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case WEST -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(-1, -spcoffset.get()-2, 0));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        default -> {
                        }
                    }
                }
            }
            else {
                if (mc.player.getPitch() <= 40) {            //UP
                    switch (mc.player.getMovementDirection()) {
                        case NORTH -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(0, 0, -1));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case SOUTH -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(0, 0, 1));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case EAST -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(1, 0, 0));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case WEST -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(-1, 0, 0));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        default -> {
                        }
                    }
                } else if (mc.player.getPitch() >= 40) {            //DOWN
                    switch (mc.player.getMovementDirection()) {
                        case NORTH -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(0, -2, -1));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case SOUTH -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(0, -2, 1));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case EAST -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(1, -2, 0));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        case WEST -> {
                            BlockPos pos1 = playerPos.add(new Vec3i(-1, -2, 0));
                            event.renderer.box(pos1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                        }
                        default -> {
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (mc.options.useKey.isPressed()){
            pause = pause ? false : true;
            mc.player.setVelocity(0,0,0);
            cookie=0;
            speed=0;
            Modules.get().get(Timer.class).setOverride(Timer.OFF);
            resetTimer = true;
            if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock) return;
            BlockPos pos = playerPos.add(new Vec3i(0,-1,0));
            if (mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                mc.player.swingHand(Hand.MAIN_HAND);}
        }
    }
    @EventHandler
    private void onKeyEvent(KeyEvent event) {
        if (pause == false)return;
        if (mc.options.forwardKey.isPressed()){
            mc.player.setPitch(35);
        }
        if (mc.options.backKey.isPressed()){
            mc.player.setPitch(75);
        }
        if ((lagpause.get() && timeSinceLastTick >= lag.get()) || !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock ||  pause == false) return;
        if (mc.options.leftKey.isPressed() && !mc.options.sneakKey.isPressed()){
            mc.player.setYaw(mc.player.getYaw()-90);
        }
        if (mc.options.rightKey.isPressed() && !mc.options.sneakKey.isPressed()){
            mc.player.setYaw(mc.player.getYaw()+90);
        }
    }
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket)
        ((PlayerMoveC2SPacketAccessor) event.packet).setOnGround(true);
    }
    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        playerPos = mc.player.getBlockPos();
        timeSinceLastTick = TickRate.INSTANCE.getTimeSinceLastTick();

        if (speed<spd.get()){
            go=false;
            speed++;
        }
        if (speed>=spd.get()){
            go=true;
            speed=0;
        }
        if (pause == false) return;
        if (!delayakick.get()){
            offLeft=666666666;
            delayLeft=0;
        }
        else if (delayakick.get() && offLeft>offTime.get()){
            offLeft=offTime.get();
        }
        mc.player.setVelocity(0,0,0);
        PlayerUtils.centerPlayer();
        mc.player.setPos(mc.player.getX(),Math.floor(mc.player.getY())+0.25,mc.player.getZ());
            if (Modules.get().get(Flight.class).isActive()) {
                Modules.get().get(Flight.class).toggle();
            }
            if (Modules.get().get(FlightAntikick.class).isActive()) {
                Modules.get().get(FlightAntikick.class).toggle();
            }
            if (Modules.get().get(TPFly.class).isActive()) {
                Modules.get().get(TPFly.class).toggle();
            }
            if (mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.AIR) {
                resetTimer = false;
                Modules.get().get(Timer.class).setOverride(StairTimer.get());
            } else if (!resetTimer) {
                Modules.get().get(Timer.class).setOverride(Timer.OFF);
                resetTimer = true;
            }
        if (swap.get()){
            cascadingpileof();
        }
        if ((lagpause.get() && timeSinceLastTick >= lag.get()) || !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock ||  pause == false || go==false) return;
        if (mc.options.sneakKey.isPressed() && mc.options.rightKey.isPressed() && delayLeft <= 0 && offLeft > 0){
            cookie++;
            if (cookie==munscher.get()){
                cookieyaw=mc.player.getYaw();
                mc.player.setYaw(mc.player.getYaw()+90);
            }else if (cookie>=munscher.get()+munscher.get()){
                mc.player.setYaw(mc.player.getYaw()-90);
                cookie=0;
            }
        }
        if (mc.options.sneakKey.isPressed() && mc.options.leftKey.isPressed() && delayLeft <= 0 && offLeft > 0){
            cookie++;
            if (cookie==munscher.get()){
                cookieyaw=mc.player.getYaw();
                mc.player.setYaw(mc.player.getYaw()-90);
            }else if (cookie>=munscher.get()+munscher.get()){
                mc.player.setYaw(mc.player.getYaw()+90);
                cookie=0;
            }
        }
        else if (!mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed() && cookie>=1){
            mc.player.setYaw(cookieyaw);
            cookieyaw=mc.player.getYaw();
            cookie=0;
        }
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (mc.player.getPitch() <= 40){
            if (delayLeft > 0) delayLeft--;
            else if ((!lagpause.get() || timeSinceLastTick < lag.get()) && delayLeft <= 0 && offLeft > 0 && pause==true && mc.player.getY() <= limit.get() &&  mc.player.getY() >= downlimit.get() ) {
                offLeft--;
                if (mc.player == null || mc.world == null) {toggle(); return;}
                if ((lagpause.get() && timeSinceLastTick >= lag.get()) || !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock ||  pause == false || go==false) return;
                switch (mc.player.getMovementDirection()) {
                    case NORTH -> {            //UP
                        if (mc.options.jumpKey.isPressed()){
                            BlockPos un1 = playerPos.add(new Vec3i(0,spcoffset.get()+2,0));
                            BlockPos un2 = playerPos.add(new Vec3i(0,spcoffset.get()+1,-1));
                            BlockPos un3 = playerPos.add(new Vec3i(0,spcoffset.get()+2,-1));
                            BlockPos un4 = playerPos.add(new Vec3i(0,spcoffset.get()+3,-1));
                            BlockPos pos = playerPos.add(new Vec3i(0,spcoffset.get(),-1));
                            if (!mc.world.getBlockState(un1).getMaterial().isSolid() && !mc.world.getBlockState(un2).getMaterial().isSolid() && !mc.world.getBlockState(un3).getMaterial().isSolid() && !mc.world.getBlockState(un4).getMaterial().isSolid() && !mc.world.getBlockState(un1).getMaterial().isLiquid() && !mc.world.getBlockState(un2).getMaterial().isLiquid() && !mc.world.getBlockState(un3).getMaterial().isLiquid() && !mc.world.getBlockState(un4).getMaterial().isLiquid() && !mc.world.getBlockState(un1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(un2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(un3).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(un4).getMaterial().equals(Material.POWDER_SNOW)){
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX(),mc.player.getY()+1+spcoffset.get(),mc.player.getZ()-1);
                            } else {if (InvertUpDir.get()) mc.player.setPitch(75);}
                        } else {
                            BlockPos un1 = playerPos.add(new Vec3i(0,2,0));
                            BlockPos un2 = playerPos.add(new Vec3i(0,1,-1));
                            BlockPos un3 = playerPos.add(new Vec3i(0,2,-1));
                            BlockPos un4 = playerPos.add(new Vec3i(0,3,-1));
                            BlockPos pos = playerPos.add(new Vec3i(0,0,-1));
                            if (!mc.world.getBlockState(un1).getMaterial().isSolid() && !mc.world.getBlockState(un2).getMaterial().isSolid() && !mc.world.getBlockState(un3).getMaterial().isSolid() && !mc.world.getBlockState(un4).getMaterial().isSolid() && !mc.world.getBlockState(un1).getMaterial().isLiquid() && !mc.world.getBlockState(un2).getMaterial().isLiquid() && !mc.world.getBlockState(un3).getMaterial().isLiquid() && !mc.world.getBlockState(un4).getMaterial().isLiquid() && !mc.world.getBlockState(un1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(un2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(un3).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(un4).getMaterial().equals(Material.POWDER_SNOW)){
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX(),mc.player.getY()+1,mc.player.getZ()-1);
                            } else {if (InvertUpDir.get()) mc.player.setPitch(75);}
                        }
                    }
                    case EAST -> {            //UP
                        if (mc.options.jumpKey.isPressed()){
                            BlockPos ue1 = playerPos.add(new Vec3i(0,spcoffset.get()+2,0));
                            BlockPos ue2 = playerPos.add(new Vec3i(+1,spcoffset.get()+1,0));
                            BlockPos ue3 = playerPos.add(new Vec3i(+1,spcoffset.get()+2,0));
                            BlockPos ue4 = playerPos.add(new Vec3i(+1,spcoffset.get()+3,0));
                            BlockPos pos = playerPos.add(new Vec3i(1,spcoffset.get(),0));
                            if (!mc.world.getBlockState(ue1).getMaterial().isSolid() && !mc.world.getBlockState(ue2).getMaterial().isSolid() && !mc.world.getBlockState(ue3).getMaterial().isSolid() && !mc.world.getBlockState(ue4).getMaterial().isSolid() && !mc.world.getBlockState(ue1).getMaterial().isLiquid() && !mc.world.getBlockState(ue2).getMaterial().isLiquid() && !mc.world.getBlockState(ue3).getMaterial().isLiquid() && !mc.world.getBlockState(ue4).getMaterial().isLiquid() && !mc.world.getBlockState(ue1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ue2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ue3).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ue4).getMaterial().equals(Material.POWDER_SNOW)){
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX()+1,mc.player.getY()+1+spcoffset.get(),mc.player.getZ());
                            } else {if (InvertUpDir.get()) mc.player.setPitch(75);}
                        } else {
                            BlockPos ue1 = playerPos.add(new Vec3i(0,2,0));
                            BlockPos ue2 = playerPos.add(new Vec3i(+1,1,0));
                            BlockPos ue3 = playerPos.add(new Vec3i(+1,2,0));
                            BlockPos ue4 = playerPos.add(new Vec3i(+1,3,0));
                            BlockPos pos = playerPos.add(new Vec3i(1,0,0));
                            if (!mc.world.getBlockState(ue1).getMaterial().isSolid() && !mc.world.getBlockState(ue2).getMaterial().isSolid() && !mc.world.getBlockState(ue3).getMaterial().isSolid() && !mc.world.getBlockState(ue4).getMaterial().isSolid() && !mc.world.getBlockState(ue1).getMaterial().isLiquid() && !mc.world.getBlockState(ue2).getMaterial().isLiquid() && !mc.world.getBlockState(ue3).getMaterial().isLiquid() && !mc.world.getBlockState(ue4).getMaterial().isLiquid() && !mc.world.getBlockState(ue1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ue2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ue3).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ue4).getMaterial().equals(Material.POWDER_SNOW)){
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX()+1,mc.player.getY()+1,mc.player.getZ());
                            } else {if (InvertUpDir.get()) mc.player.setPitch(75);}
                        }
                    }
                    case SOUTH -> {            //UP
                        if (mc.options.jumpKey.isPressed()){
                            BlockPos us1 = playerPos.add(new Vec3i(0,spcoffset.get()+2,0));
                            BlockPos us2 = playerPos.add(new Vec3i(0,spcoffset.get()+1,+1));
                            BlockPos us3 = playerPos.add(new Vec3i(0,spcoffset.get()+2,+1));
                            BlockPos us4 = playerPos.add(new Vec3i(0,spcoffset.get()+3,+1));
                            BlockPos pos = playerPos.add(new Vec3i(0,spcoffset.get(),1));
                            if (!mc.world.getBlockState(us1).getMaterial().isSolid() && !mc.world.getBlockState(us2).getMaterial().isSolid() && !mc.world.getBlockState(us3).getMaterial().isSolid() && !mc.world.getBlockState(us4).getMaterial().isSolid() && !mc.world.getBlockState(us1).getMaterial().isLiquid() && !mc.world.getBlockState(us2).getMaterial().isLiquid() && !mc.world.getBlockState(us3).getMaterial().isLiquid() && !mc.world.getBlockState(us4).getMaterial().isLiquid() && !mc.world.getBlockState(us1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(us2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(us3).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(us4).getMaterial().equals(Material.POWDER_SNOW)){
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX(),mc.player.getY()+1+spcoffset.get(),mc.player.getZ()+1);
                            } else {if (InvertUpDir.get()) mc.player.setPitch(75);}
                        } else {
                            BlockPos us1 = playerPos.add(new Vec3i(0,2,0));
                            BlockPos us2 = playerPos.add(new Vec3i(0,1,+1));
                            BlockPos us3 = playerPos.add(new Vec3i(0,2,+1));
                            BlockPos us4 = playerPos.add(new Vec3i(0,3,+1));
                            BlockPos pos = playerPos.add(new Vec3i(0,0,1));
                            if (!mc.world.getBlockState(us1).getMaterial().isSolid() && !mc.world.getBlockState(us2).getMaterial().isSolid() && !mc.world.getBlockState(us3).getMaterial().isSolid() && !mc.world.getBlockState(us4).getMaterial().isSolid() && !mc.world.getBlockState(us1).getMaterial().isLiquid() && !mc.world.getBlockState(us2).getMaterial().isLiquid() && !mc.world.getBlockState(us3).getMaterial().isLiquid() && !mc.world.getBlockState(us4).getMaterial().isLiquid() && !mc.world.getBlockState(us1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(us2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(us3).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(us4).getMaterial().equals(Material.POWDER_SNOW)){
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX(),mc.player.getY()+1,mc.player.getZ()+1);
                            } else {if (InvertUpDir.get()) mc.player.setPitch(75);}
                        }
                    }
                    case WEST -> {            //UP
                        if (mc.options.jumpKey.isPressed()){
                            BlockPos uw1 = playerPos.add(new Vec3i(0,spcoffset.get()+2,0));
                            BlockPos uw2 = playerPos.add(new Vec3i(-1,spcoffset.get()+1,0));
                            BlockPos uw3 = playerPos.add(new Vec3i(-1,spcoffset.get()+2,0));
                            BlockPos uw4 = playerPos.add(new Vec3i(-1,spcoffset.get()+3,0));
                            BlockPos pos = playerPos.add(new Vec3i(-1,spcoffset.get(),0));
                            if (!mc.world.getBlockState(uw1).getMaterial().isSolid() && !mc.world.getBlockState(uw2).getMaterial().isSolid() && !mc.world.getBlockState(uw3).getMaterial().isSolid() && !mc.world.getBlockState(uw4).getMaterial().isSolid() && !mc.world.getBlockState(uw1).getMaterial().isLiquid() && !mc.world.getBlockState(uw2).getMaterial().isLiquid() && !mc.world.getBlockState(uw3).getMaterial().isLiquid() && !mc.world.getBlockState(uw4).getMaterial().isLiquid() && !mc.world.getBlockState(uw1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(uw2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(uw3).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(uw4).getMaterial().equals(Material.POWDER_SNOW)){
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX()-1,mc.player.getY()+1+spcoffset.get(),mc.player.getZ());
                            } else {if (InvertUpDir.get()) mc.player.setPitch(75);}
                            }else {
                            BlockPos uw1 = playerPos.add(new Vec3i(0,2,0));
                            BlockPos uw2 = playerPos.add(new Vec3i(-1,1,0));
                            BlockPos uw3 = playerPos.add(new Vec3i(-1,2,0));
                            BlockPos uw4 = playerPos.add(new Vec3i(-1,3,0));
                            BlockPos pos = playerPos.add(new Vec3i(-1,0,0));
                            if (!mc.world.getBlockState(uw1).getMaterial().isSolid() && !mc.world.getBlockState(uw2).getMaterial().isSolid() && !mc.world.getBlockState(uw3).getMaterial().isSolid() && !mc.world.getBlockState(uw4).getMaterial().isSolid() && !mc.world.getBlockState(uw1).getMaterial().isLiquid() && !mc.world.getBlockState(uw2).getMaterial().isLiquid() && !mc.world.getBlockState(uw3).getMaterial().isLiquid() && !mc.world.getBlockState(uw4).getMaterial().isLiquid() && !mc.world.getBlockState(uw1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(uw2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(uw3).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(uw4).getMaterial().equals(Material.POWDER_SNOW)){
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX()-1,mc.player.getY()+1,mc.player.getZ());
                            } else {if (InvertUpDir.get()) mc.player.setPitch(75);}
                        }
                    }
                    default -> {}
                }
                if (mc.player.getY() >= limit.get()-1 && InvertUpDir.get()){
                    mc.player.setPitch(75);
                }
            } else if (mc.player.getY() <= downlimit.get() && !InvertDownDir.get()|| mc.player.getY() >= limit.get() && !InvertUpDir.get()|| delayLeft <= 0 && offLeft <= 0) {
                delayLeft = delay.get();
                offLeft = offTime.get();
            }
        } else if (mc.player.getPitch() >= 40){
            if (delayLeft > 0) delayLeft--;
            else if ((!lagpause.get() || timeSinceLastTick < lag.get()) && delayLeft <= 0 && offLeft > 0 && pause==true && mc.player.getY() <= limit.get() && mc.player.getY() >= downlimit.get()) {
                offLeft--;
                if (mc.player == null || mc.world == null) {toggle(); return;}
                if ((lagpause.get() && timeSinceLastTick >= lag.get()) || !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock ||  pause == false || go==false) return;
                switch (mc.player.getMovementDirection()) {
                    case NORTH -> {            //DOWN
                        if (mc.options.jumpKey.isPressed()){
                            BlockPos dn1 = playerPos.add(new Vec3i(0,-spcoffset.get()-1,-1));
                            BlockPos dn2 = playerPos.add(new Vec3i(0,-spcoffset.get(),-1));
                            BlockPos dn3 = playerPos.add(new Vec3i(0,-spcoffset.get()+1,-1));
                            BlockPos pos = playerPos.add(new Vec3i(0,-spcoffset.get()-2,-1));
                            if (!mc.world.getBlockState(dn1).getMaterial().isSolid() && !mc.world.getBlockState(dn2).getMaterial().isSolid() && !mc.world.getBlockState(dn3).getMaterial().isSolid() && !mc.world.getBlockState(dn1).getMaterial().isLiquid() && !mc.world.getBlockState(dn2).getMaterial().isLiquid() && !mc.world.getBlockState(dn3).getMaterial().isLiquid() && !mc.world.getBlockState(dn1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(dn2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(dn3).getMaterial().equals(Material.POWDER_SNOW)) {
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX(),mc.player.getY()-1-spcoffset.get(),mc.player.getZ()-1);
                            } else {if (InvertDownDir.get()) mc.player.setPitch(35);}
                            } else {
                            BlockPos dn1 = playerPos.add(new Vec3i(0,-1,-1));
                            BlockPos dn2 = playerPos.add(new Vec3i(0,0,-1));
                            BlockPos dn3 = playerPos.add(new Vec3i(0,1,-1));
                            BlockPos pos = playerPos.add(new Vec3i(0,-2,-1));
                            if (!mc.world.getBlockState(dn1).getMaterial().isSolid() && !mc.world.getBlockState(dn2).getMaterial().isSolid() && !mc.world.getBlockState(dn3).getMaterial().isSolid() && !mc.world.getBlockState(dn1).getMaterial().isLiquid() && !mc.world.getBlockState(dn2).getMaterial().isLiquid() && !mc.world.getBlockState(dn3).getMaterial().isLiquid() && !mc.world.getBlockState(dn1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(dn2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(dn3).getMaterial().equals(Material.POWDER_SNOW)) {
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX(),mc.player.getY()-1,mc.player.getZ()-1);
                            } else {if (InvertDownDir.get()) mc.player.setPitch(35);}
                        }
                    }
                    case EAST -> {            //DOWN
                        if (mc.options.jumpKey.isPressed()){
                            BlockPos de1 = playerPos.add(new Vec3i(1,-spcoffset.get()-1,0));
                            BlockPos de2 = playerPos.add(new Vec3i(1,-spcoffset.get(),0));
                            BlockPos de3 = playerPos.add(new Vec3i(1,-spcoffset.get()+1,0));
                            BlockPos pos = playerPos.add(new Vec3i(1,-spcoffset.get()-2,0));
                            if (!mc.world.getBlockState(de1).getMaterial().isSolid() && !mc.world.getBlockState(de2).getMaterial().isSolid() && !mc.world.getBlockState(de3).getMaterial().isSolid() && !mc.world.getBlockState(de1).getMaterial().isLiquid() && !mc.world.getBlockState(de2).getMaterial().isLiquid() && !mc.world.getBlockState(de3).getMaterial().isLiquid() && !mc.world.getBlockState(de1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(de2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(de3).getMaterial().equals(Material.POWDER_SNOW)) {
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX()+1,mc.player.getY()-1-spcoffset.get(),mc.player.getZ());
                            } else {if (InvertDownDir.get()) mc.player.setPitch(35);}
                            } else {
                            BlockPos de1 = playerPos.add(new Vec3i(1,-1,0));
                            BlockPos de2 = playerPos.add(new Vec3i(1,0,0));
                            BlockPos de3 = playerPos.add(new Vec3i(1,1,0));
                            BlockPos pos = playerPos.add(new Vec3i(1,-2,0));
                            if (!mc.world.getBlockState(de1).getMaterial().isSolid() && !mc.world.getBlockState(de2).getMaterial().isSolid() && !mc.world.getBlockState(de3).getMaterial().isSolid() && !mc.world.getBlockState(de1).getMaterial().isLiquid() && !mc.world.getBlockState(de2).getMaterial().isLiquid() && !mc.world.getBlockState(de3).getMaterial().isLiquid() && !mc.world.getBlockState(de1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(de2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(de3).getMaterial().equals(Material.POWDER_SNOW)) {
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX()+1,mc.player.getY()-1,mc.player.getZ());
                            } else {if (InvertDownDir.get()) mc.player.setPitch(35);}
                        }
                    }
                    case SOUTH -> {            //DOWN
                        if (mc.options.jumpKey.isPressed()){
                            BlockPos ds1 = playerPos.add(new Vec3i(0,-spcoffset.get()-1,1));
                            BlockPos ds2 = playerPos.add(new Vec3i(0,-spcoffset.get(),1));
                            BlockPos ds3 = playerPos.add(new Vec3i(0,-spcoffset.get()+1,1));
                            BlockPos pos = playerPos.add(new Vec3i(0,-spcoffset.get()-2,1));
                            if (!mc.world.getBlockState(ds1).getMaterial().isSolid() && !mc.world.getBlockState(ds2).getMaterial().isSolid() && !mc.world.getBlockState(ds3).getMaterial().isSolid() && !mc.world.getBlockState(ds1).getMaterial().isLiquid() && !mc.world.getBlockState(ds2).getMaterial().isLiquid() && !mc.world.getBlockState(ds3).getMaterial().isLiquid() && !mc.world.getBlockState(ds1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ds2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ds3).getMaterial().equals(Material.POWDER_SNOW)) {
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX(),mc.player.getY()-1- spcoffset.get(),mc.player.getZ()+1);
                            } else {if (InvertDownDir.get()) mc.player.setPitch(35);}
                            } else {
                            BlockPos ds1 = playerPos.add(new Vec3i(0,-1,1));
                            BlockPos ds2 = playerPos.add(new Vec3i(0,0,1));
                            BlockPos ds3 = playerPos.add(new Vec3i(0,1,1));
                            BlockPos pos = playerPos.add(new Vec3i(0,-2,1));
                            if (!mc.world.getBlockState(ds1).getMaterial().isSolid() && !mc.world.getBlockState(ds2).getMaterial().isSolid() && !mc.world.getBlockState(ds3).getMaterial().isSolid() && !mc.world.getBlockState(ds1).getMaterial().isLiquid() && !mc.world.getBlockState(ds2).getMaterial().isLiquid() && !mc.world.getBlockState(ds3).getMaterial().isLiquid() && !mc.world.getBlockState(ds1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ds2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(ds3).getMaterial().equals(Material.POWDER_SNOW)) {
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX(),mc.player.getY()-1,mc.player.getZ()+1);
                            } else {if (InvertDownDir.get()) mc.player.setPitch(35);}
                        }
                    }
                    case WEST -> {            //DOWN
                        if (mc.options.jumpKey.isPressed()){
                            BlockPos dw1 = playerPos.add(new Vec3i(-1,-spcoffset.get()-1,0));
                            BlockPos dw2 = playerPos.add(new Vec3i(-1,-spcoffset.get(),0));
                            BlockPos dw3 = playerPos.add(new Vec3i(-1,-spcoffset.get()+1,0));
                            BlockPos pos = playerPos.add(new Vec3i(-1,-spcoffset.get()-2,0));
                            if (!mc.world.getBlockState(dw1).getMaterial().isSolid() && !mc.world.getBlockState(dw2).getMaterial().isSolid() && !mc.world.getBlockState(dw3).getMaterial().isSolid() && !mc.world.getBlockState(dw1).getMaterial().isLiquid() && !mc.world.getBlockState(dw2).getMaterial().isLiquid() && !mc.world.getBlockState(dw3).getMaterial().isLiquid() && !mc.world.getBlockState(dw1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(dw2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(dw3).getMaterial().equals(Material.POWDER_SNOW)) {
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX()-1,mc.player.getY()-1-spcoffset.get(),mc.player.getZ());
                            } else {if (InvertDownDir.get()) mc.player.setPitch(35);}
                            }else {
                            BlockPos dw1 = playerPos.add(new Vec3i(-1,-1,0));
                            BlockPos dw2 = playerPos.add(new Vec3i(-1,0,0));
                            BlockPos dw3 = playerPos.add(new Vec3i(-1,1,0));
                            BlockPos pos = playerPos.add(new Vec3i(-1,-2,0));
                            if (!mc.world.getBlockState(dw1).getMaterial().isSolid() && !mc.world.getBlockState(dw2).getMaterial().isSolid() && !mc.world.getBlockState(dw3).getMaterial().isSolid() && !mc.world.getBlockState(dw1).getMaterial().isLiquid() && !mc.world.getBlockState(dw2).getMaterial().isLiquid() && !mc.world.getBlockState(dw3).getMaterial().isLiquid() && !mc.world.getBlockState(dw1).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(dw2).getMaterial().equals(Material.POWDER_SNOW) && !mc.world.getBlockState(dw3).getMaterial().equals(Material.POWDER_SNOW)) {
                                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()){
                                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                                mc.player.setPosition(mc.player.getX()-1,mc.player.getY()-1,mc.player.getZ());
                            } else {if (InvertDownDir.get()) mc.player.setPitch(35);}
                        }
                    }
                    default -> {}
                }
                if (mc.player.getY() <= downlimit.get()+1 && InvertDownDir.get()){
                    mc.player.setPitch(35);
                }
            } else if (mc.player.getY() <= downlimit.get() || mc.player.getY() >= limit.get() || delayLeft <= 0 && offLeft <= 0) {
                delayLeft = delay.get();
                offLeft = offTime.get();
            }
        }
        if (pause == false) return;
        PlayerUtils.centerPlayer();
    }

    private boolean shouldFlyDown(double currentY, double lastY) {
        if (currentY >= lastY) {
            return true;
        } else return lastY - currentY < 0.03130D;
    }

    private boolean isEntityOnAir(Entity entity) {
        return entity.world.getStatesInBox(entity.getBoundingBox().expand(0.0625).stretch(0.0, -0.55, 0.0)).allMatch(AbstractBlock.AbstractBlockState::isAir);
    }
    private void cascadingpileof() {
        if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock){
            mc.player.getInventory().selectedSlot = 0;
            if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock){
                mc.player.getInventory().selectedSlot = 1;
                if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock){
                    mc.player.getInventory().selectedSlot = 2;
                    if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock){
                        mc.player.getInventory().selectedSlot = 3;
                        if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock){
                            mc.player.getInventory().selectedSlot = 4;
                            if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock){
                                mc.player.getInventory().selectedSlot = 5;
                                if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock){
                                    mc.player.getInventory().selectedSlot = 6;
                                    if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock){
                                        mc.player.getInventory().selectedSlot = 7;
                                        if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FallingBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BambooBlock){
                                            mc.player.getInventory().selectedSlot = 8;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}