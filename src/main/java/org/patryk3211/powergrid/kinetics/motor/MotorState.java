package org.patryk3211.powergrid.kinetics.motor;

/**
 * Runtime state of a physical DC motor.
 *
 * This class intentionally contains no Minecraft or Create dependencies.
 */
public final class MotorState {

    private double voltage;
    private double current;
    private double backEmf;

    private double electromagneticTorque;
    private double loadTorque;
    private double frictionTorque;
    private double netTorque;

    private double angularVelocity;
    private double angularAcceleration;

    private double rpm;

    private double electricalPower;
    private double mechanicalPower;
    private double copperLoss;

    private double temperature;

    public double voltage() {
        return voltage;
    }

    public void voltage(double value) {
        voltage = value;
    }

    public double current() {
        return current;
    }

    public void current(double value) {
        current = value;
    }

    public double backEmf() {
        return backEmf;
    }

    public void backEmf(double value) {
        backEmf = value;
    }

    public double electromagneticTorque() {
        return electromagneticTorque;
    }

    public void electromagneticTorque(double value) {
        electromagneticTorque = value;
    }

    public double loadTorque() {
        return loadTorque;
    }

    public void loadTorque(double value) {
        loadTorque = value;
    }

    public double frictionTorque() {
        return frictionTorque;
    }

    public void frictionTorque(double value) {
        frictionTorque = value;
    }

    public double netTorque() {
        return netTorque;
    }

    public void netTorque(double value) {
        netTorque = value;
    }

    public double angularVelocity() {
        return angularVelocity;
    }

    public void angularVelocity(double value) {
        angularVelocity = value;
    }

    public double angularAcceleration() {
        return angularAcceleration;
    }

    public void angularAcceleration(double value) {
        angularAcceleration = value;
    }

    public double rpm() {
        return rpm;
    }

    public void rpm(double value) {
        rpm = value;
    }

    public double electricalPower() {
        return electricalPower;
    }

    public void electricalPower(double value) {
        electricalPower = value;
    }

    public double mechanicalPower() {
        return mechanicalPower;
    }

    public void mechanicalPower(double value) {
        mechanicalPower = value;
    }

    public double copperLoss() {
        return copperLoss;
    }

    public void copperLoss(double value) {
        copperLoss = value;
    }

    public double temperature() {
        return temperature;
    }

    public void temperature(double value) {
        temperature = value;
    }

    public void reset() {
        voltage = 0;
        current = 0;
        backEmf = 0;
        electromagneticTorque = 0;
        loadTorque = 0;
        frictionTorque = 0;
        netTorque = 0;
        angularVelocity = 0;
        angularAcceleration = 0;
        rpm = 0;
        electricalPower = 0;
        mechanicalPower = 0;
        copperLoss = 0;
        temperature = 0;
    }
}