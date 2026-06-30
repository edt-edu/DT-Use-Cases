package de.serwth.dt.energyusagemock;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EnergyUsageMockServiceTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String SORTING_TOPIC =
      "PLC/Island 1/SortingLine/I1SortingLine01/events/received/command";
  private static final String SORTING_FEEDBACK_TOPIC =
      "PLC/Island 1/SortingLine/I1SortingLine01/events/emitted/command_feedback";
  private static final String CONVEYOR_TOPIC =
      "PLC/Island 1/ConveyorBelt/I1ConveyorBelt01/events/received/command";
  private static final String CONVEYOR_FEEDBACK_TOPIC =
      "PLC/Island 1/ConveyorBelt/I1ConveyorBelt01/events/emitted/command_feedback";
  private static final String MULTIPROCESSING_TOPIC =
      "PLC/Island 1/MultiProcessing/I1MultiProcessing01/events/received/command";
  private static final String MULTIPROCESSING_FEEDBACK_TOPIC =
      "PLC/Island 1/MultiProcessing/I1MultiProcessing01/events/emitted/command_feedback";

  @Test
  void processesCommandAndProducesEnergyMeasurementWithoutMqtt() {
    EnergyUsageMockService service = new EnergyUsageMockService(config());

    service.handleMessage(
        "PLC/Island 1/SortingLine/I1SortingLine01/events/received/command",
        "{\"value\":\"COMMAND SORTING 17 EJECT payload\"}".getBytes(StandardCharsets.UTF_8)
    );

    List<EnergyMeasurement> measurements = service.energyMeasurements();

    assertEquals(1, measurements.size());
    EnergyMeasurement measurement = measurements.getFirst();
    assertEquals("PLC/Island 1/DrillingMachine/I1DrillingMachine01/events/emitted/energy_measurement",
        measurement.topic());
    assertEquals(850.0, measurement.powerW());
    assertEquals(EnergyOperation.DRILLING, measurement.operation());
    assertEquals("SortingLine", measurement.sourceDeviceClass());
    assertEquals("I1SortingLine01", measurement.sourceDeviceId());
  }

  @Test
  void drillingReturnsToBaselineAfterLoggedSortingOperationFinishes() throws JsonProcessingException {
    EnergyUsageMockService service = new EnergyUsageMockService(config());

    handleCommand(
        service,
        SORTING_TOPIC,
        "COMMAND SORTING 4 EJECT [<Color.RED: 1>]",
        "2025-10-13T13:28:13.529810+00:00Z"
    );
    handleFeedback(
        service,
        SORTING_FEEDBACK_TOPIC,
        4,
        "MUST_CONTINUE",
        "running subroutine 2/3: SubRoutine(Going to middle sensor)\n"
            + "Last subCycleStepResult:  CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:28:13.532638+00:00Z"
    );
    handleFeedback(
        service,
        SORTING_FEEDBACK_TOPIC,
        4,
        "MUST_CONTINUE",
        "running subroutine 3/3: SubRoutine(Ejecting payload)\n"
            + "Last subCycleStepResult: detectColorCycleStep CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:28:17.153560+00:00Z"
    );

    assertMeasurement(service, EnergyOperation.DRILLING, 850.0);

    handleFeedback(
        service,
        SORTING_FEEDBACK_TOPIC,
        4,
        "DONE",
        "routine finished\nLast subCycleStepResult: detectColorCycleStep CycleStepResultEnum.DONE",
        "2025-10-13T13:28:19.208319+00:00Z"
    );

    assertBaseline(service, EnergyOperation.DRILLING);
  }

  @Test
  void millingReturnsToBaselineAfterLoggedConveyorOperationFinishes() throws JsonProcessingException {
    EnergyUsageMockService service = new EnergyUsageMockService(config());

    handleCommand(
        service,
        CONVEYOR_TOPIC,
        "COMMAND CONVEYOR 6 MOVE_TO_SENSOR [<Direction.FORWARD: 1>]",
        "2025-10-13T13:28:45.396517+00:00Z"
    );
    handleFeedback(
        service,
        CONVEYOR_FEEDBACK_TOPIC,
        6,
        "MUST_CONTINUE",
        "running subroutine 1/2: SubRoutine(Move to sensor)\n"
            + "Last subCycleStepResult:  CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:28:45.398111+00:00Z"
    );
    handleFeedback(
        service,
        CONVEYOR_FEEDBACK_TOPIC,
        6,
        "MUST_CONTINUE",
        "running subroutine 1/2: SubRoutine(Move to sensor)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:28:49.176790+00:00Z"
    );

    assertMeasurement(service, EnergyOperation.MILLING, 712.5);

    handleFeedback(
        service,
        CONVEYOR_FEEDBACK_TOPIC,
        6,
        "DONE",
        "routine finished\nLast subCycleStepResult: target config reached CycleStepResultEnum.DONE",
        "2025-10-13T13:28:49.251670+00:00Z"
    );

    assertBaseline(service, EnergyOperation.MILLING);
  }

  @Test
  void loggedStartupSortingDoneBeforeOlderCommandDoesNotReactivateDrilling() throws JsonProcessingException {
    EnergyUsageMockService service = new EnergyUsageMockService(config());

    handleFeedback(
        service,
        SORTING_FEEDBACK_TOPIC,
        4,
        "DONE",
        "routine finished\nLast subCycleStepResult: detectColorCycleStep CycleStepResultEnum.DONE",
        "2025-10-13T13:18:39.047163+00:00Z"
    );
    handleCommand(
        service,
        SORTING_TOPIC,
        "COMMAND SORTING 4 EJECT [<Color.RED: 1>]",
        "2025-10-13T13:18:33.188303+00:00Z"
    );

    assertEquals(0, service.energyMeasurements().size());
  }

  @Test
  void loggedStartupConveyorDoneBeforeOlderCommandDoesNotReactivateMilling() throws JsonProcessingException {
    EnergyUsageMockService service = new EnergyUsageMockService(config());

    handleFeedback(
        service,
        CONVEYOR_FEEDBACK_TOPIC,
        6,
        "DONE",
        "routine finished\nLast subCycleStepResult: target config reached CycleStepResultEnum.DONE",
        "2025-10-13T13:19:09.176067+00:00Z"
    );
    handleCommand(
        service,
        CONVEYOR_TOPIC,
        "COMMAND CONVEYOR 6 MOVE_TO_SENSOR [<Direction.FORWARD: 1>]",
        "2025-10-13T13:19:05.356675+00:00Z"
    );

    assertEquals(0, service.energyMeasurements().size());
  }

  @Test
  void heatingReturnsToBaselineAfterLoggedMultiprocessingOperationFinishes() throws JsonProcessingException {
    EnergyUsageMockService service = new EnergyUsageMockService(config());

    handleLoggedMultiprocessingProcess1Until(service, "4/16: SubRoutine(heat payload)");

    assertMeasurement(service, EnergyOperation.HEATING, 2242.5);

    handleLoggedMultiprocessingDone(service);

    assertBaseline(service, EnergyOperation.HEATING);
  }

  @Test
  void grindingReturnsToBaselineAfterLoggedMultiprocessingOperationFinishes() throws JsonProcessingException {
    EnergyUsageMockService service = new EnergyUsageMockService(config());

    handleLoggedMultiprocessingProcess1Until(service, "14/16: SubRoutine(saw payload)");

    assertMeasurement(service, EnergyOperation.GRINDING, 1815.0);

    handleLoggedMultiprocessingDone(service);

    assertBaseline(service, EnergyOperation.GRINDING);
  }

  @Test
  void polishingReturnsToBaselineAfterLoggedMultiprocessingOperationFinishes() throws JsonProcessingException {
    EnergyUsageMockService service = new EnergyUsageMockService(config());

    handleLoggedMultiprocessingProcess1Until(service, "15/16: SubRoutine(eject payload)");

    assertMeasurement(service, EnergyOperation.POLISHING, 787.5);

    handleLoggedMultiprocessingDone(service);

    assertBaseline(service, EnergyOperation.POLISHING);
  }

  private void handleLoggedMultiprocessingProcess1Until(
      EnergyUsageMockService service,
      String stopAtInfoPart
  ) throws JsonProcessingException {
    handleCommand(
        service,
        MULTIPROCESSING_TOPIC,
        "COMMAND MULTIPROCESSING 11 PROCESS1 []",
        "2025-10-13T13:29:16.464226+00:00Z"
    );

    handleMultiprocessingFeedback(
        service,
        "running subroutine 1/16: SubRoutine(waiting for payload)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:16.467637+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 2/16: SubRoutine(open oven door)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:16.967990+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 3/16: SubRoutine(move payload into oven)\n"
            + "Last subCycleStepResult: moving oven feeder in CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:17.169028+00:00Z"
    );
    if (handleMultiprocessingFeedbackUntil(
        service,
        stopAtInfoPart,
        "running subroutine 4/16: SubRoutine(heat payload)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:19.518007+00:00Z"
    )) {
      return;
    }
    handleMultiprocessingFeedback(
        service,
        "running subroutine 5/16: SubRoutine(move out of oven)\n"
            + "Last subCycleStepResult: moving oven feeder out CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:21.524659+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 5/16: SubRoutine(move out of oven)\n"
            + "Last subCycleStepResult: moving arm to oven CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:23.894553+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 6/16: SubRoutine(lower arm)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:29.673559+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 7/16: SubRoutine(pickup payload)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:30.177934+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 8/16: SubRoutine(raise arm)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:30.678286+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 9/16: SubRoutine(go to turn table)\n"
            + "Last subCycleStepResult: moving arm to turn table CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:31.180840+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 10/16: SubRoutine(lower arm)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:39.312999+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 11/16: SubRoutine(drop of payload)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:39.817498+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 12/16: SubRoutine(raise arm)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:40.824440+00:00Z"
    );
    handleMultiprocessingFeedback(
        service,
        "running subroutine 13/16: SubRoutine(turn to saw)\n"
            + "Last subCycleStepResult: moving turn table to TurnTablePosition.SAW CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:41.325499+00:00Z"
    );
    if (handleMultiprocessingFeedbackUntil(
        service,
        stopAtInfoPart,
        "running subroutine 14/16: SubRoutine(saw payload)\n"
            + "Last subCycleStepResult: waiting for hold timer CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:42.895690+00:00Z"
    )) {
      return;
    }
    handleMultiprocessingFeedbackUntil(
        service,
        stopAtInfoPart,
        "running subroutine 15/16: SubRoutine(eject payload)\n"
            + "Last subCycleStepResult:  CycleStepResultEnum.MUST_CONTINUE",
        "2025-10-13T13:29:44.896126+00:00Z"
    );
  }

  private boolean handleMultiprocessingFeedbackUntil(
      EnergyUsageMockService service,
      String stopAtInfoPart,
      String info,
      String timestamp
  ) throws JsonProcessingException {
    handleMultiprocessingFeedback(service, info, timestamp);
    return info.contains(stopAtInfoPart);
  }

  private void handleMultiprocessingFeedback(
      EnergyUsageMockService service,
      String info,
      String timestamp
  ) throws JsonProcessingException {
    handleFeedback(service, MULTIPROCESSING_FEEDBACK_TOPIC, 11, "MUST_CONTINUE", info, timestamp);
  }

  private void handleLoggedMultiprocessingDone(EnergyUsageMockService service) throws JsonProcessingException {
    handleFeedback(
        service,
        MULTIPROCESSING_FEEDBACK_TOPIC,
        11,
        "DONE",
        "routine finished\nLast subCycleStepResult:  CycleStepResultEnum.DONE",
        "2025-10-13T13:29:47.353355+00:00Z"
    );
  }

  private void assertMeasurement(
      EnergyUsageMockService service,
      EnergyOperation operation,
      double powerW
  ) {
    List<EnergyMeasurement> measurements = service.energyMeasurements();
    EnergyMeasurement measurement = measurementFor(measurements, operation, powerW);

    assertNotNull(measurement);
    assertEquals(config().energyMeasurementTopic(operation), measurement.topic());
    assertEquals(operation, measurement.operation());
    assertEquals(powerW, measurement.powerW());
  }

  private void assertBaseline(EnergyUsageMockService service, EnergyOperation operation) {
    List<EnergyMeasurement> measurements = service.energyMeasurements();
    EnergyMeasurement measurement = measurementFor(measurements, operation, 0.0);

    assertNotNull(measurement);
    assertEquals(config().energyMeasurementTopic(operation), measurement.topic());
    assertEquals(operation, measurement.operation());
    assertEquals(0.0, measurement.powerW());
  }

  private EnergyMeasurement measurementFor(
      List<EnergyMeasurement> measurements,
      EnergyOperation operation,
      double powerW
  ) {
    return measurements.stream()
        .filter(measurement -> measurement.operation() == operation)
        .filter(measurement -> measurement.powerW() == powerW)
        .findFirst()
        .orElse(null);
  }

  private void handleCommand(
      EnergyUsageMockService service,
      String topic,
      String command,
      String timestamp
  ) throws JsonProcessingException {
    handle(service, topic, envelope(OBJECT_MAPPER.writeValueAsString(command), timestamp));
  }

  private void handleFeedback(
      EnergyUsageMockService service,
      String topic,
      int commandId,
      String status,
      String info,
      String timestamp
  ) throws JsonProcessingException {
    String feedback = OBJECT_MAPPER.writeValueAsString(new CommandFeedback(commandId, status, info));
    handle(service, topic, envelope(feedback, timestamp));
  }

  private void handle(EnergyUsageMockService service, String topic, String payload) {
    service.handleMessage(topic, payload.getBytes(StandardCharsets.UTF_8));
  }

  private String envelope(String value, String timestamp) throws JsonProcessingException {
    return OBJECT_MAPPER.writeValueAsString(new Envelope(value, timestamp));
  }

  private EnergyUsageMockConfig config() {
    return new EnergyUsageMockConfig(
        "localhost",
        1883,
        "test-client",
        1000,
        2000,
        "Island 1"
    );
  }

  private record Envelope(String value, String timestamp) {
  }

  private record CommandFeedback(String jsonType, int commandId, String status, String info) {
    CommandFeedback(int commandId, String status, String info) {
      this("COMMAND_FEEDBACK", commandId, status, info);
    }
  }
}
