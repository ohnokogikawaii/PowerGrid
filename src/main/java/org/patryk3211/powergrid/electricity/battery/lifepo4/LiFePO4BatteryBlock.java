package org.patryk3211.powergrid.electricity.battery.lifepo4;

import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import org.patryk3211.powergrid.collections.ModdedBlockEntities;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.battery.AbstractBatteryBlock;
import org.patryk3211.powergrid.electricity.battery.BatterySpec;
import org.patryk3211.powergrid.electricity.deviceconnector.IAcceptConnector;
import org.patryk3211.powergrid.electricity.info.IHaveElectricProperties;
import org.patryk3211.powergrid.electricity.info.Voltage;
import org.patryk3211.powergrid.electricity.redstoneconverter.IRedstoneConverterBehaviour;
import org.patryk3211.powergrid.electricity.wire.powercord.AutoCordEndpoint;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;


public class LiFePO4BatteryBlock
        extends AbstractBatteryBlock<LiFePO4BatteryBlockEntity>
        implements
        IAcceptConnector,
        IHaveElectricProperties,
        IRedstoneConverterBehaviour {

    /*
     * -------------------------------------------------
     * Facing
     * -------------------------------------------------
     *
     * 水平方向のみ。
     *
     * 設置時にはバッテリーの正面が
     * プレイヤー側を向く。
     */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            HorizontalDirectionalBlock.FACING;


    /*
     * -------------------------------------------------
     * Block shape
     * -------------------------------------------------
     *
     * lifepo4.json
     *
     * Battery:
     *     [0, 0, 2] -> [16, 12, 14]
     *
     * Positive:
     *     [1, 12, 5] -> [3, 13, 7]
     *
     * Negative:
     *     [1, 12, 9] -> [3, 13, 11]
     *
     * 基準方向は NORTH。
     */
    private static final VoxelShape SHAPE =
            Shapes.or(
                    Block.box(
                            0, 0, 2,
                            16, 12, 14
                    ),
                    Block.box(
                            1, 12, 5,
                            3, 13, 7
                    ),
                    Block.box(
                            1, 12, 9,
                            3, 13, 11
                    )
            );


    /*
     * -------------------------------------------------
     * Directional shape
     * -------------------------------------------------
     *
     * SHAPEをFACINGに合わせて回転する。
     */
    private static final VoxelShaper BATTERY_SHAPER =
            VoxelShaper.forDirectional(
                    SHAPE,
                    Direction.NORTH
            );


    /*
     * -------------------------------------------------
     * Base terminals
     * -------------------------------------------------
     *
     * Terminal 0 = +
     * Terminal 1 = -
     *
     * 位置はlifepo4.jsonのBlockbench座標そのもの。
     */
    private static final TerminalBoundingBox[] TERMINALS = {

            /*
             * ＋端子
             */
            new TerminalBoundingBox(
                    IDecoratedTerminal.POSITIVE,
                    1, 12, 5,
                    3, 13, 7
            ).withColor(
                    IDecoratedTerminal.RED
            ),

            /*
             * －端子
             */
            new TerminalBoundingBox(
                    IDecoratedTerminal.NEGATIVE,
                    1, 12, 9,
                    3, 13, 11
            ).withColor(
                    IDecoratedTerminal.BLUE
            )
    };


    public LiFePO4BatteryBlock(Properties properties) {
        super(properties);

        /*
         * デフォルト方向
         */
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
        );


        /*
         * -------------------------------------------------
         * Electrical terminals
         * -------------------------------------------------
         *
         * FACINGに合わせて端子を回転。
         *
         * Device Connectorとは独立している。
         */
        setTerminalCollection(
                BlockStateTerminalCollection.builder(this)
                        .forAllStates(state ->
                                BlockStateTerminalCollection.each(
                                        TERMINALS,
                                        terminal ->
                                                rotateTerminal(
                                                        terminal,
                                                        state.getValue(FACING)
                                                )
                                )
                        )
                        .withShapeMapper(
                                state ->
                                        BATTERY_SHAPER.get(
                                                state.getValue(FACING)
                                        )
                        )
                        .build()
        );
    }


    /*
     * -------------------------------------------------
     * Terminal rotation
     * -------------------------------------------------
     *
     * 基準方向:
     *
     * NORTH
     *
     * + = x 1～3
     * - = x 1～3
     *
     * これをFACINGに合わせて回転する。
     */
    private static TerminalBoundingBox rotateTerminal(
            TerminalBoundingBox terminal,
            Direction facing
    ) {
        return switch (facing) {

            case NORTH ->
                    terminal;

            case EAST ->
                    terminal.rotateAroundY(90);

            case SOUTH ->
                    terminal.rotateAroundY(180);

            case WEST ->
                    terminal.rotateAroundY(270);

            default ->
                    terminal;
        };
    }


    /*
     * -------------------------------------------------
     * Placement
     * -------------------------------------------------
     *
     * プレイヤー側を向く。
     *
     * プレイヤーが北側にいる
     *     → SOUTH
     *
     * プレイヤーが南側にいる
     *     → NORTH
     *
     * という関係になる。
     */
    @Override
    public @Nullable BlockState getStateForPlacement(
            BlockPlaceContext context
    ) {
        Direction direction =
                context.getHorizontalDirection().getOpposite();

        return defaultBlockState()
                .setValue(FACING, direction);
    }


    /*
     * -------------------------------------------------
     * Block state
     * -------------------------------------------------
     */
    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        super.createBlockStateDefinition(builder);

        builder.add(FACING);
    }


    /*
     * -------------------------------------------------
     * Battery specification
     * -------------------------------------------------
     */
    @Override
    public BatterySpec getSpec() {
        return SimpleBatterySpec.LIFEPO4;
    }


    /*
     * -------------------------------------------------
     * Tooltip
     * -------------------------------------------------
     */
    @Override
    public void appendProperties(
            ItemStack stack,
            Player player,
            List<Component> tooltip
    ) {
        float maxCharge =
                getSpec().getMaxCharge();

        float charge;

        net.minecraft.world.item.component.CustomData customData =
                stack.get(DataComponents.CUSTOM_DATA);

        /*
         * CUSTOM_DATAを持たない通常のItemStackでも
         * クラッシュしないようにする。
         */
        CompoundTag tag =
                customData != null
                        ? customData.copyTag()
                        : new CompoundTag();

        if (tag.contains("Energy")) {

            charge = (float) (
                    tag.getDouble("Energy")
                            / maxCharge
            );

        } else {

            charge =
                    getSpec().getInitialCharge()
                            / maxCharge;
        }


        Voltage.max(
                getSpec().calculateVoltage(charge),
                player,
                tooltip
        );


        Lang.translate(
                        "tooltip.charge.current"
                )
                .style(ChatFormatting.GRAY)
                .addTo(tooltip);


        Lang.builder()
                .add(Component.literal(" "))
                .add(
                        Lang.numberConstant(
                                charge * 100
                        )
                )
                .add(Component.literal("%"))
                .style(ChatFormatting.AQUA)
                .addTo(tooltip);


        Lang.translate(
                        "tooltip.capacity"
                )
                .style(ChatFormatting.GRAY)
                .addTo(tooltip);


        Lang.builder()
                .add(Component.literal(" "))
                .add(
                        Lang.numberConstant(
                                maxCharge / 3600
                        )
                )
                .add(Component.literal(" "))
                .add(Unit.ENERGY.get())
                .style(ChatFormatting.GREEN)
                .addTo(tooltip);
    }


    /*
     * -------------------------------------------------
     * Device Connector
     * -------------------------------------------------
     *
     * 既存機能をそのまま維持。
     */
    @Override
    public boolean canConnect(
            LevelReader world,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        return IAcceptConnector.super.canConnect(
                world,
                pos,
                state,
                side
        );
    }


    @Override
    public boolean isPolarized() {
        return true;
    }


    @Override
    public boolean renderPlug() {
        return true;
    }


    @Override
    public @Nullable AutoCordEndpoint getEndpoint(
            UseOnContext context
    ) {
        return IAcceptConnector.super.getEndpoint(context);
    }


    @Override
    public @Nullable ITerminalPlacement cordTerminal(
            BlockState state,
            Level level,
            BlockHitResult hit
    ) {
        return IAcceptConnector.super.cordTerminal(
                state,
                level,
                hit
        );
    }


    /*
     * -------------------------------------------------
     * Shape
     * -------------------------------------------------
     *
     * FACINGに応じてShapeを回転。
     */
    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return BATTERY_SHAPER.get(
                state.getValue(FACING)
        );
    }


    /*
     * -------------------------------------------------
     * Collision shape
     * -------------------------------------------------
     *
     * FACINGに応じて当たり判定も回転。
     */
    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return BATTERY_SHAPER.get(
                state.getValue(FACING)
        );
    }


    /*
     * -------------------------------------------------
     * Redstone
     * -------------------------------------------------
     */
    @Override
    public float getSignal(
            Level level,
            BlockState state,
            BlockPos pos,
            Direction face
    ) {
        LiFePO4BatteryBlockEntity be =
                getBlockEntity(level, pos);

        if (be == null)
            return 0;

        return (float) (
                be.getEnergy()
                        / be.getCapacity()
        );
    }


    /*
     * -------------------------------------------------
     * Block Entity
     * -------------------------------------------------
     */
    @Override
    public Class<LiFePO4BatteryBlockEntity>
    getBlockEntityClass() {
        return LiFePO4BatteryBlockEntity.class;
    }


    @Override
    public BlockEntityType<? extends LiFePO4BatteryBlockEntity>
    getBlockEntityType() {
        return ModdedBlockEntities.LIFEPO4.get();
    }


    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new LiFePO4BatteryBlockEntity(
                ModdedBlockEntities.LIFEPO4.get(),
                pos,
                state
        );
    }


    @Override
    public void withBlockEntityDo(
            BlockGetter world,
            BlockPos pos,
            Consumer<LiFePO4BatteryBlockEntity> action
    ) {
        super.withBlockEntityDo(
                world,
                pos,
                action
        );
    }


    @Override
    public Optional<LiFePO4BatteryBlockEntity>
    getBlockEntityOptional(
            BlockGetter world,
            BlockPos pos
    ) {
        return super.getBlockEntityOptional(
                world,
                pos
        );
    }


    @Override
    public @Nullable LiFePO4BatteryBlockEntity
    getBlockEntity(
            BlockGetter world,
            BlockPos pos
    ) {
        return super.getBlockEntity(
                world,
                pos
        );
    }
}