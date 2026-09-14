package org.patryk3211.powergrid.electricity.battery.lifepo4;


import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.battery.BatterySpec;


public abstract class BatteryBlockEntity
        extends ElectricBlockEntity {


    protected BatterySpec spec;


    protected double energy;

    protected double capacity;



    public BatteryBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state,
            BatterySpec spec
    ){

        super(type,pos,state);

        this.spec = spec;

        this.capacity =
                spec.getMaxCharge();

        this.energy =
                spec.getInitialCharge();

    }



    public double getEnergy(){

        return energy;
    }



    public void setEnergy(double energy){

        this.energy = energy;

    }



    public double getCapacity(){

        return capacity;

    }



    public BatterySpec getSpec(){

        return spec;

    }


}