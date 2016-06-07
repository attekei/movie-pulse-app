package studies.movie_pulse_app.sensor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.renderscript.Sampler;
import android.util.Log;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.exceptions.BleScanException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import studies.movie_pulse_app.sensor.bluetooth.RBLService;
import studies.movie_pulse_app.sensor.event.BluetoothFailureEvent;
import studies.movie_pulse_app.sensor.event.ConnEstablishedEvent;
import studies.movie_pulse_app.sensor.event.ConnLostEvent;
import studies.movie_pulse_app.sensor.event.SensorEvent;
import studies.movie_pulse_app.sensor.event.ValueReading;
import studies.movie_pulse_app.sensor.event.ValueReadingsEvent;

public class BTSensor extends Sensor {
    private final Observable<SensorEvent> events;
    private final RxBleClient rxBleClient;
    private BluetoothAdapter mBluetoothAdapter;
    public static BluetoothDevice activeDevice = null;
    private static final long SCAN_PERIOD = 5000;
    private Context ctx;

    public BTSensor(Context ctx) {
        this.ctx = ctx;

        rxBleClient = RxBleClient.create(ctx);

        // Restart the BT connection bootstrapping in the case of an error
        events = createEventsObservable().onErrorResumeNext(this::getErrorRecoveryStream);
    }

    private Observable<SensorEvent> getErrorRecoveryStream(Throwable e) {
        if (e instanceof BleScanException) {
            // No bluetooth enabled?
            return Observable.<SensorEvent>just(new BluetoothFailureEvent())
                    .doOnNext(s -> Log.i("BTSensor", "Scan error, trying to recover. Error: " + e.toString() ));
        } else {
            return Observable.<SensorEvent>just(new ConnLostEvent())
                    .doOnNext(s -> Log.i("BTSensor", "Connection lost, trying to recover. Error: " + e.toString() ))
                    .mergeWith(this.createEventsObservable())
                    .onErrorResumeNext(this::getErrorRecoveryStream);
        }
    }

    private Observable<SensorEvent> createEventsObservable() {

        Observable<RxBleConnection> connectionStream = rxBleClient.scanBleDevices()
                .doOnNext(s -> Log.i("BTSensor", String.format("Device found, name %s", s.getBleDevice().getName())))
                .doOnUnsubscribe(() -> Log.i("BTSensor","Device scan ended"))
                .filter(s -> s.getBleDevice().getName() != null && s.getBleDevice().getName().contains("Biscuit"))
                .doOnNext(s -> Log.i("BTSensor", "Sensor with correct name found"))
                .take(1) // End the device scan after finding one device
                .flatMap(s -> s.getBleDevice().establishConnection(ctx, false))
                .doOnNext(s -> Log.i("BTSensor","Connection to the sensor successfully established"))
                .doOnUnsubscribe(() -> Log.i("BTSensor","All listeners unsubscribed, terminating sensor connection"))
                .share();

        Observable<Long> timeInStartGenerator = connectionStream.map(n -> System.currentTimeMillis());

        Observable<byte[]> rawReadingsStream = connectionStream
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(RBLService.UUID_BLE_SHIELD_RX))
                .doOnNext(r -> Log.i("BTSensor", "Listening to notifications is set up"))
                .flatMap(notificationObservable -> notificationObservable);

        Observable<ValueReadingsEvent> readingsStream =
                Observable.combineLatest(rawReadingsStream, timeInStartGenerator, this::parseRawSensorReadings)
                        .map(ValueReadingsEvent::new)
                        .timeout(e -> Observable.timer(3, TimeUnit.SECONDS));

        return Observable.<SensorEvent>merge(
                connectionStream.map(c -> new ConnEstablishedEvent()),
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
