package org.firstinspires.ftc.teamcode.a_opmodes.teleop;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.arcrobotics.ftclib.command.CommandScheduler;
import com.arcrobotics.ftclib.gamepad.GamepadKeys.Button;
import com.arcrobotics.ftclib.gamepad.GamepadKeys.Trigger;
import com.arcrobotics.ftclib.geometry.Vector2d;
import com.arcrobotics.ftclib.hardware.motors.Motor;
import com.arcrobotics.ftclib.hardware.motors.MotorEx;
import com.arcrobotics.ftclib.util.Direction;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.teamcode.b_hardware.subsystems.Intake;
import org.firstinspires.ftc.teamcode.c_drive.RRMecanumDrive.Mode;
import org.firstinspires.ftc.teamcode.d_util.utilclasses.TimingScheduler;

import java.util.Map;
import java.util.Map.Entry;

@TeleOp(name = "Main TeleOp", group = "Competition")
public class MainTeleOp extends BaseOpMode {//required vars here
  private double cycle = 0;
  private double prevRead = 0;
  private TimingScheduler timingScheduler;
  private boolean centricity = false;
  private boolean isManual = true;
  private int percent = 1, part = 0;
  private boolean isLeftIntakeRunning = false;




  //config? stuff here =========================================================================

  private double fieldCentricOffset = -90.0;
  public enum TemplateState{
    INTAKE(0.5),
    TRANSPORT(0.5),
    OUTTAKE(0.5);

    public final double progressRate;

    TemplateState(double progressRate){this.progressRate = progressRate;}
  }

  Map<TemplateState, Map<Button, TemplateState>> stateMap = new StateMap().getStateMap();

  public TemplateState state = TemplateState.INTAKE;


  //opmode vars here ==============================================================================================
  //If there is a module-specific var, put it in the module class ie slideStage goes in the slides module


  private MotorEx carousel;

  void subInit() {
    //TODO: initialize subsystems not initialized in bot constructor
    timingScheduler = new TimingScheduler(this);
    carousel = new MotorEx(hardwareMap, "carousel");
    carousel.setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE);
    carousel.set(0);
  }

  @Override
  public void subLoop() {
    //update stuff=================================================================================================
    cycle = 1.0/(time-prevRead);
    prevRead = time;
    timingScheduler.run();

    //Movement =================================================================================================
    //TODO: change depending on mode :)
    driveSpeed = 1 - 0.35 * (triggerSignal(Trigger.LEFT_TRIGGER) + triggerSignal(Trigger.RIGHT_TRIGGER));

    if(justPressed(Button.BACK)){
      isManual = !isManual;
    }

    if(isManual) {
      drive();
    }else{
      followPath();
    }


    //TODO: insert actual teleop stuff here
//    if(buttonSignal(Button.DPAD_UP)){
//      bot.carousel.run();
//    }else{
//      bot.carousel.stop();
//    }
//
    //clicking intake
    if(gamepadEx1.stateJustChanged(Button.X) && bot.intake.runState == Intake.state.OFF){
      bot.intake.run();
    }
    else if(gamepadEx1.stateJustChanged(Button.X) && bot.intake.runState == Intake.state.ON){
      bot.intake.stop();
    }

    //hold down button for intake
    if(gamepadEx1.isDown(Button.B)){
      bot.intake.run();
    }
    else{
      bot.intake.stop();
    }

    //hold down button for carousel
    if(gamepadEx1.isDown(Button.Y)){
      carousel.set(0.5);
    }
    else{
      carousel.set(0);
    }

    //clicking gate
    if(gamepadEx1.stateJustChanged(Button.A) && bot.gate.runState == Gate.state.OFF){
      bot.gate.closeGateFlap();
    }
    else if(gamepadEx1.stateJustChanged(Button.A) && bot.gate.runState == Gate.state.ON){
      bot.gate.openGateFlap();
    }

    //hold down button
    if(gamepadEx1.isDown(Button.A)){
      bot.gate.closeGateFlap();
    }
    else{
      bot.gate.openGateFlap();
    }


    //hold down trigger for outtake to go up
    if(gamepadEx2.isDown(Trigger.LEFT_TRIGGER)){
      bot.outtake.run();
    }
    else{
      bot.outtake.stop();
    }

    //hold down trigger for outtake to go down
    if(gamepadEx2.isDown(Trigger.RIGHT_TRIGGER)){
      bot.outtake.down();
    }
    else{
      bot.outtake.stop();
    }






    /*//TODO: make control scheme
    Controller 1
    A:      B:      X:      Y:
    DPAD
    L:      D:     U:      R:
    Joystick
    L:Field centric movement
    R:Set orientation / Rotation (Determine through practice)
    Trigger L/R: slow driving (maybe)
    Bumper
    L:none/switch to previous path      R:none/switch to next path
    Other
    Start:  Back:switch between automation and driving

    Controller 2
    A:      B:      X:      Y:
    DPAD
    L:      D:     U:      R:
    Joystick
    L:movement/reset field centric or progress automation
    R:movement/switch robotfield centric or none
    Trigger L/R: slow driving
    Bumper
    L:none/switch to previous path      R:none/switch to next path
    Other
    Start:  Back:switch between automation and driving
     */


    /*
    AUTOMATION CONTROL SCHEME

     */



    CommandScheduler.getInstance().run();

    // TODO organize this test code
    updateLocalization();
    telemetry.addData("percent", percent);
    telemetry.addData("part", part);
    telemetry.addData("cycle", cycle);
    telemetry.addData("x", bot.roadRunner.getPoseEstimate().getX());
    telemetry.addData("y", bot.roadRunner.getPoseEstimate().getY());
    telemetry.addData("heading", bot.roadRunner.getPoseEstimate().getHeading());
    telemetry.addData("current raw angle", bot.imu.getAngularOrientation().toAngleUnit(AngleUnit.DEGREES).firstAngle);
  }


  private void drive(){//Driving ===================================================================================
    updateState();

    final double gyroAngle =
        bot.imu.getAngularOrientation().toAngleUnit(AngleUnit.DEGREES).secondAngle//TODO: make sure that the orientation is correct
            - fieldCentricOffset;
    Vector2d driveVector = stickSignal(Direction.LEFT),
        turnVector = new Vector2d(
            stickSignal(Direction.RIGHT).getX() * Math.abs(stickSignal(Direction.RIGHT).getX()),
            0);
    if (bot.roadRunner.mode == Mode.IDLE) {
      if (centricity)//epic java syntax
        bot.drive.driveFieldCentric(
            driveVector.getX() * driveSpeed,
            driveVector.getY() * driveSpeed,
            turnVector.getX() * driveSpeed,
            gyroAngle);
      else
        bot.drive.driveRobotCentric(
            driveVector.getX() * driveSpeed,
            driveVector.getY() * driveSpeed,
            turnVector.getX() * driveSpeed
        );
    }
    if (justPressed(Button.LEFT_STICK_BUTTON)) {
      fieldCentricOffset = bot.imu.getAngularOrientation()
          .toAngleUnit(AngleUnit.DEGREES).firstAngle;
    }
    if(justPressed(Button.RIGHT_STICK_BUTTON)){
      centricity = !centricity;
    }
  }

  private void followPath(){//Path following ===================================================================================

    updateState();

  }

  private void updateState(){
    for(Entry<Button, TemplateState> pair : stateMap.get(state).entrySet()){
      if(justPressed(pair.getKey())){
        state = pair.getValue();
        percent = 0;
      }
    }
  }

  private void updateLocalization() {
    bot.roadRunner.update();
  }
}
