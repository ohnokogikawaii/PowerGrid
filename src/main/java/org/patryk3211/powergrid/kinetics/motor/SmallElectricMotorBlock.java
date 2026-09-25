package org.patryk3211.powergrid.kinetics.motor;

public class SmallElectricMotorBlock
        extends PhysicsMotorBlock {

    public SmallElectricMotorBlock(
            Properties properties
    ) {
        super(properties);
    }

    @Override
    public MotorParameters getMotorParameters() {
        return MotorParameters.small();
    }
}