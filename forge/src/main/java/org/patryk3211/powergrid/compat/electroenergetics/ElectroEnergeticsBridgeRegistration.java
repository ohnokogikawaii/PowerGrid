package org.patryk3211.powergrid.compat.electroenergetics;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.patryk3211.powergrid.PowerGrid;

public final class ElectroEnergeticsBridgeRegistration {

    private ElectroEnergeticsBridgeRegistration() {
    }

    /*
     * ------------------------------------------------------------
     * Block
     * ------------------------------------------------------------
     */

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(
                    Registries.BLOCK,
                    PowerGrid.MOD_ID
            );

    public static final DeferredHolder<
            Block,
            ElectroEnergeticsBridgeBlock
            > BRIDGE_BLOCK =
            BLOCKS.register(
                    "electroenergetics_bridge",
                    () -> new ElectroEnergeticsBridgeBlock(
                            Block.Properties.of()
                                    .strength(2.0f)
                    )
            );

    /*
     * ------------------------------------------------------------
     * Item
     * ------------------------------------------------------------
     */

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(
                    Registries.ITEM,
                    PowerGrid.MOD_ID
            );

    public static final DeferredHolder<
            Item,
            BlockItem
            > BRIDGE_ITEM =
            ITEMS.register(
                    "electroenergetics_bridge",
                    () -> new BlockItem(
                            BRIDGE_BLOCK.get(),
                            new Item.Properties()
                    )
            );

    /*
     * ------------------------------------------------------------
     * Block Entity
     * ------------------------------------------------------------
     */

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE,
                    PowerGrid.MOD_ID
            );

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<ElectroEnergeticsBridgeBlockEntity>
            > BRIDGE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(
                    "electroenergetics_bridge",
                    () -> BlockEntityType.Builder.of(
                            ElectroEnergeticsBridgeBlockEntity::new,
                            BRIDGE_BLOCK.get()
                    ).build(null)
            );

    /*
     * ------------------------------------------------------------
     * Registration
     * ------------------------------------------------------------
     */

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);

        ElectroEnergeticsCompat.init(modBus);
    }
}