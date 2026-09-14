package org.patryk3211.powergrid.compat.tfmg;

import com.drmangotea.tfmg.content.electricity.base.ElectricBlockValues;
import com.drmangotea.tfmg.content.electricity.base.ElectricalNetwork;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jspecify.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.deviceconnector.BridgeElectricBehaviour;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlockEntity;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TFMGCompatDeviceConnectorBlockEntity extends DeviceConnectorBlockEntity implements IElectric {
    public ElectricBlockValues data = new ElectricBlockValues(getElectricPos());
    private int voltage;
    private boolean powerRefresh = false;
    private boolean firstUpdate = false;

    public TFMGCompatDeviceConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        data.connectNextTick = true;
    }

    @Override
    protected BridgeElectricBehaviour makeBridge() {
        return new TFMGBridgeElectricBehaviour(
                this,
                worldPosition.relative(getBlockState().getValue(DeviceConnectorBlock.FACING)),
                () -> converterWire
        );
    }

    @Override
    public void lazyTick() {
        super.lazyTick();

        var newVoltage = (int) Math.abs(converterWire.potentialDifference());

        if (newVoltage != voltage) {
            if (firstUpdate) {
                firstUpdate = false;
            } else {
                voltage = newVoltage;
                updateNetwork();
                refreshPower();
            }
        }

        lazyTickElectricity();
    }

    private void refreshPower() {
        powerRefresh = false;

        float power = getGeneratorLoad();
        var resistance = voltage * voltage / power;

        if (power > 0 && resistance > 0) {
            converterWire.setResistance(resistance);
        } else {
            converterWire.setResistance(1e+6);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (powerRefresh)
            refreshPower();

        tickElectricity();
    }

    /**
     * TFMG Community Edition 1.2.4b:
     * IElectric#getPos() returns long.
     */
    @Override
    public long getElectricPos() {
        // 自身の持つ BlockPos を安全に long に変換して返す（名前が被らないのでエラーになりません）
        return this.getBlockPos().asLong();
    }




    @Override
    public LevelAccessor getLevelAccessor() {
        return level;
    }

    @Override
    public boolean destroyed() {
        return IElectric.super.destroyed();
    }

    @Override
    public ElectricalNetwork getOrCreateElectricNetwork() {
        return IElectric.super.getOrCreateElectricNetwork();
    }

    @Override
    public ElectricBlockValues getData() {
        return data;
    }

    @Override
    public boolean canWork() {
        return IElectric.super.canWork();
    }

    @Override
    public void blockFail() {
        IElectric.super.blockFail();
    }

    @Override
    public float getPowerUsage() {
        return IElectric.super.getPowerUsage();
    }

    @Override
    public int getNetworkPowerUsage(IElectric blocked) {
        return IElectric.super.getNetworkPowerUsage(blocked);
    }

    @Override
    public float getNetworkPowerUsage() {
        return IElectric.super.getNetworkPowerUsage();
    }

    @Override
    public int getNetworkPowerGeneration() {
        return IElectric.super.getNetworkPowerGeneration();
    }

    @Override
    public float resistance() {
        return 0;
    }

    @Override
    public float resistance(String suffix) {
        return super.resistance(suffix);
    }

    @Override
    public void paused() {
        super.paused();
    }

    @Override
    public void unpaused() {
        super.unpaused();
    }

    @Override
    public int voltageGeneration() {
        return voltage;
    }

    @Override
    public void recalculateNetworkResistance() {
        IElectric.super.recalculateNetworkResistance();
    }

    @Override
    public int getMaxVoltage() {
        return 0;
    }

    @Override
    public int getMaxCurrent() {
        return IElectric.super.getMaxCurrent();
    }

    @Override
    public void onConnected() {
        IElectric.super.onConnected();
    }

    @Override
    public void updateUnpowered(List<BlockPos> alreadyChecked) {
        IElectric.super.updateUnpowered(alreadyChecked);
    }

    @Override
    public void doActionNextTick(Consumer<Integer> method) {
        IElectric.super.doActionNextTick(method);
    }

    @Override
    public boolean makeMultimeterTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return IElectric.super.makeMultimeterTooltip(tooltip, isPlayerSneaking);
    }

    @Override
    public void checkForFEOutputs(List<Direction> directions) {
        IElectric.super.checkForFEOutputs(directions);
    }

    @Override
    public float powerGeneration() {
        return ModdedConfigs.server().electricity.tfmgConnectorPower.get();
    }

    @Override
    public float getNetworkResistance() {
        return IElectric.super.getNetworkResistance();
    }

    @Override
    public boolean networkUndersupplied() {
        return IElectric.super.networkUndersupplied();
    }

    @Override
    public float getCurrent() {
        return IElectric.super.getCurrent();
    }

    @Override
    public void updateNextTick() {
        IElectric.super.updateNextTick();
    }

    @Override
    public void updateNetwork() {
        IElectric.super.updateNetwork();
    }

    @Override
    public void onNetworkChanged(int oldVoltage, float oldPower) {
        powerRefresh = true;
    }

    @Override
    public float getGeneratorResistance() {
        return IElectric.super.getGeneratorResistance();
    }

    @Override
    public float getGeneratorLoad() {
        return IElectric.super.getGeneratorLoad();
    }

    @Override
    public int getBlocksConnectedToNetworkCount(long id) {
        return IElectric.super.getBlocksConnectedToNetworkCount(id);
    }

    @Override
    public void sendStuff() {
        sendData();
    }

    @Override
    public void setVoltage(int newVoltage) {
        IElectric.super.setVoltage(newVoltage);
    }

    @Override
    public void setNetworkResistance(float newUsage) {
        IElectric.super.setNetworkResistance(newUsage);
    }

    @Override
    public void setNetwork(long network) {
        IElectric.super.setNetwork(network);
    }

    @Override
    public void remove() {
        super.remove();
        onRemoved();
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        readElectricity(compound, clientPacket);
    }

    @Override
    public boolean hasElectricitySlot(Direction direction) {
        return getBlockState().getValue(DeviceConnectorBlock.FACING) == direction;
    }

    @Override
    public void onPlaced() {
        IElectric.super.onPlaced();
    }

    @Override
    public void onRemoved() {
        IElectric.super.onRemoved();
    }

    @Override
    public void readElectricity(CompoundTag compound, boolean clientPacket) {
        IElectric.super.readElectricity(compound, clientPacket);
    }

    @Override
    public void tickElectricity() {
        IElectric.super.tickElectricity();
    }

    @Override
    public void sendEnergy() {
        IElectric.super.sendEnergy();
    }

    @Override
    public boolean isCable() {
        return IElectric.super.isCable();
    }

    @Override
    public void lazyTickElectricity() {
        IElectric.super.lazyTickElectricity();
    }

    @Override
    public boolean containedFluidTooltip(List<Component> tooltip, boolean isPlayerSneaking, IFluidHandler handler) {
        return super.containedFluidTooltip(tooltip, isPlayerSneaking, handler);
    }

    @Override
    public ItemStack getIcon(boolean isPlayerSneaking) {
        return super.getIcon(isPlayerSneaking);
    }

    @Override
    public <T> boolean hasData(Supplier<AttachmentType<T>> type) {
        return super.hasData(type);
    }

    @Override
    public <T> T getData(Supplier<AttachmentType<T>> type) {
        return super.getData(type);
    }

    @Override
    public <T> Optional<T> getExistingData(AttachmentType<T> type) {
        return super.getExistingData(type);
    }

    @Override
    public <T> Optional<T> getExistingData(Supplier<AttachmentType<T>> type) {
        return super.getExistingData(type);
    }

    @Override
    public @Nullable <T> T getExistingDataOrNull(Supplier<AttachmentType<T>> type) {
        return super.getExistingDataOrNull(type);
    }

    @Override
    public @Nullable <T> T setData(Supplier<AttachmentType<T>> type, T data) {
        return super.setData(type, data);
    }

    @Override
    public @Nullable <T> T removeData(Supplier<AttachmentType<T>> type) {
        return super.removeData(type);
    }

    @Override
    public void syncData(Supplier<? extends AttachmentType<?>> type) {
        super.syncData(type);
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    public void requestModelDataUpdate() {
        super.requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        return super.getModelData();
    }

    @Override
    public boolean hasCustomOutlineRendering(Player player) {
        return super.hasCustomOutlineRendering(player);
    }

    @Override
    public void invalidateCapabilities() {
        super.invalidateCapabilities();
    }
}

