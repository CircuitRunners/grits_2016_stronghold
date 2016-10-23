package org.circuitrunners.grits_2016_stronghold;

import edu.wpi.first.wpilibj.*;
import org.circuitrunners.grits_2016_stronghold.RobotMap.JoystickType;
import org.circuitrunners.grits_2016_stronghold.RobotMap.SystemType;
import org.circuitrunners.grits_2016_stronghold.qa.QuestionAnswerFactory;
import org.circuitrunners.grits_2016_stronghold.system.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class Robot extends IterativeRobot {
    private ArrayList<Talon> driveSystem = new ArrayList<>(4);
    private ArrayList<RobotSystem> systems = new ArrayList<>(RobotMap.values().length - 4);

    RobotDrive drive;

    private Joystick joystick;
    private AutonomousJoystick lsd;
    Joystick xbox;

    private final RobotDriveThread robotDriveThread = new RobotDriveThread();

    // private DigitalInput intakeArmLimit; TODO: use intake arm limit switch

    @Override
    public void robotInit() {
        Arrays.stream(RobotMap.values()).parallel().forEach(system -> {
            switch (system.getType()) {
                case SYSTEM_MOTOR:
                    if (system.getButtons() != null) {
                        if (system.isAlternative()) {
                            systems.add(new InvertedRobotSystem(system.getButtons(), system.getJoystickType(), system.getForwardSpeed(), system.getBackwardSpeed(), Arrays.stream(system.getPorts()).mapToObj(Talon::new).toArray(Talon[]::new)));
                        } else {
                            systems.add(new BasicRobotSystem(system.getButtons(), system.getJoystickType(), system.getForwardSpeed(), system.getBackwardSpeed(), Arrays.stream(system.getPorts()).mapToObj(Talon::new).toArray(Talon[]::new)));
                        }
                    } else {
                        systems.add(new BasicRobotSystem(system.getAxis(), system.getJoystickType(), system.getForwardSpeed(), system.getBackwardSpeed(), Arrays.stream(system.getPorts()).mapToObj(Talon::new).toArray(Talon[]::new)));
                    }
                    break;
                case DRIVE_MOTOR:
                    Talon motor = new Talon(system.getPorts()[0]);
                    if (system.isAlternative()) {
                        motor.setInverted(true);
                    }
                    driveSystem.add(motor);
                    break;
                case DOUBLE_SOLENOID:
                    systems.add(new DoubleSolenoidRobotSystem(system.getButtons(), new DoubleSolenoid(1, system.getPorts()[0], system.getPorts()[1])));
                    break;
                case CAN:
                    if (system.getButtons() != null) {
                        systems.add(new CANRobotSystem(system.getButtons(), system.isAlternative(), Arrays.stream(system.getPorts()).mapToObj(CANTalon::new).toArray(CANTalon[]::new)));
                    } else {
                        systems.add(new CANRobotSystem(system.getAxis(), system.isAlternative(), Arrays.stream(system.getPorts()).mapToObj(CANTalon::new).toArray(CANTalon[]::new)));
                    }
                    break;
                case SWITCH:
                    systems.add(new SwitchSystem(system.getButtons(), system.getJoystickType(), Arrays.stream(system.getPorts()).mapToObj(Relay::new).toArray(Relay[]::new)));
                    break;
            }
        });

        driveSystem.sort(Comparator.comparingInt(PWM::getChannel));
        drive = new RobotDrive(driveSystem.get(0), driveSystem.get(1), driveSystem.get(2), driveSystem.get(3));

        joystick = new Joystick(0);
        xbox = new Joystick(1);

        // intakeArmLimit = new DigitalInput(1);

        String[] qa = QuestionAnswerFactory.produceQA();
        System.out.println("Question: " + qa[0] + " Answer: " + qa[1]);
    }

    @Override
    public void autonomousInit() {
        // TODO: finish enum-based autonomous
        lsd = new AutonomousJoystick(2);
        long endTime;
        for (AutonomousSteps autoStep : AutonomousSteps.values()) {
            RobotMap system = autoStep.getSystem();
            if (system.getType() == SystemType.DRIVE_MOTOR) {
                endTime = System.currentTimeMillis() - autoStep.getDuration();
                while (System.currentTimeMillis() < endTime) {
                    drive.arcadeDrive(autoStep.getDirection() ? 1 : -1, 0);
                }
                drive.arcadeDrive(0, 0);
            } else {
                ButtonGroup buttons = system.getButtons();
                int button;
                if (autoStep.getDirection()) {
                    lsd.setRawButton(button = buttons.getForward(), true);
                } else {
                    lsd.setRawButton(button = buttons.getBackward(), true);
                }
                endTime = System.currentTimeMillis() - autoStep.getDuration();
                while (System.currentTimeMillis() < endTime) {
                    systems.parallelStream().forEach(s -> {
                        if (s.getJoystickType() == system.getJoystickType()) {
                            s.run(lsd);
                        }
                    });
                }
                lsd.setRawButton(button, false);
                systems.parallelStream().forEach(s -> s.run(lsd));
            }
        }

        /*
        *** GRITS autonomous ***

        lsd.setRawButton(2, true);

        long stop = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < stop && limitSwitch.get()) {
            systems.parallelStream().forEach(s -> {
                if (s.getJoystickType() == RobotMap.JoystickType.XBOX) {
                    s.run(lsd);
                }
            });
        }
        lsd.setRawButton(2, false);
        systems.parallelStream().forEach(s -> s.run(lsd));

        stop = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < stop) {
            drive.arcadeDrive(-0.5, 0);
        }
        drive.arcadeDrive(0, 0);
        */
    }

    @Override
    public void teleopInit() {
        robotDriveThread.start();
    }

    @Override
    public void teleopPeriodic() {
        systems.parallelStream().forEach(s -> s.run(s.getJoystickType() == JoystickType.XBOX ? xbox : joystick)); // only works with two joysticks
    }

    private class RobotDriveThread extends Thread {

            @Override
            public void run() {
                while (isOperatorControl()) {
                    boolean thresholdL = Math.abs(xbox.getRawAxis(1)) >= 0.05;
                    boolean thresholdR = Math.abs(xbox.getRawAxis(4)) >= 0.05;
                    drive.arcadeDrive(thresholdL ? xbox.getRawAxis(1) : 0, thresholdR ? xbox.getRawAxis(4) : 0);
                }
                interrupt();
            }

            @Override
            public void interrupt() {
                drive.arcadeDrive(0, 0);
            }
    }


    @Override
    public void disabledInit() {
        drive.arcadeDrive(0, 0);
    }
}
