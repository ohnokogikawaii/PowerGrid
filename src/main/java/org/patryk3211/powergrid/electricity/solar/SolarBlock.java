package org.patryk3211.powergrid.electricity.solar;

import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.patryk3211.powergrid.collections.ModdedBlockEntities;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

public class SolarBlock
        extends DirectionalElectricBlock
        implements IBE<SolarBlockEntity> {

    /*
     * Solar panel本体。
     *
     * Blockbenchのモデル:
     * Y = 4 ～ 5
     *
     * つまり基準状態では水平なパネル。
     */
    private static final VoxelShape SHAPE =
            box(
                    0,
                    4,
                    0,
                    16,
                    5,
                    16
            );

    /*
     * l3 = ＋端子
     * n1 = －端子
     *
     * Blockbenchの座標をそのまま使用。
     */
    private static final TerminalBoundingBox[] TERMINALS = {

            // ＋端子 l3
            new TerminalBoundingBox(
                    IDecoratedTerminal.POSITIVE,
                    3.75,
                    3.5,
                    10.75,
                    4.75,
                    4.0,
                    11.25
            ).withColor(
                    IDecoratedTerminal.RED
            ),

            // －端子 n1
            new TerminalBoundingBox(
                    IDecoratedTerminal.NEGATIVE,
                    11.25,
                    3.5,
                    10.75,
                    12.25,
                    4.0,
                    11.25
            ).withColor(
                    IDecoratedTerminal.BLUE
            )
    };

    /*
     * Solar専用VoxelShaper。
     *
     * 基準方向を DOWN にする。
     *
     * 地面に置いた場合:
     * FACING = DOWN
     * → 水平のまま
     *
     * 天井:
     * FACING = UP
     * → 水平のまま
     *
     * 壁:
     * FACING = NORTH/SOUTH/EAST/WEST
     * → 垂直になる。
     */
    private static final VoxelShaper SOLAR_SHAPER =
            VoxelShaper.forDirectional(
                    SHAPE,
                    Direction.DOWN
            );

    public SolarBlock(Properties properties) {
        super(properties);

        /*
         * Solar専用の端子配置を登録。
         */
        setTerminalCollection(
                BlockStateTerminalCollection.builder(this)
                        .forAllStates(state ->
                                BlockStateTerminalCollection.each(
                                        TERMINALS,
                                        terminal -> rotateTerminal(
                                                terminal,
                                                state.getValue(FACING)
                                        )
                                )
                        )
                        .withShapeMapper(
                                state ->
                                        SOLAR_SHAPER.get(
                                                state.getValue(FACING)
                                        )
                        )
                        .build()
        );
    }

    /**
     * 基準状態は DOWN。
     *
     * DOWN:
     *   地面に水平
     *
     * UP:
     *   天井に水平
     *
     * NORTH/SOUTH/EAST/WEST:
     *   壁に垂直
     */
    private static TerminalBoundingBox rotateTerminal(
            TerminalBoundingBox terminal,
            Direction facing
    ) {
        return switch (facing) {

            case DOWN ->
                    terminal;

            case UP ->
                    terminal.rotateAroundX(180);

            case NORTH ->
                    terminal.rotateAroundX(270);

            case SOUTH ->
                    terminal.rotateAroundX(90);

            case EAST ->
                    terminal
                            .rotateAroundX(270)
                            .rotateAroundY(90);

            case WEST ->
                    terminal
                            .rotateAroundX(90)
                            .rotateAroundY(270)
                            .rotateAroundZ(180);
        };
    }

    @Override
    public Class<SolarBlockEntity> getBlockEntityClass() {
        return SolarBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SolarBlockEntity>
    getBlockEntityType() {
        return ModdedBlockEntities.SOLAR.get();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {

        /*
         * クリックした面の反対方向をSolarのFACINGにする。
         *
         * 地面をクリック:
         *   clickedFace = UP
         *   FACING      = DOWN
         *
         * 天井をクリック:
         *   clickedFace = DOWN
         *   FACING      = UP
         *
         * 壁をクリック:
         *   clickedFace = NORTH
         *   FACING      = SOUTH
         *
         * これによりSolarの表面が設置した側とは反対、
         * つまりプレイヤー側を向く。
         */
        Direction direction = ctx.getClickedFace().getOpposite();

        /*
         * Shiftで反対向きにする。
         */
        if (ctx.getPlayer() != null
                && ctx.getPlayer().isShiftKeyDown()) {
            direction = direction.getOpposite();
        }

        return defaultBlockState()
                .setValue(FACING, direction);
    }
}