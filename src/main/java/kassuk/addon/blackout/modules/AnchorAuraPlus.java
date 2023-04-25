package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.enums.SwingState;
import kassuk.addon.blackout.enums.SwingType;
import kassuk.addon.blackout.managers.Managers;
import kassuk.addon.blackout.utils.*;
import kassuk.addon.blackout.utils.meteor.BODamageUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BedItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Made by OLEPOSSU / Raksamies
*/

public class AnchorAuraPlus extends BlackOutModule {
    public AnchorAuraPlus() {super(BlackOut.BLACKOUT, "Anchor Aura+", "Automatically places and breaks respawn anchors to cause damage to your opponents but better");}
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlacing = settings.createGroup("Placing");
    private final SettingGroup sgDamage = settings.createGroup("Damage");
    private final SettingGroup sgRender = settings.createGroup("Render");

    //   General Page
    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
        .name("Pause Eat")
        .description("Pauses when you are eating")
        .defaultValue(true)
        .build()
    );
    private final Setting<LogicMode> logicMode = sgGeneral.add(new EnumSetting.Builder<LogicMode>()
        .name("Logic Mode")
        .description("Logic for bullying kids.")
        .defaultValue(LogicMode.BreakPlace)
        .build()
    );
    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("Switch Mode")
        .description(".")
        .defaultValue(SwitchMode.Silent)
        .build()
    );

    //   Placing Page
    private final Setting<Double> speed = sgPlacing.add(new DoubleSetting.Builder()
        .name("Speed")
        .description(".")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    //   Damage Page
    private final Setting<Double> minDmg = sgDamage.add(new DoubleSetting.Builder()
        .name("Min Damage")
        .description(".")
        .defaultValue(8)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> maxDmg = sgDamage.add(new DoubleSetting.Builder()
        .name("Max Damage")
        .description(".")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> minRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("Min Damage Ratio")
        .description(".")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    //   Render Page
    public final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("Shape Mode")
        .description(".")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("Line Color")
        .description("Line color of rendered stuff")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );
    public final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("Side Color")
        .description("Side color of rendered stuff")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    public enum LogicMode {
        PlaceBreak,
        BreakPlace
    }
    public enum RotationMode {
        Packet,
        Manager
    }
    public enum SwitchMode {
        Silent,
        Normal,
        SilentBypass,
        Disabled
    }
    public enum AnchorState {
        Air,
        Anchor,
        Loaded
    }

    BlockPos[] blocks = new BlockPos[]{};
    int lastIndex = 0;
    int length = 0;
    long tickTime = -1;
    double bestDmg = -1;
    long lastTime = 0;

    BlockPos placePos = null;
    PlaceData placeData = null;
    BlockPos calcPos = null;
    PlaceData calcData = null;
    BlockPos renderPos = null;
    List<PlayerEntity> targets = new ArrayList<>();
    Map<BlockPos, Anchor> anchors = new HashMap<>();

    double timer = 0;

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTickPre(TickEvent.Post event) {
        calculate(length - 1);
        renderPos = calcPos;
        placePos = calcPos;
        placeData = calcData;

        blocks = getBlocks(mc.player.getEyePos(), Math.max(SettingUtils.getPlaceRange(), SettingUtils.getPlaceWallsRange()));

        // Reset stuff
        tickTime = System.currentTimeMillis();
        length = blocks.length;
        lastIndex = 0;
        bestDmg = -1;
        calcPos = null;
        calcData = null;

        updateTargets();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        double delta = (System.currentTimeMillis() - lastTime) / 1000f;
        timer += delta;
        lastTime = System.currentTimeMillis();
        if (tickTime < 0 || mc.player == null || mc.world == null) {return;}

        if (pauseCheck()) {
            update();
        }

        List<BlockPos> toRemove = new ArrayList<>();
        anchors.forEach((pos, anchor) -> {
            if (System.currentTimeMillis() - anchor.time > 500) {
                toRemove.add(pos);
            }
        });
        toRemove.forEach(anchors::remove);

        int index = Math.min((int) Math.ceil((System.currentTimeMillis() - tickTime) / 50f * length), length - 1);
        calculate(index);

        if (renderPos != null && pauseCheck()) {
            event.renderer.box(renderPos, color.get(), lineColor.get(), shapeMode.get(), 0);
        }

    }

    boolean pauseCheck() {
        return !pauseEat.get() || !mc.player.isUsingItem();
    }

    void calculate(int index) {
        BlockPos pos;

        double dmg;
        double self;
        for (int i = lastIndex; i < index; i++) {
            pos = blocks[i];

            dmg = getDmg(pos);
            self = BODamageUtils.anchorDamage(mc.player, new Vec3i((int) (pos.getX() + 0.5), (int) (pos.getY() + 0.5), (int) (pos.getZ() + 0.5)));

            if (!dmgCheck(dmg, self)) {continue;}

            PlaceData data = SettingUtils.getPlaceData(pos);

            if (!data.valid()) {continue;}

            if (EntityUtils.intersectsWithEntity(new Box(pos), entity -> !(entity instanceof ItemEntity))) {continue;}

            calcData = data;
            calcPos = pos;
            bestDmg = dmg;
        }
        lastIndex = index;
    }

    void updateTargets() {
        List<PlayerEntity> players = new ArrayList<>();
        double closestDist = 1000;
        PlayerEntity closest;
        double dist;
        for (int i = 3; i > 0; i--) {

            closest = null;
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (players.contains(player) || Friends.get().isFriend(player) || player == mc.player) {continue;}

                dist = player.distanceTo(mc.player);

                if (dist > 15) {continue;}

                if (closest == null || dist < closestDist) {
                    closestDist = dist;
                    closest = player;
                }
            }
            if (closest != null) {
                players.add(closest);
            }
        }
        targets = players;
    }

    BlockPos[] getBlocks(Vec3d middle, double radius) {
        ArrayList<BlockPos> result = new ArrayList<>();
        int i = (int) Math.ceil(radius);
        BlockPos pos;

        for (int x = -i; x <= i; x++) {
            for (int y = -i; y <= i; y++) {
                for (int z = -i; z <= i; z++) {
                    pos = new BlockPos((int) (Math.floor(middle.x) + x), (int) (Math.floor(middle.y) + y), (int) (Math.floor(middle.z) + z));

                    if (!OLEPOSSUtils.replaceable(pos) && !(mc.world.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR)) {continue;}

                    if (!inRangeToTargets(pos)) {continue;}

                    if (!SettingUtils.inPlaceRange(pos)) {continue;}

                    result.add(pos);
                }
            }
        }
        return result.toArray(new BlockPos[0]);
    }

    boolean inRangeToTargets(BlockPos pos) {
        for (PlayerEntity target : targets) {
            if (OLEPOSSUtils.distance(target.getPos().add(0, 1, 0), new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) < 3.5) {
                return true;
            }
        }
        return false;
    }

    void update() {

        if (placePos == null || placeData == null || !placeData.valid()) {return;}

        Anchor anchor = getAnchor(placePos);

        if (logicMode.get() == LogicMode.PlaceBreak) {
            switch (anchor.state) {
                case Anchor -> {
                    if (chargeUpdate(placePos)) {
                        Anchor a = new Anchor(AnchorState.Loaded, anchor.charges + 1, System.currentTimeMillis());

                        anchors.remove(placePos);
                        anchors.put(placePos, a);
                    }
                }
                case Loaded -> {
                    if (explodeUpdate(placePos)) {
                        anchors.remove(placePos);
                        anchors.put(placePos, new Anchor(AnchorState.Air, 0, System.currentTimeMillis()));
                    }
                }
                case Air -> {
                    if (timer <= 1 / speed.get()) {return;}

                    if (placeUpdate()) {
                        anchors.remove(placePos);
                        anchors.put(placePos, new Anchor(AnchorState.Anchor, 0, System.currentTimeMillis()));
                        timer = 0;
                    }
                }
            }
        } else {
            switch (anchor.state) {
                case Air -> {
                    if (placeUpdate()) {
                        anchors.remove(placePos);
                        anchors.put(placePos, new Anchor(AnchorState.Anchor, 0, System.currentTimeMillis()));
                    }
                }
                case Anchor -> {
                    if (chargeUpdate(placePos)) {
                        Anchor a = new Anchor(AnchorState.Loaded, anchor.charges + 1, System.currentTimeMillis());

                        anchors.remove(placePos);
                        anchors.put(placePos, a);
                    }
                }
                case Loaded -> {
                    if (timer <= 1 / speed.get()) {return;}

                    if (explodeUpdate(placePos)) {
                        anchors.remove(placePos);
                        anchors.put(placePos, new Anchor(AnchorState.Air, 0, System.currentTimeMillis()));
                        timer = 0;
                    }
                }
            }
        }
    }

    void place(Hand hand) {
        SettingUtils.swing(SwingState.Pre, SwingType.Placing, hand);

        sendPacket(new PlayerInteractBlockC2SPacket(hand, new BlockHitResult(OLEPOSSUtils.getMiddle(placeData.pos()), placeData.dir(), placeData.pos(), false), 0));

        SettingUtils.swing(SwingState.Post, SwingType.Placing, hand);
    }

    Anchor getAnchor(BlockPos pos) {
        if (anchors.containsKey(pos)) {
            return anchors.get(pos);
        }
        BlockState state = mc.world.getBlockState(pos);
        return new Anchor(state.getBlock() == Blocks.RESPAWN_ANCHOR ? state.get(Properties.CHARGES) < 1 ? AnchorState.Anchor : AnchorState.Loaded : AnchorState.Air, state.getBlock() == Blocks.RESPAWN_ANCHOR ? state.get(Properties.CHARGES) : 0, System.currentTimeMillis());
    }

    boolean placeUpdate() {
        Hand hand = Managers.HOLDING.isHolding(Items.RESPAWN_ANCHOR) ? Hand.MAIN_HAND : mc.player.getOffHandStack().getItem() == Items.RESPAWN_ANCHOR ? Hand.OFF_HAND : null;

        boolean switched = hand != null;

        if (!switched) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    FindItemResult result = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
                    switched = result.found();
                }
                case SilentBypass -> {
                    FindItemResult result = InvUtils.find(Items.RESPAWN_ANCHOR);
                    switched = result.found();
                }
            }
        }

        if (!switched) {return false;}

        if (SettingUtils.shouldRotate(RotationType.Placing) && !Managers.ROTATION.start(placeData.pos(), priority, RotationType.Placing)) {return false;}


        if (hand == null) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    FindItemResult result = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
                    InvUtils.swap(result.slot(), true);
                }
                case SilentBypass -> {
                    FindItemResult result = InvUtils.find(Items.RESPAWN_ANCHOR);
                    switched = BOInvUtils.invSwitch(result.slot());
                }
            }
        }

        if (!switched) {return false;}

        place(hand == null ? Hand.MAIN_HAND : hand);

        if (SettingUtils.shouldRotate(RotationType.Placing)) {
            Managers.ROTATION.end(placeData.pos());
        }

        if (hand == null) {
            switch (switchMode.get()) {
                case Silent -> InvUtils.swapBack();
                case SilentBypass -> BOInvUtils.swapBack();
            }
        }
        return true;
    }

    boolean chargeUpdate(BlockPos pos) {
        Hand hand = Managers.HOLDING.isHolding(Items.GLOWSTONE) ? Hand.MAIN_HAND : mc.player.getOffHandStack().getItem() == Items.GLOWSTONE ? Hand.OFF_HAND : null;
        Direction dir = SettingUtils.getPlaceOnDirection(pos);

        if (dir == null) {return false;}

        boolean switched = hand != null;

        if (!switched) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    FindItemResult result = InvUtils.findInHotbar(Items.GLOWSTONE);
                    switched = result.found();
                }
                case SilentBypass -> {
                    FindItemResult result = InvUtils.find(Items.GLOWSTONE);
                    switched = result.found();
                }
            }
        }

        if (!switched) {return false;}

        if (SettingUtils.shouldRotate(RotationType.Interact) && !Managers.ROTATION.start(pos, priority, RotationType.Interact)) {return false;}

        if (hand == null) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    FindItemResult result = InvUtils.findInHotbar(Items.GLOWSTONE);
                    InvUtils.swap(result.slot(), true);
                }
                case SilentBypass -> {
                    FindItemResult result = InvUtils.find(Items.GLOWSTONE);
                    switched = BOInvUtils.invSwitch(result.slot());
                }
            }
        }

        if (!switched) {return false;}

        interact(pos, dir, hand == null ? Hand.MAIN_HAND : hand);

        if (SettingUtils.shouldRotate(RotationType.Interact)) {
            Managers.ROTATION.end(placeData.pos());
        }

        if (hand == null) {
            switch (switchMode.get()) {
                case Silent -> InvUtils.swapBack();
                case SilentBypass -> BOInvUtils.swapBack();
            }
        }
        return true;
    }
    boolean explodeUpdate(BlockPos pos) {
        Hand hand = !Managers.HOLDING.isHolding(Items.GLOWSTONE) ? Hand.MAIN_HAND : mc.player.getOffHandStack().getItem() != Items.GLOWSTONE ? Hand.OFF_HAND : null;
        Direction dir = SettingUtils.getPlaceOnDirection(pos);

        if (dir == null) {return false;}

        boolean switched = hand != null;

        if (!switched) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    FindItemResult result = InvUtils.findInHotbar(stack -> stack.getItem() != Items.GLOWSTONE);
                    switched = result.found();
                }
                case SilentBypass -> {
                    FindItemResult result = InvUtils.find(stack -> stack.getItem() != Items.GLOWSTONE);
                    switched = result.found();
                }
            }
        }

        if (!switched) {return false;}

        if (SettingUtils.shouldRotate(RotationType.Interact) && !Managers.ROTATION.start(pos, priority, RotationType.Interact)) {return false;}

        if (hand == null) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    FindItemResult result = InvUtils.findInHotbar(item -> item.getItem() != Items.GLOWSTONE);
                    InvUtils.swap(result.slot(), true);
                }
                case SilentBypass -> {
                    FindItemResult result = InvUtils.find(item -> item.getItem() != Items.GLOWSTONE);
                    switched = BOInvUtils.invSwitch(result.slot());
                }
            }
        }

        if (!switched) {return false;}

        interact(pos, dir, hand == null ? Hand.MAIN_HAND : hand);

        if (SettingUtils.shouldRotate(RotationType.Interact)) {
            Managers.ROTATION.end(placeData.pos());
        }

        if (hand == null) {
            switch (switchMode.get()) {
                case Silent -> InvUtils.swapBack();
                case SilentBypass -> BOInvUtils.swapBack();
            }
        }
        return true;
    }

    void interact(BlockPos pos, Direction dir, Hand hand) {
        SettingUtils.swing(SwingState.Pre, SwingType.Placing, hand);

        sendPacket(new PlayerInteractBlockC2SPacket(hand, new BlockHitResult(OLEPOSSUtils.getMiddle(pos), dir, pos, false), 0));

        SettingUtils.swing(SwingState.Post, SwingType.Interact, hand);
    }

    boolean dmgCheck(double dmg, double self) {
        if (dmg < bestDmg) {return false;}

        if (dmg < minDmg.get()) {return false;}
        if (self > maxDmg.get()) {return false;}
        if (dmg / self < minRatio.get()) {return false;}

        return true;
    }

    double getDmg(BlockPos pos) {
        double highest = -1;
        for (PlayerEntity target : targets) {
            highest = Math.max(highest, BODamageUtils.anchorDamage(target, new Vec3i((int) (pos.getX() + 0.5), (int) (pos.getY() + 0.5), (int) (pos.getZ() + 0.5))));
        }
        return highest;
    }

    Box bedBox(BlockPos pos) {
        return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 0.5, pos.getZ() + 1);
    }

    record Anchor(AnchorState state, int charges, long time) {}
}
