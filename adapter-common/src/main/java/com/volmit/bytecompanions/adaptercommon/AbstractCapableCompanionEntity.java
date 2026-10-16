package com.volmit.bytecompanions.adaptercommon;

import com.mojang.datafixers.util.Pair;
import net.kyori.adventure.text.Component;
import net.minecraft.core.Rotations;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import com.volmit.bytecompanions.adaptercommon.util.NmsUtil;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionOverlay;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.Animations;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.api.companion.data.OverlayData;
import com.volmit.bytecompanions.core.MiniatureAnimator;
import com.volmit.bytecompanions.core.companion.CompanionBehaviorController;
import com.volmit.bytecompanions.api.companion.behavior.CompanionBehavior;
import com.volmit.bytecompanions.core.companion.*;
import com.volmit.bytecompanions.spi.companions.CompanionViewerListener;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;
import com.volmit.bytecompanions.spi.companions.ViewerSnapshot;
import com.volmit.bytecompanions.spi.companions.NmsCapableCompanionEntity;

import java.util.*;
import java.util.function.Function;

public abstract class AbstractCapableCompanionEntity implements NmsCapableCompanionEntity, RotationsAccessor {

    private record CompatibilityAnchor(
            double x, double y, double z,
            float yaw, float pitch, float bodyYaw,
            double eyeHeight, double height,
            @NotNull World world
    ) implements CompanionAnchor {
    }

    public static void validateCompatibility(
            @NotNull World world,
            @NotNull Function<CompanionAnchor, ? extends AbstractCapableCompanionEntity> constructor
    ) {
        AbstractCapableCompanionEntity entity = constructor.apply(
                new CompatibilityAnchor(0, 0, 0, 0, 0, 0, 0, 0, world));
        entity.validateNativeOperations();
    }

    private void validateNativeOperations() {
        Rotations pose = new Rotations(10, 20, 30);
        if (this.rotX(pose) != 10 || this.rotY(pose) != 20 || this.rotZ(pose) != 30) {
            throw new IllegalStateException("Native rotation accessors returned unexpected values");
        }

        this.baseArmorStand.setHeadPose(pose);
        this.baseArmorStand.setBodyPose(pose);
        this.baseArmorStand.setLeftArmPose(pose);
        this.baseArmorStand.setRightArmPose(pose);
        this.baseArmorStand.setLeftLegPose(pose);
        this.baseArmorStand.setRightLegPose(pose);
        this.baseArmorStand.setYRot(20);
        this.baseArmorStand.setXRot(10);
        this.baseArmorStand.yBodyRot = 20;
        this.baseArmorStand.xo = 0.25;
        this.baseArmorStand.yo = 0.5;
        this.baseArmorStand.zo = 0.75;

        org.bukkit.inventory.ItemStack equipment = new org.bukkit.inventory.ItemStack(Material.STONE);
        this.baseEquipment.put(NmsUtil.equipmentSlotToNms(org.bukkit.inventory.EquipmentSlot.HEAD),
                CraftItemStack.asNMSCopy(equipment));
        this.baseEquipment.put(EquipmentSlot.MAINHAND, CraftItemStack.asNMSCopy(equipment));
        CompanionOverlay overlay = this.addOverlay("compatibility", new OverlayData(false,
                Map.of(org.bukkit.inventory.EquipmentSlot.HEAD, () -> equipment)));
        overlay.equipment(org.bukkit.inventory.EquipmentSlot.HAND, equipment);
        overlay.equipment();
        this.equipment();
        this.overlayEquipment("compatibility");

        new ClientboundBundlePacket(List.of(
                this.createAddEntityPacket(this.baseArmorStand),
                this.createDataPacket(this.baseArmorStand),
                this.createAttributesPacket(this.baseArmorStand),
                this.buildEquipmentPacket(this.entityId, this.baseEquipment),
                this.createMovementPacket(this.entityId, Short.MIN_VALUE, (short) 0, Short.MAX_VALUE,
                        (byte) -128, (byte) 127),
                new ClientboundRemoveEntitiesPacket(this.entityId)));

        for (OverlayEntity nativeOverlay : this.overlays.values()) {
            new ClientboundBundlePacket(List.of(
                    this.createAddEntityPacket(nativeOverlay.armorStand()),
                    this.createDataPacket(nativeOverlay.armorStand()),
                    this.createAttributesPacket(nativeOverlay.armorStand()),
                    new ClientboundRemoveEntitiesPacket(nativeOverlay.armorStand().getId())));
        }

        this.dirtyMovement = true;
        this.dirtyRotation = true;
        this.dirtyMetadata = true;
        this.dirtyEquipment = true;
        this.flushDirty();
        this.validateMetadataReset(pose);
    }

    private void validateMetadataReset(Rotations previousPose) {
        List<SynchedEntityData.DataValue<?>> previousValues = this.metadataValues(this.baseArmorStand);
        Rotations resetPose = new Rotations(0, 0, 0);
        this.baseArmorStand.setHeadPose(resetPose);
        List<SynchedEntityData.DataValue<?>> resetValues = this.metadataValues(this.baseArmorStand);
        for (SynchedEntityData.DataValue<?> previous : previousValues) {
            if (!previousPose.equals(previous.value())) {
                continue;
            }
            for (SynchedEntityData.DataValue<?> reset : resetValues) {
                if (previous.id() == reset.id() && resetPose.equals(reset.value())) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Native metadata omitted a pose reset to its default value");
    }

    private record CycleTarget(
            @Nullable String overlayId,
            EquipmentSlot slot
    ) {

    }

    // ------------------ CONSTANTS ------------------
    private static final double ROTATION_CONVERSION = 256.0 / 360.0;

    // ------------------ STATE ------------------
    private final CompanionAnchor anchor;
    private final CompanionViewerEnvironment viewerEnvironment;
    private final UUID worldId;
    private final Map<Player, ViewerState> viewerStates = new HashMap<>();
    private final DefaultViewerTracker viewerTracker;
    private final MiniatureAnimator animator;
    private final CompanionBehaviorController behavior;
    private final CompanionModel model;

    private final int viewDistance;
    private final int viewerTrackerDelay;
    private Player restrictedViewer;

    private static final class ViewerState {
        private final ViewerSnapshot snapshot;
        private volatile boolean active = true;

        private ViewerState(ViewerSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }

    // Base ArmorStand
    private ArmorStand baseArmorStand;
    private final int entityId;
    private boolean spawned;

    // Overlay armor stands (order preserved with LinkedHashMap)
    private final Map<String, OverlayEntity> overlays = new LinkedHashMap<>();

    // Equipment cache
    private final Map<EquipmentSlot, ItemStack> baseEquipment = new EnumMap<>(EquipmentSlot.class);
    private final Map<CycleTarget, ArmorCycleState> armorCycles = new HashMap<>();

    // Viewer update tick counter
    private byte viewerTrackerTick;

    private final List<CompanionViewerListener> viewerListeners = new ArrayList<>();

    private long lastTime;
    private final CompanionAnchorTracker previousAnchor;

    private double previousEntityX;
    private double previousEntityY;
    private double previousEntityZ;

    // ----

    private volatile int formatIndex;
    private volatile int formatCount = 1;

    // -------------- DIRTY FLAGS --------------
    protected boolean dirtyMovement;
    protected boolean dirtyRotation;
    protected boolean dirtyMetadata;
    protected boolean dirtyEquipment;

    // ------------------ CONSTRUCTOR ------------------
    public AbstractCapableCompanionEntity(
            final @NotNull CompanionViewerEnvironment viewerEnvironment,
            final @NotNull CompanionAnchor anchor,
            final @NotNull CompanionModel model,
            final @NotNull AppearanceData appearanceData,
            final @NotNull Component customName,
            final int viewDistance,
            final int viewerTrackerDelay,
            final int entityId
    ) {
        this.anchor = anchor;
        this.viewerEnvironment = viewerEnvironment;
        this.worldId = anchor.world().getUID();
        this.model = model;
        this.viewDistance = viewDistance;
        this.viewerTracker = new DefaultViewerTracker();

        this.viewerTrackerDelay = viewerTrackerDelay;
        this.viewerTrackerTick = 0;

        this.lastTime = System.nanoTime();
        this.previousAnchor = new CompanionAnchorTracker(anchor);

        this.animator = new MiniatureAnimator();

        final ServerLevel serverLevel = ((CraftWorld) anchor.world()).getHandle();
        this.baseArmorStand = new ArmorStand(serverLevel, 0, 0, 0);

        if (entityId > -1) {
            this.baseArmorStand.setId(entityId);
        }

        this.setAppearance(appearanceData, customName);
        this.entityId = this.baseArmorStand.getId();
        this.spawned = false;
        this.behavior = new CompanionBehaviorController(model,
                () -> new CompanionBehaviorController.Position(this.worldId, this.currentX(), this.currentY(),
                        this.currentZ(), this.baseArmorStand.getYRot()), System::nanoTime);
    }

    public void setAppearance(
            final @NotNull AppearanceData appearanceData,
            final @NotNull Component customName
    ) {
        this.baseArmorStand.setMarker(appearanceData.marker());
        this.baseArmorStand.setSmall(appearanceData.small());
        this.baseArmorStand.setInvisible(!appearanceData.visible());
        this.baseArmorStand.setNoBasePlate(!appearanceData.showBaseplate());
        this.baseArmorStand.setShowArms(appearanceData.showArms());

        if (appearanceData.scale() > 0) {
            final AttributeInstance attributeInstance = this.baseArmorStand.getAttribute(Attributes.SCALE);
            if (attributeInstance != null) {
                attributeInstance.setBaseValue(appearanceData.scale());
            }
        }

        this.baseArmorStand.setCustomName(this.toNativeComponent(customName));
        this.baseArmorStand.setCustomNameVisible(appearanceData.customNameVisible());
        this.baseArmorStand.setHeadPose(new Rotations(0, 0, 0));
    }

    @Override
    public void formatIndex(final int index, final int formatCount) {
        this.formatIndex = index;
        this.formatCount = formatCount;
    }

    /**
     * Registers an overlay that will be spawned with the companion
     */
    public void registerOverlay(final @NotNull String overlayId, final boolean lockedHeadYaw) {
        if (this.overlays.containsKey(overlayId)) {
            return;
        }

        final Level level = this.baseArmorStand.level();
        final OverlayEntity overlay = new OverlayEntity(
                overlayId,
                level,
                this.baseArmorStand.isSmall(),
                lockedHeadYaw,
                this
        );

        // Sync scale if present
        final AttributeInstance baseScale = this.baseArmorStand.getAttribute(Attributes.SCALE);
        if (baseScale != null) {
            final AttributeInstance overlayScale = overlay.armorStand().getAttribute(Attributes.SCALE);
            if (overlayScale != null) {
                overlayScale.setBaseValue(baseScale.getBaseValue());
            }
        }

        this.overlays.put(overlayId, overlay);
    }

    @Override
    public void spawn() {

        // Initial spawn position (behind player)
        final double yawRad = Math.toRadians(this.anchor.yaw());
        final double offX = -Math.sin(yawRad) * 1.5;
        final double offZ = Math.cos(yawRad) * 1.5;

        final double x = this.anchor.x() + offX;
        final double y = this.anchor.y() + 0.3;
        final double z = this.anchor.z() + offZ;
        final float yaw = (float) (Math.toDegrees(Math.atan2(-offZ, offX)) - 90);

        // Position base armor stand
        this.positionArmorStand(this.baseArmorStand, x, y, z, yaw);

        // Position all overlays
        for (final OverlayEntity overlay : this.overlays.values()) {
            this.positionArmorStand(overlay.armorStand(), x, y, z, yaw);
        }

        // Track owner as initial viewer
        if (this.anchor instanceof PlayerAnchor playerAnchor) {
            final Player player = playerAnchor.player();
            ViewerSnapshot snapshot = this.viewerEnvironment.snapshot(player);
            if (snapshot != null && this.isWithinViewDistance(snapshot)) {
                this.viewerStates.put(player, new ViewerState(snapshot));
                this.viewerTracker.add(player);
                this.spawnForViewer(player);
            }
        }

        this.spawned = true;
    }

    private void positionArmorStand(
            final @NotNull ArmorStand stand,
            final double x,
            final double y,
            final double z,
            final float yaw
    ) {
        stand.xo = x;
        stand.yo = y;
        stand.zo = z;
        stand.setYRot(yaw);
        stand.xOld = x;
        stand.yOld = y;
        stand.zOld = z;
    }

    private void spawnForViewer(final @NotNull Player viewer) {
        // Bundle 1: Spawn entities
        final List<Packet<? super ClientGamePacketListener>> spawnPackets = new ArrayList<>();

        spawnPackets.add(this.createAddEntityPacket(this.baseArmorStand));
        spawnPackets.add(this.createDataPacket(this.baseArmorStand));
        spawnPackets.add(this.createAttributesPacket(this.baseArmorStand));

        for (final OverlayEntity overlay : this.overlays.values()) {
            spawnPackets.add(this.createAddEntityPacket(overlay.armorStand()));
            spawnPackets.add(this.createDataPacket(overlay.armorStand()));
            spawnPackets.add(this.createAttributesPacket(overlay.armorStand()));
        }

        this.send(viewer, new ClientboundBundlePacket(spawnPackets));

        // Bundle 2: Equipment (mismo tick, pero después)
        final List<Packet<? super ClientGamePacketListener>> equipmentPackets = new ArrayList<>();

        if (!this.baseEquipment.isEmpty()) {
            equipmentPackets.add(this.buildEquipmentPacket(this.baseArmorStand.getId(), this.baseEquipment));
        }

        for (final OverlayEntity overlay : this.overlays.values()) {
            if (!overlay.equipment().isEmpty()) {
                equipmentPackets.add(this.buildEquipmentPacket(overlay.armorStand().getId(), overlay.nmsEquipment()));
            }
        }

        if (!equipmentPackets.isEmpty()) {
            this.send(viewer, new ClientboundBundlePacket(equipmentPackets));
        }
    }

    @Contract("_ -> new")
    private @NotNull ClientboundAddEntityPacket createAddEntityPacket(final @NotNull ArmorStand stand) {
        return new ClientboundAddEntityPacket(
                stand.getId(),
                stand.getUUID(),
                stand.xo,
                stand.yo,
                stand.zo,
                stand.getXRot(),
                stand.getYRot(),
                stand.getType(),
                stand.getId(),
                stand.getDeltaMovement(),
                0.0
        );
    }

    @Contract("_ -> new")
    private @NotNull ClientboundSetEntityDataPacket createDataPacket(final @NotNull ArmorStand stand) {
        return new ClientboundSetEntityDataPacket(stand.getId(), this.metadataValues(stand));
    }

    protected abstract List<SynchedEntityData.DataValue<?>> metadataValues(ArmorStand stand);

    protected abstract net.minecraft.network.chat.Component toNativeComponent(@Nullable Component component);

    protected abstract Component fromNativeComponent(net.minecraft.network.chat.Component component);

    private @NotNull ClientboundUpdateAttributesPacket createAttributesPacket(final @NotNull ArmorStand stand) {
        final AttributeInstance attributeInstance = stand.getAttribute(Attributes.SCALE);
        return new ClientboundUpdateAttributesPacket(
                stand.getId(),
                attributeInstance == null ? Collections.emptyList() : List.of(attributeInstance)
        );
    }

    // ------------------ VIEWER HANDLING ------------------
    private void findNewViewers() {
        for (final ViewerSnapshot snapshot : this.viewerEnvironment.snapshots()) {
            Player onlinePlayer = snapshot.player();
            if (this.isWithinViewDistance(snapshot)) {
                if (this.viewerTracker.isViewer(onlinePlayer)) {
                    continue;
                }

                this.viewerTracker.add(onlinePlayer);
                this.viewerStates.put(onlinePlayer, new ViewerState(snapshot));
                this.spawnForViewer(onlinePlayer);
                this.notifyViewerAdded(onlinePlayer);
            }
        }
    }

    private void updateViewers() {
        boolean checkDistance = this.viewerTrackerTick++ >= this.viewerTrackerDelay;
        if (checkDistance) this.viewerTrackerTick = 0;

        final Iterator<Player> iterator = this.viewerTracker.viewers().iterator();
        while (iterator.hasNext()) {
            final Player viewer = iterator.next();
            ViewerSnapshot snapshot = this.viewerEnvironment.snapshot(viewer);
            ViewerState state = this.viewerStates.get(viewer);
            if (snapshot == null || state == null || !state.active || !state.snapshot.sessionId().equals(snapshot.sessionId())
                    || !this.viewerEnvironment.visible(snapshot, this.visualOwner())
                    || checkDistance && !this.isWithinViewDistance(snapshot)) {
                this.despawnForViewer(viewer);
                iterator.remove();
                this.notifyViewerRemoved(viewer);
            }
        }
    }

    private void notifyViewerAdded(final @NotNull Player viewer) {
        if (this.viewerListeners.isEmpty()) {
            return;
        }

        for (final CompanionViewerListener listener : this.viewerListeners) {
            listener.onViewerAdded(viewer);
        }
    }

    private void notifyViewerRemoved(final @NotNull Player viewer) {
        if (this.viewerListeners.isEmpty()) {
            return;
        }

        for (final CompanionViewerListener listener : this.viewerListeners) {
            listener.onViewerRemoved(viewer);
        }
    }

    private boolean isWithinViewDistance(final @NotNull ViewerSnapshot viewer) {
        return this.allowsViewer(viewer.player())
                && viewer.within(this.worldId, this.anchor.x(), this.anchor.y(), this.anchor.z(), this.viewDistance)
                && this.viewerEnvironment.visible(viewer, this.visualOwner());
    }

    private Player visualOwner() {
        return this.anchor instanceof PlayerAnchor playerAnchor ? playerAnchor.player() : null;
    }

    @Override
    public void restrictViewer(Player viewer) {
        if (this.spawned || this.restrictedViewer != null) {
            throw new IllegalStateException("Viewer restriction must be set once before spawning");
        }
        this.restrictedViewer = Objects.requireNonNull(viewer, "viewer");
    }

    private boolean allowsViewer(Player viewer) {
        return this.restrictedViewer == null || this.restrictedViewer == viewer;
    }

    private void send(Player viewer, Packet<? super ClientGamePacketListener> packet) {
        ViewerState state = this.viewerStates.get(viewer);
        if (state != null) {
            this.viewerEnvironment.deliverVisual(state.snapshot, this.visualOwner(), this.worldId, -1, () -> state.active,
                    () -> ((CraftPlayer) viewer).getHandle().connection.send(packet), () -> state.active = false);
        }
    }

    private void removeForViewer(Player viewer, ClientboundRemoveEntitiesPacket packet) {
        ViewerState state = this.viewerStates.remove(viewer);
        if (state != null) {
            state.active = false;
            this.viewerEnvironment.deliver(state.snapshot, this.worldId, () -> true,
                    () -> ((CraftPlayer) viewer).getHandle().connection.send(packet));
        }
    }

    private void despawnForViewer(final @NotNull Player viewer) {
        final int[] entityIds = new int[1 + this.overlays.size()];
        entityIds[0] = this.baseArmorStand.getId();

        int index = 1;
        for (final OverlayEntity overlay : this.overlays.values()) {
            entityIds[index++] = overlay.armorStand().getId();
        }

        final ClientboundRemoveEntitiesPacket entitiesPacket = new ClientboundRemoveEntitiesPacket(entityIds);
        this.removeForViewer(viewer, entitiesPacket);
    }

    @Override
    public void tick() {
        this.tick(0);
    }

    @Override
    public boolean needsResynchronization() {
        return this.previousAnchor.needsResynchronization();
    }

    @Override
    public CompanionBehavior behavior() {
        return this.behavior;
    }

    // ------------------ TICK ------------------
    @Override
    public void tick(final double elapsedTime) {
        if (!this.isSpawned()) {
            return;
        }

        final long now = System.nanoTime();
        final double deltaTime = (now - this.lastTime) / 1_000_000_000.0;
        this.lastTime = now;

        this.updateViewers();
        this.findNewViewers();
        this.tickArmorCycles();

        final Collection<Player> viewers = this.viewerTracker.unmodifiableViewers();
        final AnimationContext context = new DefaultContext(
                this.anchor,
                viewers,
                this.previousAnchor.x(),
                this.previousAnchor.y(),
                this.previousAnchor.z(),
                this.baseArmorStand.xo,
                this.baseArmorStand.yo,
                this.baseArmorStand.zo,
                this.previousEntityX,
                this.previousEntityY,
                this.previousEntityZ,
                this.baseArmorStand.getYRot(),
                this.rotY(this.baseArmorStand.getHeadPose()),
                this.rotX(this.baseArmorStand.getHeadPose()),
                deltaTime,
                elapsedTime,
                this.formatIndex,
                this.formatCount
        );

        final double snapshotX = this.baseArmorStand.xo;
        final double snapshotY = this.baseArmorStand.yo;
        final double snapshotZ = this.baseArmorStand.zo;

        final AnimationFrame animationFrame = this.animator.tick(context);

        if (this.anchor instanceof PlayerAnchor playerAnchor && playerAnchor.player().isDead()) {
            this.behavior.reset();
        }
        this.behavior.apply(context, animationFrame);

        this.applyFrame(animationFrame);
        this.flushDirty();

        final AnimationContext postContext = new DefaultContext(
                this.anchor,
                viewers,
                this.previousAnchor.x(),
                this.previousAnchor.y(),
                this.previousAnchor.z(),
                this.baseArmorStand.xo,   // ← coords ya finales
                this.baseArmorStand.yo,
                this.baseArmorStand.zo,
                snapshotX,
                snapshotY,
                snapshotZ,
                this.baseArmorStand.getYRot(),
                this.rotY(this.baseArmorStand.getHeadPose()),
                this.rotX(this.baseArmorStand.getHeadPose()),
                deltaTime,
                elapsedTime,
                this.formatIndex,
                this.formatCount
        );

        this.animator.runPostTracking(postContext);

        this.previousAnchor.advance();

        this.previousEntityX = snapshotX;
        this.previousEntityY = snapshotY;
        this.previousEntityZ = snapshotZ;

    }

    private void applyFrame(final @NotNull AnimationFrame frame) {
        // Movement
        final double newX = (frame.absoluteX != null ? frame.absoluteX : this.baseArmorStand.xo) + frame.deltaX;
        final double newY = (frame.absoluteY != null ? frame.absoluteY : this.baseArmorStand.yo) + frame.deltaY;
        final double newZ = (frame.absoluteZ != null ? frame.absoluteZ : this.baseArmorStand.zo) + frame.deltaZ;

        if (newX != this.baseArmorStand.xo || newY != this.baseArmorStand.yo || newZ != this.baseArmorStand.zo) {
            this.dirtyMovement = true;
        }

        this.baseArmorStand.xo = newX;
        this.baseArmorStand.yo = newY;
        this.baseArmorStand.zo = newZ;

        // Body rotation
        float bodyYaw =
                (frame.absoluteBodyYaw != null ? frame.absoluteBodyYaw : this.baseArmorStand.getYRot())
                        + frame.deltaBodyYaw;

        if (bodyYaw != this.baseArmorStand.getYRot()) {
            this.dirtyRotation = true;
        }

        this.baseArmorStand.setYRot(bodyYaw);
        this.baseArmorStand.yBodyRot = bodyYaw;

        // Head rotation
        float headPitch =
                (frame.absoluteHeadPitch != null ? frame.absoluteHeadPitch : this.rotX(this.baseArmorStand.getHeadPose()))
                        + frame.deltaHeadPitch;

        if (headPitch != this.rotX(this.baseArmorStand.getHeadPose())) {
            this.dirtyMetadata = true;
        }

        this.baseArmorStand.setHeadPose(new Rotations(headPitch, 0, 0));

        // Limb poses
        if (frame.leftArm != null) {
            this.baseArmorStand.setLeftArmPose(toNms(frame.leftArm));
            this.dirtyMetadata = true;
        }

        if (frame.rightArm != null) {
            this.baseArmorStand.setRightArmPose(toNms(frame.rightArm));
            this.dirtyMetadata = true;
        }

        if (frame.leftLeg != null) {
            this.baseArmorStand.setLeftLegPose(toNms(frame.leftLeg));
            this.dirtyMetadata = true;
        }

        if (frame.rightLeg != null) {
            this.baseArmorStand.setRightLegPose(toNms(frame.rightLeg));
            this.dirtyMetadata = true;
        }

        // Sync overlays with base
        for (final OverlayEntity overlay : this.overlays.values()) {
            overlay.syncPositionFrom(this.baseArmorStand);
            overlay.syncPoseFrom(this.baseArmorStand);
        }
    }

    private void tickArmorCycles() {
        if (this.armorCycles.isEmpty()) {
            return;
        }

        for (final Map.Entry<CycleTarget, ArmorCycleState> entry : this.armorCycles.entrySet()) {
            if (entry.getValue().tick()) {
                this.applyCycleFrame(entry.getKey(), entry.getValue());
            }
        }
    }

    private void applyCycleFrame(final @NotNull CycleTarget target, final @NotNull ArmorCycleState state) {
        final ItemStack updatedItem = this.buildFrameItem(state.provider(), state.currentFrame());

        if (target.overlayId() == null) {
            this.baseEquipment.put(target.slot(), updatedItem);
        } else {
            final OverlayEntity overlay = this.overlays.get(target.overlayId());
            if (overlay == null) {
                return;
            }

            overlay.setEquipment(target.slot(), updatedItem);
        }

        this.dirtyEquipment = true;
    }

    private @NotNull ItemStack buildFrameItem(final @NotNull ItemStackProvider provider, final @NotNull ArmorCycleEntry frame) {
        final org.bukkit.inventory.ItemStack bukkitItem = provider.provide();
        final org.bukkit.inventory.meta.ItemMeta meta = bukkitItem.getItemMeta();

        if (frame.rgbColor() != null && meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(frame.rgbColor());
        }

        if (frame.trimMaterial() != null && frame.trimPattern() != null && meta instanceof org.bukkit.inventory.meta.ArmorMeta armorMeta) {
            armorMeta.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(frame.trimMaterial(), frame.trimPattern()));
        }

        bukkitItem.setItemMeta(meta);
        return CraftItemStack.asNMSCopy(bukkitItem);
    }

    @Contract("_ -> new")
    private @NotNull Rotations toNms(com.volmit.bytecompanions.api.util.@NotNull Rotations rot) {
        return new Rotations(rot.x(), rot.y(), rot.z());
    }

    protected void flushDirty() {
        final List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();

        // Movement + rotation
        if (this.dirtyMovement || this.dirtyRotation) {
            final short dx = (short) ((this.baseArmorStand.xo - this.baseArmorStand.xOld) * 4096);
            final short dy = (short) ((this.baseArmorStand.yo - this.baseArmorStand.yOld) * 4096);
            final short dz = (short) ((this.baseArmorStand.zo - this.baseArmorStand.zOld) * 4096);
            final byte yaw = (byte) (this.baseArmorStand.getYRot() * ROTATION_CONVERSION);
            final byte pitch = (byte) (this.baseArmorStand.getXRot() * ROTATION_CONVERSION);

            // Base armor stand movement
            packets.add(this.createMovementPacket(
                    this.baseArmorStand.getId(),
                    dx, dy, dz,
                    yaw, pitch
            ));

            // Overlay movement
            for (final OverlayEntity overlay : this.overlays.values()) {
                packets.add(this.createMovementPacket(
                        overlay.armorStand().getId(),
                        dx, dy, dz,
                        yaw, pitch
                ));
            }
        }

        // Metadata (head pose, arms, legs, invisibility, name, etc.)
        if (this.dirtyMetadata) {
            packets.add(this.createDataPacket(this.baseArmorStand));

            for (final OverlayEntity overlay : this.overlays.values()) {
                packets.add(this.createDataPacket(overlay.armorStand()));
            }
        }

        // Equipment
        if (this.dirtyEquipment) {
            // Base equipment
            if (!this.baseEquipment.isEmpty()) {
                packets.add(this.buildEquipmentPacket(this.baseArmorStand.getId(), this.baseEquipment));
            }

            // Overlay equipment
            for (final OverlayEntity overlay : this.overlays.values()) {
                if (!overlay.equipment().isEmpty()) {
                    packets.add(this.buildEquipmentPacket(overlay.armorStand().getId(), overlay.nmsEquipment()));
                }
            }
        }

        if (!packets.isEmpty()) {
            final ClientboundBundlePacket bundlePacket = new ClientboundBundlePacket(packets);
            for (final Player viewer : this.viewerTracker.viewers()) {
                this.send(viewer, bundlePacket);
            }
        }

        // Reset flags
        this.dirtyMovement = false;
        this.dirtyRotation = false;
        this.dirtyMetadata = false;
        this.dirtyEquipment = false;

        // Sync old position
        this.baseArmorStand.xOld = this.baseArmorStand.xo;
        this.baseArmorStand.yOld = this.baseArmorStand.yo;
        this.baseArmorStand.zOld = this.baseArmorStand.zo;
    }

    protected ClientboundMoveEntityPacket createMovementPacket(
            int entityId, short dx, short dy, short dz, byte yaw, byte pitch
    ) {
        return new ClientboundMoveEntityPacket.PosRot(entityId, dx, dy, dz, yaw, pitch, false);
    }

    protected org.bukkit.inventory.ItemStack copyToBukkit(ItemStack item) {
        return CraftItemStack.asBukkitCopy(item);
    }

    @Contract("_, _ -> new")
    private @NotNull ClientboundSetEquipmentPacket buildEquipmentPacket(
            final int entityId,
            final @NotNull Map<EquipmentSlot, ItemStack> equipment
    ) {
        final List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>(equipment.size());
        equipment.forEach((slot, item) -> list.add(Pair.of(slot, item.copy())));
        return new ClientboundSetEquipmentPacket(entityId, list);
    }

    // ------------------ PUBLIC API ------------------
    @Override
    public @NonNull CompanionModel model() {
        return this.model;
    }

    @Override
    public boolean isSpawned() {
        return this.baseArmorStand != null && this.spawned;
    }

    @Override
    public int entityId() {
        return this.entityId;
    }

    @Override
    public double currentX() {
        return this.baseArmorStand != null ? this.baseArmorStand.xo : 0;
    }

    @Override
    public double currentY() {
        return this.baseArmorStand != null ? this.baseArmorStand.yo : 0;
    }

    @Override
    public double currentZ() {
        return this.baseArmorStand != null ? this.baseArmorStand.zo : 0;
    }

    @Override
    public void destroy() {
        Throwable behaviorFailure = null;
        try {
            this.behavior.close();
        } catch (RuntimeException | LinkageError failure) {
            behaviorFailure = failure;
        }
        try {
            if (this.isSpawned() || !this.viewerTracker.isEmpty()) {
                // Notify before clearing, so listeners can cleanup any per-viewer fake entities/links.
                for (final Player viewer : this.viewerTracker.viewers()) {
                    this.notifyViewerRemoved(viewer);
                }
                final int[] entityIds = new int[1 + this.overlays.size()];
                entityIds[0] = this.baseArmorStand.getId();
                int index = 1;
                for (final OverlayEntity overlay : this.overlays.values()) {
                    entityIds[index++] = overlay.armorStand().getId();
                }
                final ClientboundRemoveEntitiesPacket entitiesPacket = new ClientboundRemoveEntitiesPacket(entityIds);
                for (final Player viewer : this.viewerTracker.viewers()) {
                    this.removeForViewer(viewer, entitiesPacket);
                }
                this.baseArmorStand = null;
                this.overlays.clear();
                this.spawned = false;
                this.viewerTracker.clear();
            }
        } catch (RuntimeException | LinkageError failure) {
            if (behaviorFailure != null) failure.addSuppressed(behaviorFailure);
            throw failure;
        }
        if (behaviorFailure instanceof RuntimeException failure) throw failure;
        if (behaviorFailure instanceof LinkageError failure) throw failure;
    }

    @Override
    public void teleport(final @NotNull Location loc) {
        if (!this.isSpawned()) {
            return;
        }

        this.baseArmorStand.xo = loc.getX();
        this.baseArmorStand.yo = loc.getY();
        this.baseArmorStand.zo = loc.getZ();

        this.baseArmorStand.xOld = this.baseArmorStand.xo;
        this.baseArmorStand.yOld = this.baseArmorStand.yo;
        this.baseArmorStand.zOld = this.baseArmorStand.zo;

        // Sync overlays
        for (final OverlayEntity overlay : this.overlays.values()) {
            overlay.syncPositionFrom(this.baseArmorStand);
        }

        this.dirtyMovement = true;
        this.flushDirty();
    }

    @Override
    public void setPose(float headYaw, float bodyYaw) {
        if (!this.isSpawned()) {
            return;
        }

        this.baseArmorStand.setYRot(bodyYaw);
        this.baseArmorStand.yBodyRot = bodyYaw;

        final Rotations current = this.baseArmorStand.getHeadPose();
        this.baseArmorStand.setHeadPose(new Rotations(this.rotX(current), headYaw, this.rotZ(current)));

        // Sync overlays
        for (final OverlayEntity overlay : this.overlays.values()) {
            overlay.syncPoseFrom(this.baseArmorStand);
        }

        this.dirtyRotation = true;
        this.dirtyMetadata = true;

        this.flushDirty();
    }

    @Override
    public void lookAt(double x, double y, double z) {
        if (!this.isSpawned()) {
            return;
        }

        final double dx = x - this.baseArmorStand.xo;
        final double dy = y - this.baseArmorStand.yo;
        final double dz = z - this.baseArmorStand.zo;
        final double h = Math.sqrt(dx * dx + dz * dz);

        if (h < 1e-5) {
            return;
        }

        final float yaw = (float) (Math.toDegrees(Math.atan2(dx, dz)) - 90);
        final float pitch = (float) (-Math.toDegrees(Math.atan2(dy, h)));

        this.baseArmorStand.setYRot(yaw);
        this.baseArmorStand.yBodyRot = yaw;
        this.baseArmorStand.setHeadPose(new Rotations(pitch, 0, 0));

        // Sync overlays
        for (final OverlayEntity overlay : this.overlays.values()) {
            overlay.syncPoseFrom(this.baseArmorStand);
        }

        this.dirtyRotation = true;
        this.dirtyMetadata = true;

        this.flushDirty();
    }

    @Override
    public void customName(final @Nullable Component name) {
        if (!this.isSpawned()) {
            return;
        }

        this.baseArmorStand.setCustomName(this.toNativeComponent(name));
        this.dirtyMetadata = true;
        this.flushDirty();
    }

    @Override
    public @NotNull Component customName() {
        if (!this.isSpawned()) {
            return Component.empty();
        }

        return this.fromNativeComponent(this.baseArmorStand.getCustomName());
    }

    @Override
    public void setVisible(boolean visible) {
        if (!this.isSpawned()) {
            return;
        }

        this.baseArmorStand.setInvisible(!visible);
        this.dirtyMetadata = true;
        this.flushDirty();
    }

    @Override
    public void setSmall(boolean small) {
        if (!this.isSpawned()) {
            return;
        }

        this.baseArmorStand.setSmall(small);

        // Sync overlays
        for (final OverlayEntity overlay : this.overlays.values()) {
            overlay.armorStand().setSmall(small);
        }

        this.dirtyMetadata = true;
        this.flushDirty();
    }

    @Override
    public void setShowArms(boolean showArms) {
        if (!this.isSpawned()) {
            return;
        }

        this.baseArmorStand.setShowArms(showArms);
        this.dirtyMetadata = true;
        this.flushDirty();
    }

    @Override
    public void setShowBaseplate(boolean showBaseplate) {
        if (!this.isSpawned()) {
            return;
        }

        this.baseArmorStand.setNoBasePlate(!showBaseplate);
        this.dirtyMetadata = true;
        this.flushDirty();
    }

    @Override
    public void setMarker(boolean marker) {
        if (!this.isSpawned()) {
            return;
        }

        this.baseArmorStand.setMarker(marker);
        this.dirtyMetadata = true;
        this.flushDirty();
    }

    @Override
    public void setNameVisible(boolean visible) {
        if (!this.isSpawned()) {
            return;
        }

        this.baseArmorStand.setCustomNameVisible(visible);
        this.dirtyMetadata = true;
        this.flushDirty();
    }

    /**
     * Sets equipment for a specific overlay.
     *
     * @param overlayId The overlay identifier
     * @param slot      The equipment slot
     * @param item      The item to set
     * @return true if the equipment was successfully set, false otherwise
     * @throws IllegalArgumentException if the overlay doesn't exist
     */
    public boolean equipment(
            final @NotNull String overlayId,
            final org.bukkit.inventory.@NotNull EquipmentSlot slot,
            final org.bukkit.inventory.@Nullable ItemStack item
    ) {
        if (!this.isSpawned()) {
            return false;
        }

        final OverlayEntity overlay = this.overlays.get(overlayId);
        if (overlay == null) {
            throw new IllegalArgumentException("Overlay not found: " + overlayId);
        }

        final EquipmentSlot nmsSlot = NmsUtil.equipmentSlotToNms(slot);
        final ItemStack nmsItem = CraftItemStack.asNMSCopy(item);

        overlay.setEquipment(nmsSlot, nmsItem);
        this.dirtyEquipment = true;
        this.flushDirty();
        return true;
    }

    @Override
    public CompanionOverlay addOverlay(final @NotNull String overlayId, final @Nullable OverlayData overlayData) {
        //if (!this.isSpawned()) {
        //    return null;
        //}

        if (this.overlays.containsKey(overlayId)) {
            return null; // Overlay already exists
        }

        final ServerLevel level = ((CraftWorld) this.anchor.world()).getHandle();
        final Map<org.bukkit.inventory.EquipmentSlot, ItemStackProvider> overlayEquipment = overlayData.equipment();
        final OverlayEntity overlay = new OverlayEntity(
                overlayId,
                level,
                this.baseArmorStand.isSmall(),
                overlayData.lockHeadYaw(),
                this
        );

        for (Map.Entry<org.bukkit.inventory.EquipmentSlot, ItemStackProvider> entry : overlayEquipment.entrySet()) {
            overlay.setEquipment(NmsUtil.equipmentSlotToNms(entry.getKey()), CraftItemStack.asNMSCopy(entry.getValue().provide()));
        }

        // Sync scale if present
        final AttributeInstance baseScale = this.baseArmorStand.getAttribute(Attributes.SCALE);
        if (baseScale != null) {
            final AttributeInstance overlayScale = overlay.armorStand().getAttribute(Attributes.SCALE);
            if (overlayScale != null) {
                overlayScale.setBaseValue(baseScale.getBaseValue());
            }
        }

        // Position the new overlay at the same position as base
        overlay.syncPositionFrom(this.baseArmorStand);
        overlay.syncPoseFrom(this.baseArmorStand);

        // Spawn the overlay for all current viewers
        for (final Player viewer : this.viewerTracker.viewers()) {
            final List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
            packets.add(this.createAddEntityPacket(overlay.armorStand()));
            packets.add(this.createDataPacket(overlay.armorStand()));
            packets.add(this.createAttributesPacket(overlay.armorStand()));

            final ClientboundBundlePacket bundlePacket = new ClientboundBundlePacket(packets);
            this.send(viewer, bundlePacket);
        }

        this.overlays.put(overlayId, overlay);
        return overlay;
    }

    @Override
    public boolean removeOverlay(final @NotNull String overlayId) {
        if (!this.isSpawned()) {
            return false;
        }

        final OverlayEntity overlay = this.overlays.remove(overlayId);
        if (overlay == null) {
            return false; // Overlay doesn't exist
        }

        // Despawn the overlay for all viewers
        final ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(overlay.armorStand().getId());
        for (final Player viewer : this.viewerTracker.viewers()) {
            this.send(viewer, removePacket);
        }

        return true;
    }

    @Override
    public boolean hasOverlay(final @NotNull String overlayId) {
        return this.overlays.containsKey(overlayId);
    }

    @Override
    public void clearOverlays() {
        if (!this.isSpawned()) {
            return;
        }

        // Remove all overlays
        final List<String> overlayIds = new ArrayList<>(this.overlays.keySet());
        for (final String overlayId : overlayIds) {
            this.removeOverlay(overlayId);
        }
    }

    // TODO: implement this
    public boolean overlayVisible(@NotNull String overlayId, boolean visible) {
        if (!this.isSpawned()) {
            return false;
        }

        final OverlayEntity overlay = this.overlays.get(overlayId);
        if (overlay == null) {
            return false;
        }

        overlay.armorStand().setInvisible(!visible);
        this.dirtyMetadata = true;
        this.flushDirty();

        return true;
    }

    @Override
    public @Nullable CompanionOverlay getOverlay(final @NotNull String overlayId) {
        return this.overlays.get(overlayId);
    }

    @Override
    public void equipment(
            org.bukkit.inventory.@NotNull EquipmentSlot slot,
            org.bukkit.inventory.@Nullable ItemStack item
    ) {

        final EquipmentSlot nmsSlot = NmsUtil.equipmentSlotToNms(slot);
        final ItemStack nmsItem = CraftItemStack.asNMSCopy(item);

        this.baseEquipment.put(nmsSlot, nmsItem == null ? ItemStack.EMPTY : nmsItem);
        this.armorCycles.remove(new CycleTarget(null, nmsSlot));

        this.dirtyEquipment = true;
        if (this.isSpawned()) {
            this.flushDirty();
        }

    }

    @Override
    public void equipment(final @NotNull org.bukkit.inventory.EquipmentSlot equipmentSlot, final @NotNull ItemStackProvider itemStackProvider) {
        this.equipment(equipmentSlot, itemStackProvider.provide());
        this.registerOrClearCycle(null, NmsUtil.equipmentSlotToNms(equipmentSlot), itemStackProvider);
    }

    private void registerOrClearCycle(
            final @Nullable String overlayId,
            final EquipmentSlot slot,
            final ItemStackProvider provider
    ) {
        final CycleTarget target = new CycleTarget(overlayId, slot);
        this.armorCycles.remove(target);

        if (provider instanceof CyclableItemStackProvider armorProvider) {
            final ArmorCycle armorCycle = armorProvider.cycle();
            if (armorCycle != null && !armorCycle.frames().isEmpty()) {
                this.armorCycles.put(target, new ArmorCycleState(provider, armorCycle));
            }
        }
    }

    @Override
    public @NotNull @Unmodifiable Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> equipment() {
        final Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> result = new EnumMap<>(org.bukkit.inventory.EquipmentSlot.class);

        for (final Map.Entry<EquipmentSlot, ItemStack> entry : this.baseEquipment.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            final org.bukkit.inventory.EquipmentSlot bukkitSlot = NmsUtil.equipmentSlotToBukkitApi(entry.getKey());
            final org.bukkit.inventory.ItemStack bukkitItem = this.copyToBukkit(entry.getValue());
            result.put(bukkitSlot, bukkitItem);
        }

        return Collections.unmodifiableMap(result);
    }

    @Override
    public @NotNull @Unmodifiable Set<String> overlayIds() {
        return Collections.unmodifiableSet(this.overlays.keySet());
    }

    public @NotNull @Unmodifiable Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> overlayEquipment(
            final @NotNull String overlayId
    ) {
        final OverlayEntity overlay = this.overlays.get(overlayId);
        if (overlay == null) {
            return Collections.emptyMap();
        }

        final Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> itemStackMap =
                new EnumMap<>(org.bukkit.inventory.EquipmentSlot.class);

        overlay.nmsEquipment().forEach((equipmentSlot, itemStack) ->
                itemStackMap.put(
                        NmsUtil.equipmentSlotToBukkitApi(equipmentSlot),
                        this.copyToBukkit(itemStack)
                )
        );

        return Collections.unmodifiableMap(itemStackMap);
    }

    @Override
    public @NotNull Animations animations() {
        return this.animator.animationSet();
    }
}

