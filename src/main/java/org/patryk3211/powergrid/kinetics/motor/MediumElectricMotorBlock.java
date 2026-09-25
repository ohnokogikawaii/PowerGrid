package org.patryk3211.powergrid.kinetics.motor;

public class MediumElectricMotorBlock
        extends PhysicsMotorBlock {

    public MediumElectricMotorBlock(
            Properties properties
    ) {
        super(properties);
    }

    @Override
    public MotorParameters getMotorParameters() {
        return MotorParameters.medium();
    }
}