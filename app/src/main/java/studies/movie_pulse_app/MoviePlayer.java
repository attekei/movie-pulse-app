package studies.movie_pulse_app;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.renderscript.Sampler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.tbruyelle.rxpermissions.RxPermissions;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.observables.ConnectableObservable;
import rx.subscriptions.Subscriptions;
import studies.movie_pulse_app.sensor.BTSensor;
import studies.movie_pulse_app.sensor.MockSensor;
import studies.movie_pulse_app.sensor.Sensor;
import studies.movie_pulse_app.sensor.event.BluetoothFailureEvent;
import studies.movie_pulse_app.sensor.event.ConnEstablishedEvent;
import studies.movie_pulse_app.sensor.event.ConnLostEvent;
import studies.movie_pulse_app.sensor.event.SearchingDeviceEvent;
import studies.movie_pulse_app.sensor.event.SensorEvent;
import studies.movie_pulse_app.sensor.event.ValueReading;
import studies.movie_pulse_app.sensor.event.ValueReadingsEvent;

public class MoviePlayer extends AppCompatActivity {

    @BindView(R.id.pulse_chart) XYPlot pulseChart;
    @BindView(R.id.status_text) TextView statusText;
    private Long initialTime = null;
    private SimpleXYSeries pulseHistorySeries;
    private static final int HISTORY_SIZE = 300;
    private Subscription sensorEventsSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_player);
        ButterKnife.bind(this);

        configurePulseChart();

        Log.i("MoviePlayer","Starting to initialize sensor connection");


        ConnectableObservable<SensorEvent> sensorEvents = requestLocationPerm()
                .flatMap(p -> askUserWhetherToUseRealSensor())
                .flatMap(u -> u ? new BTSensor(this).getEvents() : new MockSensor(false).getEvents() )
                .observeOn(AndroidSchedulers.mainThread())
                .publish();

        // Use a centralised subscription using .publish() and .connect()
        // (unsubscribing is done in onDestroy)
        sensorEventsSubscription = sensorEvents.connect();

        sensorEvents.ofType(SearchingDeviceEvent.class).subscribe(e -> this.showSearchingDevice());
        sensorEvents.ofType(ConnEstablishedEvent.class).subscribe(e -> this.startMovie());
        sensorEvents.ofType(ConnLostEvent.class).subscribe(e -> this.pauseMovieAndInformOfError());
        sensorEvents.ofType(BluetoothFailureEvent.class).subscribe(e -> this.informOfBluetoothFailure());
        sensorEvents.ofType(ValueReadingsEvent.class).subscribe(this::addReadingToGraph);
    }

    Observable<Boolean> askUserWhetherToUseRealSensor() {
        return Observable.create((Subscriber<? super Boolean> subscriber) -> {
            final AlertDialog ad = new AlertDialog.Builder(this)
                    .setTitle("Which sensor implementation do you want to use?")
                    .setPositiveButton("BlueTooth", (dialog, which) -> {
                        subscriber.onNext(true);
                        subscriber.onCompleted();
                    })
                    .setNegativeButton("Mock", (dialog, which) -> {
                        subscriber.onNext(false);
                        subscriber.onCompleted();
                    })
                    .create();

            subscriber.add(Subscriptions.create(ad::dismiss));
            ad.show();
        });
    }

    private Observable<Boolean> requestLocationPerm() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is not enabled", Toast.LENGTH_SHORT).show();
            finish();
        }

        return RxPermissions.getInstance(this).request(Manifest.permission.BLUETOOTH_ADMIN);
    }

    private void configurePulseChart() {
        pulseHistorySeries = new SimpleXYSeries("Pulse reading");
        pulseChart.setRangeBoundaries(0, 255, BoundaryMode.FIXED);
        pulseChart.setDomainBoundaries(0, 10000, BoundaryMode.FIXED);
        pulseChart.addSeries(pulseHistorySeries, new LineAndPointFormatter(Color.rgb(100, 100, 200), Color.BLACK, Color.BLACK, null));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorEventsSubscription.unsubscribe();
    }

    private void addReadingToGraph(ValueReadingsEvent e) {
        statusText.setText(String.format("Fetched %d readings.", e.valueReadings.size()));

        for (ValueReading reading : e.valueReadings) {
            if (pulseHistorySeries.size() > HISTORY_SIZE) {
                pulseHistorySeries.removeFirst();
            }

            long timeSinceBeginning = reading.time - initialTime;
            pulseHistorySeries.addLast(timeSinceBeginning, reading.value & 0xFF);

            pulseChart.setDomainBoundaries(Math.max(0, timeSinceBeginning - 5000), timeSinceBeginning, BoundaryMode.FIXED);
            pulseChart.redraw();
        }
    }

    private void showSearchingDevice() {
        // Currently not being called, but not sure do i bother to fix it :)
        // Still, this is the state in the beginning any way, so maybe not needed
        // I keep it because I maybe want to use it with the real sensor

    }

    private void startMovie() {
        statusText.setText("Connection established!");
        if (initialTime == null) {
            initialTime = System.currentTimeMillis();
        }
    }

    private void pauseMovieAndInformOfError() {
        statusText.setText("Connection lost, trying to reconnect to the sensor...");
    }

    private void informOfBluetoothFailure() {
        statusText.setText("Bluetooth system failure, try restarting the app.");
    }

}
