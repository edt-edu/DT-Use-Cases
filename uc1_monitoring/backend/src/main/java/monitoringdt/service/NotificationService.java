package monitoringdt.service;

import functionstructure4fenix.DoubleStream;
import functionstructure4fenix.DoubleStreamObserver;
import functionstructure4fenix.DoubleValue;
import monitoringdt.EnergyShadow;
import monitoringdt.MonitoringDTManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class NotificationService {
  private static final Duration LIMIT_NOTIFICATION_COOLDOWN = Duration.ofSeconds(10);

  private LimitState lastLimitState = LimitState.OK;
  private LocalDateTime lastLimitNotificationIssued;

  public NotificationService() {
    initLimitNotificationObserver();
    addInitialNotification();
  }

  protected void addInitialNotification() {
    MonitoringDTManager.notificationBuilder()
        .issued(LocalDateTime.now())
        .content("System started")
        .seen(true)
        .build();
  }

  protected void initLimitNotificationObserver() {
    EnergyShadow es = MonitoringDTManager.getEnergyShadow();
    es.getEnergyDifference().addObserver(new DoubleStreamObserver() {
      @Override
      public void notifyAddDoubleValue(DoubleStream doubleStream, long arg, int indexInList) {
        Double warnLimit = MonitoringDTManager.getLimitConfiguration().getWarnLimit();
        Double errorLimit = MonitoringDTManager.getLimitConfiguration().getErrorLimit();

        DoubleValue v = doubleStream.getDoubleValue(indexInList);
        Double cur = v.getContent();
        LimitState currentState = determineLimitState(cur, warnLimit, errorLimit);

        if (currentState == LimitState.OK) {
          lastLimitState = currentState;
          return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (currentState != lastLimitState || cooldownElapsed(now)) {
          addLimitNotification(currentState, cur, warnLimit, errorLimit, now);
          lastLimitNotificationIssued = now;
        }
        lastLimitState = currentState;
      }
    });
  }

  protected LimitState determineLimitState(Double current, Double warnLimit, Double errorLimit) {
    if (current < errorLimit) {
      return LimitState.ERROR;
    }
    if (current < warnLimit) {
      return LimitState.WARNING;
    }
    return LimitState.OK;
  }

  protected boolean cooldownElapsed(LocalDateTime now) {
    return lastLimitNotificationIssued == null
        || !lastLimitNotificationIssued.plus(LIMIT_NOTIFICATION_COOLDOWN).isAfter(now);
  }

  protected void addLimitNotification(
      LimitState state,
      Double current,
      Double warnLimit,
      Double errorLimit,
      LocalDateTime issued
  ) {
    Double threshold = state == LimitState.ERROR ? errorLimit : warnLimit;
    String label = state == LimitState.ERROR ? "Error" : "Warning";

    MonitoringDTManager.notificationBuilder()
        .content(label + " threshold no longer met. Threshold is " + threshold + " but current value is " + current)
        .issued(issued)
        .seen(false)
        .build();
  }

  protected enum LimitState {
    OK,
    WARNING,
    ERROR
  }
}
