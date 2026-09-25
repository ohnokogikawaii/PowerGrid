package org.patryk3211.powergrid.kinetics.motor;

public class LargeElectricMotorBlock
        extends PhysicsMotorBlock {

    public LargeElectricMotorBlock(
            Properties properties
    ) {
        super(properties);
    }

    @Override
    public MotorParameters getMotorParameters() {
        return MotorParameters.large();
    }
}