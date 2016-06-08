package studies.movie_pulse_app.sensor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;
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

    public MockSensor(boolean simulateError) {
        // Funny hack to make timeInStart created at the point of subscription
        // (instead of only once when declaring MockSensor class)
        Observable<Long> timeInStartGenerator = Observable.just(null).map(new Func1<Object, Long>() {
            @Override
            public Long call(Object n) {
                return System.currentTimeMillis();
            }
        });
        Observable<Long> interval = Observable.interval(50, TimeUnit.MILLISECONDS);

        // Sensor readings as a sin wave
        // Currently buffered in 200ms interval (~4 readings per buffer)
        final Observable<ValueReadingsEvent> sensorReadings =
                Observable.combineLatest(interval, timeInStartGenerator, new Func2<Long, Long, long[]>() {
                    @Override
                    public long[] call(Long i, Long t) {
                        return new long[]{i, t};
                    }
                })
                        .map(new Func1<long[], ValueReading>() {
                            @Override
                            public ValueReading call(long[] val) {
                                long counterValue = val[0];
                                long timeInStart = val[1];
                                // Convert counter back to matching ellapsed time
                                long currentTime = timeInStart + counterValue * 50 + (int) (Math.random() * 30) - 15;
                                byte pulse = (byte) (120 + Math.sin(counterValue / 3.0) * 120);

                                return new ValueReading(currentTime, pulse);
                            }
                        })
                        .onBackpressureDrop()
                        .buffer(4)
                                // Quickfix to the bug caused by error situation simulation (.takeUntil causes backpressure)
                        .map((Func1<java.util.List<ValueReading>, ValueReadingsEvent>) new Func1<List<ValueReading>, ValueReadingsEvent>() {
                            @Override
                            public ValueReadingsEvent call(List<ValueReading> valueReadings) {
                                return new ValueReadingsEvent(valueReadings);
                            }
                        });

        // Connection establishment in the beginning
        Observable<SensorEvent> initialEvents = Observable.merge(
                Observable.just(new SearchingDeviceEvent()),
                Observable.timer(5, TimeUnit.SECONDS).map(new Func1<Long, ConnEstablishedEvent>() {
                    @Override
                    public ConnEstablishedEvent call(Long n) {
                        return new ConnEstablishedEvent();
                    }
                }),
                Observable.timer(6, TimeUnit.SECONDS).flatMap(new Func1<Long, Observable<? extends ValueReadingsEvent>>() {
                    @Override
                    public Observable<? extends ValueReadingsEvent> call(Long n) {
                        return sensorReadings;
                    }
                })
        );

        if (simulateError) {
            // After 30 seconds, simulate losing of the connection & connection recovery
            Observable<SensorEvent> simulateErrorEvents = Observable.timer(30, TimeUnit.SECONDS).flatMap(
                    new Func1<Long, Observable<? extends SensorEvent>>() {
                        @Override
                        public Observable<? extends SensorEvent> call(Long e) {
                            return Observable.merge(
                                    Observable.just(new ConnLostEvent()),
                                    Observable.timer(10, TimeUnit.SECONDS).map(new Func1<Long, ConnEstablishedEvent>() {
                                        @Override
                                        public ConnEstablishedEvent call(Long n) {
                                            return new ConnEstablishedEvent();
                                        }
                                    }),
                                    Observable.timer(11, TimeUnit.SECONDS).flatMap(new Func1<Long, Observable<? extends ValueReadingsEvent>>() {
                                        @Override
                                        public Observable<? extends ValueReadingsEvent> call(Long n) {
                                            return sensorReadings;
                                        }
                                    })
                            );
                        }
                    }
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