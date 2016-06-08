package studies.movie_pulse_app.sensor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.renderscript.Sampler;
import android.util.Log;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleScanResult;
import com.polidea.rxandroidble.exceptions.BleScanException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import studies.movie_pulse_app.sensor.event.BluetoothFailureEvent;
import studies.movie_pulse_app.sensor.event.ConnEstablishedEvent;
import studies.movie_pulse_app.sensor.event.ConnLostEvent;
import studies.movie_pulse_app.sensor.event.SensorEvent;
import studies.movie_pulse_app.sensor.event.ValueReading;
import studies.movie_pulse_app.sensor.event.ValueReadingsEvent;

public class BTSensor extends Sensor {
    private final Observable<SensorEvent> events;
    private final RxBleClient rxBleClient;
    public final static UUID UUID_BLE_SHIELD_RX =
            UUID.fromString("713d0002-503e-4c75-ba94-3148f18d941e");
    private Context ctx;

    public BTSensor(Context ctx) {
        this.ctx = ctx;

        rxBleClient = RxBleClient.create(ctx);

        // Restart the BT connection bootstrapping in the case of an error
        events = createEventsObservable().onErrorResumeNext(new Func1<Throwable, Observable<? extends SensorEvent>>() {
            @Override
            public Observable<? extends SensorEvent> call(Throwable e) {
                return BTSensor.this.getErrorRecoveryStream(e);
            }
        });
    }

    private Observable<SensorEvent> getErrorRecoveryStream(final Throwable e) {
        if (e instanceof BleScanException) {
            // No bluetooth enabled?
            return Observable.<SensorEvent>just(new BluetoothFailureEvent())
                    .doOnNext(new Action1<SensorEvent>() {
                        @Override
                        public void call(SensorEvent s) {
                            Log.i("BTSensor", "Scan error, trying to recover. Error: " + e.toString());
                        }
                    });
        } else {
            return Observable.<SensorEvent>just(new ConnLostEvent())
                    .doOnNext(new Action1<SensorEvent>() {
                        @Override
                        public void call(SensorEvent s) {
                            Log.i("BTSensor", "Connection lost, trying to recover. Error: " + e.toString());
                        }
                    })
                    .mergeWith(this.createEventsObservable())
                    .onErrorResumeNext(new Func1<Throwable, Observable<? extends SensorEvent>>() {
                        @Override
                        public Observable<? extends SensorEvent> call(Throwable e1) {
                            return BTSensor.this.getErrorRecoveryStream(e1);
                        }
                    });
        }
    }

    private Observable<SensorEvent> createEventsObservable() {

        Observable<RxBleConnection> connectionStream = rxBleClient.scanBleDevices()
                .doOnNext(new Action1<RxBleScanResult>() {
                    @Override
                    public void call(RxBleScanResult s) {
                        Log.i("BTSensor", String.format("Device found, name %s", s.getBleDevice().getName()));
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        Log.i("BTSensor", "Device scan ended");
                    }
                })
                .filter(new Func1<RxBleScanResult, Boolean>() {
                    @Override
                    public Boolean call(RxBleScanResult s) {
                        return s.getBleDevice().getName() != null && s.getBleDevice().getName().contains("Biscuit");
                    }
                })
                .doOnNext(new Action1<RxBleScanResult>() {
                    @Override
                    public void call(RxBleScanResult s) {
                        Log.i("BTSensor", "Sensor with correct name found");
                    }
                })
                .take(1) // End the device scan after finding one device
                .flatMap(new Func1<RxBleScanResult, Observable<? extends RxBleConnection>>() {
                    @Override
                    public Observable<? extends RxBleConnection> call(RxBleScanResult s) {
                        return s.getBleDevice().establishConnection(ctx, false);
                    }
                })
                .doOnNext(new Action1<RxBleConnection>() {
                    @Override
                    public void call(RxBleConnection s) {
                        Log.i("BTSensor", "Connection to the sensor successfully established");
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        Log.i("BTSensor", "All listeners unsubscribed, terminating sensor connection");
                    }
                })
                .share();

        Observable<Long> timeInStartGenerator = connectionStream.map(new Func1<RxBleConnection, Long>() {
            @Override
            public Long call(RxBleConnection n) {
                return System.currentTimeMillis();
            }
        });

        Observable<byte[]> rawReadingsStream = connectionStream
                .flatMap(new Func1<RxBleConnection, Observable<? extends Observable<byte[]>>>() {
                    @Override
                    public Observable<? extends Observable<byte[]>> call(RxBleConnection rxBleConnection) {
                        return rxBleConnection.setupNotification(UUID_BLE_SHIELD_RX);
                    }
                })
                .doOnNext(new Action1<Observable<byte[]>>() {
                    @Override
                    public void call(Observable<byte[]> r) {
                        Log.i("BTSensor", "Listening to notifications is set up");
                    }
                })
                .flatMap(new Func1<Observable<byte[]>, Observable<? extends byte[]>>() {
                    @Override
                    public Observable<? extends byte[]> call(Observable<byte[]> notificationObservable) {
                        return notificationObservable;
                    }
                });

        Observable<ValueReadingsEvent> readingsStream =
                Observable.combineLatest(rawReadingsStream, timeInStartGenerator, new Func2<byte[], Long, List<ValueReading>>() {
                    @Override
                    public List<ValueReading> call(byte[] bytes, Long timeInStart) {
                        return BTSensor.this.parseRawSensorReadings(bytes, timeInStart);
                    }
                })
                        .map(new Func1<List<ValueReading>, ValueReadingsEvent>() {
                            @Override
                            public ValueReadingsEvent call(List<ValueReading> valueReadings) {
                                return new ValueReadingsEvent(valueReadings);
                            }
                        })
                        .timeout(new Func1<ValueReadingsEvent, Observable<Long>>() {
                            @Override
                            public Observable<Long> call(ValueReadingsEvent e) {
                                return Observable.timer(3, TimeUnit.SECONDS);
                            }
                        });

        return Observable.<SensorEvent>merge(
                connectionStream.map(new Func1<RxBleConnection, ConnEstablishedEvent>() {
                    @Override
                    public ConnEstablishedEvent call(RxBleConnection c) {
                        return new ConnEstablishedEvent();
                    }
                }),
                readingsStream
        );
    }

    private List<ValueReading> parseRawSensorReadings(byte[] bytes, long timeInStart) {
        ArrayList<ValueReading> readings = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            int value = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i * 4, i * 4 + 4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            readings.add(new ValueReading(timeInStart + (long)(value >>> 8), (byte)(value & 0xFF)));
        }

        return readings;
    }

    @Override
    public Observable<SensorEvent> getEvents() {
        return events;
    }
}