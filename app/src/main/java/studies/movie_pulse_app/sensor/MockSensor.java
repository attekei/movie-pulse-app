package studies.movie_pulse_app.sensor;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import studies.movie_pulse_app.sensor.event.ConnEstablishedEvent;
import studies.movie_pulse_app.sensor.event.ConnLostEvent;
import studies.movie_pulse_app.sensor.event.SearchingDeviceEvent;
import studies.movie_pulse_app.sensor.event.SensorEvent;
import studies.movie_pulse_app.sensor.event.ValueReading;
import studies.movie_pulse_app.sensor.event.ValueReadingsEvent;
/*
 A complete mock implementation of the Sensor abstract class
 The events emitted by getSensorEventStream() are the same that
 also the production version will emit.

 This mock version also simulates an error behaviour: after 30 seconds,
 the connection is interrupted for 10 seconds (ConnLostEvent), until it recovers (ConnEstablishedEvent).
 The SearchingDeviceEvent step is skipped in this case, though it may occur in the background as well.
*/

public class MockSensor extends Sensor {
    private final Observable<SensorEvent> events;

    public MockSensor(boolean simulateError, int readingInterval) {
        // Funny hack to make timeInStart created at the point of subscription
        // (instead of only once when declaring MockSensor class)
        Observable<Long> timeInStartGenerator = Observable.just(null).map(n -> System.currentTimeMillis());
        Observable<Long> interval = Observable.interval(50, TimeUnit.MILLISECONDS);

        // Sensor readings as a sin wave
        // Currently buffered in 200ms interval (~4 readings per buffer)
        Observable<ValueReadingsEvent> sensorReadings =
                Observable.combineLatest(interval, timeInStartGenerator, (i, t) -> new long[]{i, t})
                .map(val-> {
                    long counterValue = val[0];
                    long timeInStart = val[1];
                    // Convert counter back to matching ellapsed time
                    long currentTime = timeInStart + counterValue * 50 + (int)(Math.random() * 30) - 15;
                    short pulse = (short)(120 + Math.sin(counterValue / 3.0) * 120);

                    return new ValueReading(currentTime, pulse);
                })
                .onBackpressureDrop()
                .buffer(readingInterval, TimeUnit.MILLISECONDS)
                // Quickfix to the bug caused by error situation simulation (.takeUntil causes backpressure)
                .map(ValueReadingsEvent::new);

        // Connection establishment in the beginning
        Observable<SensorEvent> initialEvents = Observable.merge(
                Observable.just(new SearchingDeviceEvent()),
                Observable.timer(5, TimeUnit.SECONDS).map(n -> new ConnEstablishedEvent()),
                Observable.timer(6, TimeUnit.SECONDS).flatMap(n -> sensorReadings)
        );

        if (simulateError) {
            // After 30 seconds, simulate losing of the connection & connection recovery
            Observable<SensorEvent> simulateErrorEvents = Observable.timer(30, TimeUnit.SECONDS).flatMap(
                    e -> Observable.merge(
                            Observable.just(new ConnLostEvent()),
                            Observable.timer(10, TimeUnit.SECONDS).map(n -> new ConnEstablishedEvent()),
                            Observable.timer(11, TimeUnit.SECONDS).flatMap(n -> sensorReadings)
                    )
            );

            events = initialEvents.takeUntil(simulateErrorEvents)
                    .mergeWith(simulateErrorEvents)
                    .share();
        } else {
            events = initialEvents;
        }
    }

    public Observable<SensorEvent> getEvents() {
        return events;
    }
}
