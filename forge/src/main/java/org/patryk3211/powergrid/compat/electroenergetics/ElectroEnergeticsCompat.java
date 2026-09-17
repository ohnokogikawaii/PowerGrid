package org.patryk3211.powergrid.compat.electroenergetics;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.patryk3211.powergrid.PowerGrid;

public final class ElectroEnergeticsCompat {

    private ElectroEnergeticsCompat() {
    }

    /*
     * Do not use DeferredRegister.Blocks here.
     *
     * NeoForge 1.21.1's specialized DeferredRegister.Blocks registration
     * expects a ResourceLocation-based factory. Using the normal
     * DeferredRegister<Block> lets us use the standard Supplier form.
     */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, PowerGrid.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, PowerGrid.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE,
                    PowerGrid.MOD_ID
            );

    public static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
            DeferredRegister.create(
                    CEERegistries.SIMULATED_DEVICE_TYPE,
                    PowerGrid.MOD_ID
            );

    public static final DeferredHolder<Block, ElectroEnergeticsBridgeBlock> BRIDGE =
            BLOCKS.register(
                    "electroenergetics_bridge",
                    () -> new ElectroEnergeticsBridgeBlock(
                            Block.Properties.of()
                                    .strength(2.0f)
                    )
            );

    public static final DeferredHolder<Item, BlockItem> BRIDGE_ITEM =
            ITEMS.register(
                    "electroenergetics_bridge",
                    () -> new BlockItem(
                            BRIDGE.get(),
                            new Item.Properties()
                    )
            );

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<ElectroEnergeticsBridgeBlockEntity>
            > BRIDGE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(
                    "electroenergetics_bridge",
                    () -> BlockEntityType.Builder.of(
                            ElectroEnergeticsBridgeBlockEntity::new,
                            BRIDGE.get()
                    ).build(null)
            );

    public static final DeferredHolder<
            SimulatedDeviceType<?>,
            SimulatedDeviceType<ElectroEnergeticsBridgeDevice>
            > BRIDGE_DEVICE =
            DEVICES.register(
                    "electroenergetics_bridge",
                    () -> new SimulatedDeviceType<>(
                            PowerGrid.asResource("electroenergetics_bridge"),
                            ElectroEnergeticsBridgeDevice::new,
                            java.util.List.of(BRIDGE.get())
                    )
            );

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        DEVICES.register(bus);
    }
}