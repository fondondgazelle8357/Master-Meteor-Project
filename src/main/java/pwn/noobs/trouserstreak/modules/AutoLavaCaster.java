//Written By etianll with a little bit of skidded codes and some new ideas. Credits to Meteor Rejects for  a bit of skids
package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import pwn.noobs.trouserstreak.Trouser;

public class AutoLavaCaster extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBuild = settings.createGroup("Build Options");
    private final SettingGroup sgTimer = settings.createGroup("Timer Settings");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final Setting<Boolean> estlavatime = sgTimer.add(new BoolSetting.Builder()
            .name("EstimateLavaTimer")
            .description("Estimates lava time automatically based on a 45 degree staircase from your Y level down to sea level, or Y-60 if you are below sea level.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> estlavatimebotY = sgTimer.add(new BoolSetting.Builder()
            .name("ChooseBottomY")
            .description("Estimates lava time automatically based on a 45 degree staircase from your Y level down to the Y level you choose.")
            .defaultValue(false)
            .visible(() -> estlavatime.get())
            .build()
    );
    private final Setting<Integer> estbotY = sgTimer.add(new IntSetting.Builder()
            .name("BottomYforEstimation")
            .description("The bottom Y level the mountain should go to roughly")
            .defaultValue(128)
            .sliderRange(-64, 320)
            .visible(() -> estlavatime.get() && estlavatimebotY.get())
            .build()
    );
    private final Setting<Integer> lavatime = sgTimer.add(new IntSetting.Builder()
            .name("LavaTimerInSeconds")
            .description("The amount of time to let lava flow, in seconds. Use .lavacalc Command to get a suggested time. Based on 20ticks/second.")
            .defaultValue(120)
            .sliderRange(15, 900)
            .visible(() -> !estlavatime.get())
            .build()
    );
    private final Setting<Integer> watertime1 = sgTimer.add(new IntSetting.Builder()
            .name("WaterPlaceDelayInTicks")
            .description("The amount of time to delay water placement, in ticks.")
            .defaultValue(65)
            .sliderRange(60, 200)
            .build()
    );
    private final Setting<Integer> waterdelay = sgTimer.add(new IntSetting.Builder()
            .name("PickupWaterDelayInTicks")
            .description("The amount of time to delay picking up water, in ticks.")
            .defaultValue(30)
            .sliderRange(20, 100)
            .build()
    );
    private final Setting<Boolean> incY = sgBuild.add(new BoolSetting.Builder()
            .name("IncreaseYlevelPerLayer")
            .description("Increase Y+1 per flow cycle. Keep Blocks in your hotbar for it to work.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> bstyle = sgBuild.add(new BoolSetting.Builder()
            .name("SwitchBuildStyletoPlusSign")
            .description("Switches build style to increase flow")
            .defaultValue(true)
            .visible(() -> incY.get())
            .build()
    );
    private final Setting<Integer> watertime2 = sgTimer.add(new IntSetting.Builder()
            .name("WaterPlaceDelayAFTERStyleChange")
            .description("The amount of time to delay water placement, in ticks.")
            .defaultValue(115)
            .sliderRange(60, 200)
            .visible(() -> (incY.get() && bstyle.get()))
            .build()
    );
    private final Setting<Integer> lay = sgBuild.add(new IntSetting.Builder()
            .name("LayersUntilSwitchBuildStyle")
            .description("How many layers until a plus sign is placed to help flow.")
            .defaultValue(2)
            .sliderRange(0,4)
            .visible(() -> (incY.get() && bstyle.get()))
            .build()
    );
    private final Setting<Integer> layerstop = sgBuild.add(new IntSetting.Builder()
            .name("LayersUntilStop")
            .description("How many layers until the bot stops.")
            .defaultValue(4)
            .sliderRange(1,319)
            .visible(() -> (incY.get()))
            .build()
    );
    private final Setting<Integer> buildlimit = sgBuild.add(new IntSetting.Builder()
            .name("Stop at Y level")
            .description("Halts bot when you reach this Y level")
            .defaultValue(319)
            .sliderRange(-63,319)
            .visible(() -> (incY.get()))
            .build()
    );
    private final Setting<Boolean> MountainsOfLavaInc = sgBuild.add(new BoolSetting.Builder()
            .name("MountainsOfLava")
            .description("Leaves Lava as the last layer on your Mountain, for added danger when the noobs log back in.")
            .defaultValue(false)
            .visible(() -> incY.get())
            .build()
    );
    private final Setting<Boolean> sneaky = sgGeneral.add(new BoolSetting.Builder()
            .name("SneakWhenActive")
            .description("Holds sneak key to help you reach the top block of your Staircase.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
            .name("YourReach")
            .description("Your Reach, in blocks. Do not increase past max. Turn it down if not using the Reach module.")
            .defaultValue(4.6)
            .sliderRange(3, 4.6)
            .build()
    );

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

    public AutoLavaCaster() {
        super(Trouser.Main, "AutoLavaCaster", "Make Layers of Cobble on your Stairs! Aim at the top of the block you want to lava on.");
    }
    private BlockPos lava;
    private int estimatedlavatime=0;
    public boolean firstplace=true;
    public static int ticks;
    int layers;
    @Override
    public void onActivate() {
        if (estlavatime.get() && !estlavatimebotY.get()){
            if (mc.player.getBlockY()>64)
            estimatedlavatime= (((mc.player.getBlockY()-64)*60)/20);
            else if (mc.player.getBlockY()<=64)
                estimatedlavatime= (((mc.player.getBlockY()-(-60))*60)/20);
        } else if (estlavatime.get() && estlavatimebotY.get()){
                estimatedlavatime= (((mc.player.getBlockY()-estbotY.get())*60)/20);
        }
        if (Modules.get().get(Timer.class).isActive()) {
            error("Timer off.");
            Modules.get().get(Timer.class).toggle();
        }
        if (mc.player.getPitch()<=5){
            if (mc.player.getPitch()<=5){
                error("Aim Down at the Top of a Block");
            }
            if (mc.options.sneakKey.isPressed()){
                mc.options.sneakKey.setPressed(false);
            }
            ticks = 0;
            toggle();
            return;
        }
        ticks = 0;
        layers = 1;
        lava = cast();
        if (mc.player.getBlockX()==lava.getX() && mc.player.getBlockY()-1==lava.getY() && mc.player.getBlockZ()==lava.getZ()){
            if (mc.player.getBlockX()==lava.getX() && mc.player.getBlockY()-1==lava.getY() && mc.player.getBlockZ()==lava.getZ()){
                error("Don't lava yourself, that's silly.");
            }
            if (mc.options.sneakKey.isPressed()){
                mc.options.sneakKey.setPressed(false);
            }
            ticks = 0;
            toggle();
            return;
        }
        if (sneaky.get()){
            mc.options.sneakKey.setPressed(true);
        }
        BlockPos hover = new BlockPos(mc.player.getBlockX(),mc.player.getBlockY()-1,mc.player.getBlockZ());
        if (mc.world.getBlockState(lava).getBlock() == Blocks.AIR){
            ticks = 0;
            toggle();
            return;
        }
        if (!(mc.world.getBlockState(lava).getBlock() == Blocks.AIR) && !(mc.world.getBlockState(hover).getBlock() == Blocks.AIR)){
        placeLava();
        }
        firstplace=true;
    }
    @Override
    public void onDeactivate() {
        ticks = 0;
        if (mc.options.sneakKey.isPressed()){
            mc.options.sneakKey.setPressed(false);
        }
    }
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (lava == null) return;
        double x1 = lava.getX();
        double y1 = lava.getY()+1;
        double z1 = lava.getZ();
        double x2 = x1+1;
        double y2 = y1+1;
        double z2 = z1+1;


        event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        BlockPos ceiling = new BlockPos(mc.player.getBlockX(),mc.player.getBlockY()+2,mc.player.getBlockZ());
        BlockPos hover = new BlockPos(mc.player.getBlockX(),mc.player.getBlockY()-1,mc.player.getBlockZ());
        ticks++;
            if (incY.get() && (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable)) {
                cascadingpileof();
            }
        PlayerUtils.centerPlayer();
        mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(lava.getX()+0.5,lava.getY()+1.05,lava.getZ()+0.5));
        if (sneaky.get()){
            mc.options.sneakKey.setPressed(true);
        }

        if (layers<lay.get()|!bstyle.get()){
            if (mc.player.getBlockX()==lava.getX() && mc.player.getBlockY()-1==lava.getY() && mc.player.getBlockZ()==lava.getZ()){
                if (mc.player.getBlockX()==lava.getX() && mc.player.getBlockY()-1==lava.getY() && mc.player.getBlockZ()==lava.getZ()){
                    error("Don't lava yourself, that's silly.");
                }
                if (mc.options.sneakKey.isPressed()){
                    mc.options.sneakKey.setPressed(false);
                }
                ticks = 0;
                toggle();
                return;
            }
            if (ticks<=5){
                if (ticks == 2 && firstplace == true){
                    if (estlavatime.get()){
                        ChatUtils.sendMsg(Text.of("Starting layer 1. Lava will take "+estimatedlavatime+" more seconds to flow."));
                    }else
                        ChatUtils.sendMsg(Text.of("Starting layer 1. Lava will take "+lavatime.get()+" more seconds to flow."));
                }
                if (mc.world.getBlockState(hover).getMaterial().isReplaceable()){
                    if (mc.world.getBlockState(hover).getMaterial().isReplaceable()){
                        error("Not on a block, try again.");
                    }
                    if (mc.options.sneakKey.isPressed()){
                        mc.options.sneakKey.setPressed(false);
                    }
                    ticks = 0;
                    toggle();
                    return;

                }
            }
        else if (firstplace==false && ticks==40){
                if (estlavatime.get() && !estlavatimebotY.get()){
                    if (mc.player.getBlockY()>64)
                        estimatedlavatime= (((mc.player.getBlockY()-64)*60)/20);
                    else if (mc.player.getBlockY()<=64)
                        estimatedlavatime= (((mc.player.getBlockY()-(-60))*60)/20);
                } else if (estlavatime.get() && estlavatimebotY.get()){
                    estimatedlavatime= (((mc.player.getBlockY()-estbotY.get())*60)/20);
                }
        placeLava();
        if (estlavatime.get()){
            ChatUtils.sendMsg(Text.of("Starting layer "+layers+". Lava will take "+estimatedlavatime+" more seconds to flow."));
        }else
        ChatUtils.sendMsg(Text.of("Starting layer "+layers+". Lava will take "+lavatime.get()+" more seconds to flow."));
    }
            else if (MountainsOfLavaInc.get() && ticks==45 && layers>=layerstop.get()){
                if (mc.options.sneakKey.isPressed()){
                    mc.options.sneakKey.setPressed(false);
                }
                ChatUtils.sendMsg(Text.of("Done Building!"));
                ticks = 0;
                toggle();
                return;
            }
        else if (estlavatime.get() && ticks==(estimatedlavatime*20) || !estlavatime.get() && ticks==(lavatime.get()*20)){
            firstplace=false;
        pickupLiquid();
    }
        else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get() || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()){
            placeWater();
            ChatUtils.sendMsg(Text.of("Finishing layer "+layers));
        }
        else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get() || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()){
            pickupLiquid();
            if (!incY.get()){
                ticks=0;
                layers++;
            }
            if (!MountainsOfLavaInc.get() && layers>=layerstop.get()){
                if (mc.options.sneakKey.isPressed()){
                    mc.options.sneakKey.setPressed(false);
                }
                ChatUtils.sendMsg(Text.of("Done Building!"));
                ticks = 0;
                toggle();
                return;
            }
            if (incY.get()){
                if (!mc.world.getBlockState(ceiling).getMaterial().isReplaceable()){
                    if (!mc.world.getBlockState(ceiling).getMaterial().isReplaceable()){
                        error("Hit the ceiling");
                    }
                    if (mc.options.sneakKey.isPressed()){
                        mc.options.sneakKey.setPressed(false);
                    }
                    ticks = 0;
                    toggle();
                    return;
                }
                if (mc.player.getY()>=buildlimit.get()){
                    if (mc.player.getY()>=buildlimit.get()){
                        error("Hit your Y Stop Value");
                    }
                    if (mc.options.sneakKey.isPressed()){
                        mc.options.sneakKey.setPressed(false);
                    }
                    ticks = 0;
                    toggle();
                    return;
                }
                cascadingpileof();
                if (incY.get() && !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable) {
                    error("Not Enough Suitable Blocks in Hand.");
                    ticks = 0;
                    toggle();
                    return;
                }
            }
        }
        if (incY.get()){
            if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+5 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+5){
                BlockPos pos = new BlockPos(lava.getX(),lava.getY()+1,lava.getZ());
                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                    mc.player.swingHand(Hand.MAIN_HAND);}
                lava = new BlockPos(lava.getX(), lava.getY()+1, lava.getZ());
            }
        else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+10 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+10){
            mc.player.jump();
        }
        else if (estlavatime.get() && (ticks>=(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+10 && ticks<=(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+15) || !estlavatime.get() && (ticks>=(lavatime.get()*20)+watertime1.get()+waterdelay.get()+10 && ticks<=(lavatime.get()*20)+watertime1.get()+waterdelay.get()+15)) {
                BlockPos pos = mc.player.getBlockPos().add(new Vec3i(0,-1,0));
                if (mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                    mc.player.swingHand(Hand.MAIN_HAND);}
        }
        else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+16 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+16){
            ticks=0;
            layers++;
        }
    }}
        else if (layers==lay.get() && bstyle.get()){
            if (ticks<=5){
                if (ticks == 2 && firstplace == true){
                    if (estlavatime.get()){
                        ChatUtils.sendMsg(Text.of("Starting layer 1. Lava will take "+estimatedlavatime+" more seconds to flow."));
                    }else
                        ChatUtils.sendMsg(Text.of("Starting layer 1. Lava will take "+lavatime.get()+" more seconds to flow."));
                }
                if (mc.world.getBlockState(hover).getMaterial().isReplaceable()){
                    if (mc.world.getBlockState(hover).getMaterial().isReplaceable()){
                        error("Not on a block, try again.");
                    }
                    if (mc.options.sneakKey.isPressed()){
                        mc.options.sneakKey.setPressed(false);
                    }
                    ticks = 0;
                    toggle();
                    return;

                }
            }
            else if (firstplace==false && ticks==40){
                if (estlavatime.get() && !estlavatimebotY.get()){
                    if (mc.player.getBlockY()>64)
                        estimatedlavatime= (((mc.player.getBlockY()-64)*60)/20);
                    else if (mc.player.getBlockY()<=64)
                        estimatedlavatime= (((mc.player.getBlockY()-(-60))*60)/20);
                } else if (estlavatime.get() && estlavatimebotY.get()){
                    estimatedlavatime= (((mc.player.getBlockY()-estbotY.get())*60)/20);
                }
                placeLava();
                if (estlavatime.get()){
                    ChatUtils.sendMsg(Text.of("Starting layer "+layers+". Lava will take "+estimatedlavatime+" more seconds to flow."));
                }else
                    ChatUtils.sendMsg(Text.of("Starting layer "+layers+". Lava will take "+lavatime.get()+" more seconds to flow."));
            }
            else if (MountainsOfLavaInc.get() && ticks==45 && layers>=layerstop.get()+1){
                if (mc.options.sneakKey.isPressed()){
                    mc.options.sneakKey.setPressed(false);
                }
                ChatUtils.sendMsg(Text.of("Done Building!"));
                ticks = 0;
                toggle();
                return;
            }
            else if (estlavatime.get() && ticks==(estimatedlavatime*20) || !estlavatime.get() && ticks==(lavatime.get()*20)){
                firstplace=false;
                pickupLiquid();
            }
            else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get() || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()){
                placeWater();
                ChatUtils.sendMsg(Text.of("Finishing layer "+layers));
            }
            else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get() || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()){
                pickupLiquid();
                if (!incY.get()){
                    ticks=0;
                    layers++;
                }
                if (!MountainsOfLavaInc.get() && layers>=layerstop.get()){
                    if (mc.options.sneakKey.isPressed()){
                        mc.options.sneakKey.setPressed(false);
                    }
                    ChatUtils.sendMsg(Text.of("Done Building!"));
                    ticks = 0;
                    toggle();
                    return;
                }
                if (incY.get()){
                    if (!mc.world.getBlockState(ceiling).getMaterial().isReplaceable()){
                        if (!mc.world.getBlockState(ceiling).getMaterial().isReplaceable()){
                            error("Hit the ceiling");
                        }
                        if (mc.options.sneakKey.isPressed()){
                            mc.options.sneakKey.setPressed(false);
                        }
                        ticks = 0;
                        toggle();
                        return;
                    }
                    if (mc.player.getY()>=buildlimit.get()){
                        if (mc.player.getY()>=buildlimit.get()){
                            error("Hit your Y Stop Value");
                        }
                        if (mc.options.sneakKey.isPressed()){
                            mc.options.sneakKey.setPressed(false);
                        }
                        ticks = 0;
                        toggle();
                        return;
                    }
                    cascadingpileof();
                    if (bstyle.get() && incY.get() && !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable) {
                        error("Not Enough Suitable Blocks in Hand.");
                        ticks = 0;
                        toggle();
                        return;
                    }
                }
            }if (incY.get()){
                if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+4 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+4){
                    BlockPos pos2 = new BlockPos(lava.getX()+1,lava.getY()+1,lava.getZ());
                    if (mc.world.getBlockState(pos2).getMaterial().isReplaceable()) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos2), Direction.DOWN, pos2, false));
                        }
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+8 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+8) {
                    BlockPos pos3 = new BlockPos(lava.getX()-1,lava.getY()+1,lava.getZ());
                    if (mc.world.getBlockState(pos3).getMaterial().isReplaceable()){
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos3), Direction.DOWN, pos3, false));
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+12 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+12) {
                    BlockPos pos4 = new BlockPos(lava.getX(),lava.getY()+1,lava.getZ()+1);
                    if (mc.world.getBlockState(pos4).getMaterial().isReplaceable()){
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos4), Direction.DOWN, pos4, false));
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+16 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+16) {
                    BlockPos pos5 = new BlockPos(lava.getX(),lava.getY()+1,lava.getZ()-1);
                    if (mc.world.getBlockState(pos5).getMaterial().isReplaceable()){
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos5), Direction.DOWN, pos5, false));
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+20 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+20){
                    BlockPos pos1 = new BlockPos(lava.getX(),lava.getY()+1,lava.getZ());
                    if (mc.world.getBlockState(pos1).getMaterial().isReplaceable()){
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos1), Direction.DOWN, pos1, false));
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+21 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+21) {
                    lava = new BlockPos(lava.getX(), lava.getY()+1, lava.getZ());
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+25 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+25){
                    mc.player.jump();
                }
                else if (estlavatime.get() && (ticks>=(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+25 && ticks<=(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+30) || !estlavatime.get() && (ticks>=(lavatime.get()*20)+watertime1.get()+waterdelay.get()+25 && ticks<=(lavatime.get()*20)+watertime1.get()+waterdelay.get()+30)) {
                    BlockPos pos = mc.player.getBlockPos().add(new Vec3i(0,-1,0));
                    if (mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                        mc.player.swingHand(Hand.MAIN_HAND);}
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime1.get()+waterdelay.get()+31 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime1.get()+waterdelay.get()+31){
                    ticks=0;
                    layers++;
                }
            }
        }
        else if (layers>lay.get() && bstyle.get()){
            if (ticks<=5){
                if (ticks == 2 && firstplace == true){
                    if (estlavatime.get()){
                        ChatUtils.sendMsg(Text.of("Starting layer 1. Lava will take "+estimatedlavatime+" more seconds to flow."));
                    }else
                        ChatUtils.sendMsg(Text.of("Starting layer 1. Lava will take "+lavatime.get()+" more seconds to flow."));
                }
                if (mc.world.getBlockState(hover).getMaterial().isReplaceable()){
                    if (mc.world.getBlockState(hover).getMaterial().isReplaceable()){
                        error("Not on a block, try again.");
                    }
                    if (mc.options.sneakKey.isPressed()){
                        mc.options.sneakKey.setPressed(false);
                    }
                    ticks = 0;
                    toggle();
                    return;

                }
            }
            else if (firstplace==false && ticks==40){
                if (estlavatime.get() && !estlavatimebotY.get()){
                    if (mc.player.getBlockY()>64)
                        estimatedlavatime= (((mc.player.getBlockY()-64)*60)/20);
                    else if (mc.player.getBlockY()<=64)
                        estimatedlavatime= (((mc.player.getBlockY()-(-60))*60)/20);
                } else if (estlavatime.get() && estlavatimebotY.get()){
                    estimatedlavatime= (((mc.player.getBlockY()-estbotY.get())*60)/20);
                }
                placeLava();
                if (estlavatime.get()){
                    ChatUtils.sendMsg(Text.of("Starting layer "+layers+". Lava will take "+estimatedlavatime+" more seconds to flow."));
                }else
                    ChatUtils.sendMsg(Text.of("Starting layer "+layers+". Lava will take "+lavatime.get()+" more seconds to flow."));
            }
            else if (MountainsOfLavaInc.get() && ticks==45 && layers>=layerstop.get()+1){
                if (mc.options.sneakKey.isPressed()){
                    mc.options.sneakKey.setPressed(false);
                }
                ChatUtils.sendMsg(Text.of("Done Building!"));
                ticks = 0;
                toggle();
                return;
            }
            else if (estlavatime.get() && ticks==(estimatedlavatime*20) || !estlavatime.get() && ticks==(lavatime.get()*20)){
                firstplace=false;
                pickupLiquid();
            }
            else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get() || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()){
                placeWater();
                ChatUtils.sendMsg(Text.of("Finishing layer "+layers));
            }
            else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get()+waterdelay.get() || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()+waterdelay.get()){
                pickupLiquid();
                if (!incY.get()){
                    ticks=0;
                    layers++;
                }
                if (!MountainsOfLavaInc.get() && layers>=layerstop.get()){
                    if (mc.options.sneakKey.isPressed()){
                        mc.options.sneakKey.setPressed(false);
                    }
                    ChatUtils.sendMsg(Text.of("Done Building!"));
                    ticks = 0;
                    toggle();
                    return;
                }
                if (incY.get()){
                    if (!mc.world.getBlockState(ceiling).getMaterial().isReplaceable()){
                        if (!mc.world.getBlockState(ceiling).getMaterial().isReplaceable()){
                            error("Hit the ceiling");
                        }
                        if (mc.options.sneakKey.isPressed()){
                            mc.options.sneakKey.setPressed(false);
                        }
                        ticks = 0;
                        toggle();
                        return;
                    }
                    if (mc.player.getY()>=buildlimit.get()){
                        if (mc.player.getY()>=buildlimit.get()){
                            error("Hit your Y Stop Value");
                        }
                        if (mc.options.sneakKey.isPressed()){
                            mc.options.sneakKey.setPressed(false);
                        }
                        ticks = 0;
                        toggle();
                        return;
                    }
                    cascadingpileof();
                    if (bstyle.get() && incY.get() && !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable) {
                        error("Not Enough Suitable Blocks in Hand.");
                        ticks = 0;
                        toggle();
                        return;
                    }
                }
            }if (incY.get()){
                if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+4 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()+waterdelay.get()+4){
                    BlockPos pos2 = new BlockPos(lava.getX()+1,lava.getY()+1,lava.getZ());
                    if (mc.world.getBlockState(pos2).getMaterial().isReplaceable()) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos2), Direction.DOWN, pos2, false));
                        mc.player.swingHand(Hand.MAIN_HAND);}
                }
                if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+8 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()+waterdelay.get()+8){
                    BlockPos pos3 = new BlockPos(lava.getX()-1,lava.getY()+1,lava.getZ());
                    if (mc.world.getBlockState(pos3).getMaterial().isReplaceable()) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos3), Direction.DOWN, pos3, false));
                        mc.player.swingHand(Hand.MAIN_HAND);}
                }
                if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+12 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()+waterdelay.get()+12){
                    BlockPos pos4 = new BlockPos(lava.getX(),lava.getY()+1,lava.getZ()+1);
                    if (mc.world.getBlockState(pos4).getMaterial().isReplaceable()) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos4), Direction.DOWN, pos4, false));
                        mc.player.swingHand(Hand.MAIN_HAND);}
                }
                if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+16 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()+waterdelay.get()+16){
                    BlockPos pos5 = new BlockPos(lava.getX(),lava.getY()+1,lava.getZ()-1);
                    if (mc.world.getBlockState(pos5).getMaterial().isReplaceable()) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos5), Direction.DOWN, pos5, false));
                        mc.player.swingHand(Hand.MAIN_HAND);}
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+20 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()+waterdelay.get()+20){
                    BlockPos pos1 = new BlockPos(lava.getX(),lava.getY()+1,lava.getZ());
                    if (mc.world.getBlockState(pos1).getMaterial().isReplaceable()){
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos1), Direction.DOWN, pos1, false));
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+21 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()+waterdelay.get()+21) {
                    lava = new BlockPos(lava.getX(), lava.getY()+1, lava.getZ());
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+25 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()+waterdelay.get()+25){
                    mc.player.jump();
                }
                else if (estlavatime.get() && (ticks>=(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+25 && ticks<=(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+30) || !estlavatime.get() && (ticks>=(lavatime.get()*20)+watertime2.get()+waterdelay.get()+25 && ticks<=(lavatime.get()*20)+watertime2.get()+waterdelay.get()+30)) {
                    BlockPos pos = mc.player.getBlockPos().add(new Vec3i(0,-1,0));
                    if (mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
                        mc.player.swingHand(Hand.MAIN_HAND);}
                }
                else if (estlavatime.get() && ticks==(estimatedlavatime*20)+watertime2.get()+waterdelay.get()+31 || !estlavatime.get() && ticks==(lavatime.get()*20)+watertime2.get()+waterdelay.get()+31){
                    ticks=0;
                    layers++;
                }
            }
        }
    }
    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen) {
            ticks = 0;
            toggle();
        }
        if (event.screen instanceof DeathScreen) {
            ticks = 0;
            toggle();
        }
    }
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        ticks = 0;
        toggle();
    }
    private void placeLava() {
        FindItemResult findItemResult = InvUtils.findInHotbar(Items.LAVA_BUCKET);
        if (!findItemResult.found()) {
            error("No lava bucket found.");
            if (mc.options.sneakKey.isPressed()){
                mc.options.sneakKey.setPressed(false);
            }
            ticks = 0;
            toggle();
            return;
        }
        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = findItemResult.slot();
        mc.interactionManager.interactItem(mc.player,Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = prevSlot;
    }

    private void placeWater() {
        FindItemResult findItemResult = InvUtils.findInHotbar(Items.WATER_BUCKET);
        if (!findItemResult.found()) {
            error("No water bucket found.");
            if (mc.options.sneakKey.isPressed()){
                mc.options.sneakKey.setPressed(false);
            }
            ticks = 0;
            toggle();
            return;
        }
        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = findItemResult.slot();
        mc.interactionManager.interactItem(mc.player,Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = prevSlot;
    }

    private void pickupLiquid() {
        FindItemResult findItemResult = InvUtils.findInHotbar(Items.BUCKET);
        if (!findItemResult.found()) {
            error("No bucket found.");
            if (mc.options.sneakKey.isPressed()){
                mc.options.sneakKey.setPressed(false);
            }
            ticks = 0;
            toggle();
            return;
        }
        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = findItemResult.slot();
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = prevSlot;
    }

    private BlockPos cast() {
        HitResult blockHit = mc.cameraEntity.raycast(reach.get(), 0, false);
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            error("Block too far, Try Again");
        }
        return ((BlockHitResult) blockHit).getBlockPos();
    }
    private void cascadingpileof() {
        if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable){
            mc.player.getInventory().selectedSlot = 0;
            if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable){
                mc.player.getInventory().selectedSlot = 1;
                if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable){
                    mc.player.getInventory().selectedSlot = 2;
                    if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable){
                        mc.player.getInventory().selectedSlot = 3;
                        if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable){
                            mc.player.getInventory().selectedSlot = 4;
                            if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable){
                                mc.player.getInventory().selectedSlot = 5;
                                if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable){
                                    mc.player.getInventory().selectedSlot = 6;
                                    if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable){
                                        mc.player.getInventory().selectedSlot = 7;
                                        if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem) || mc.player.getInventory().getMainHandStack().getItem() instanceof BedItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PowderSnowBucketItem || mc.player.getInventory().getMainHandStack().getItem() instanceof ScaffoldingItem || mc.player.getInventory().getMainHandStack().getItem() instanceof TallBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof VerticallyAttachableBlockItem || mc.player.getInventory().getMainHandStack().getItem() instanceof PlaceableOnWaterItem || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TorchBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRedstoneGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof RedstoneWireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof FenceGateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractRailBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AbstractSignBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BellBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CarpetBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ConduitBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CoralParentBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireHookBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PointedDripstoneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TripwireBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SnowBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof PressurePlateBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof WallMountedBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ShulkerBoxBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof AmethystClusterBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof BuddingAmethystBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusFlowerBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof ChorusPlantBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof LanternBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CandleBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof TntBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CakeBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CobwebBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SugarCaneBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof SporeBlossomBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof KelpBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof GlowLichenBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof CactusBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof  BambooBlock || ((BlockItem) mc.player.getInventory().getMainHandStack().getItem()).getBlock() instanceof Waterloggable){
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
