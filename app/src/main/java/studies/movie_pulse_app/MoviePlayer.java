package studies.movie_pulse_app;

import android.Manifest;
import android.app.AlertDialog;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.tbruyelle.rxpermissions.RxPermissions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.LinkedList;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.subscriptions.Subscriptions;
import studies.movie_pulse_app.sensor.BTSensor;
import studies.movie_pulse_app.sensor.MockSensor;
import studies.movie_pulse_app.sensor.event.BluetoothFailureEvent;
import studies.movie_pulse_app.sensor.event.ConnEstablishedEvent;
import studies.movie_pulse_app.sensor.event.ConnLostEvent;
import studies.movie_pulse_app.sensor.event.SearchingDeviceEvent;
import studies.movie_pulse_app.sensor.event.SensorEvent;
import studies.movie_pulse_app.sensor.event.ValueReading;
import studies.movie_pulse_app.sensor.event.ValueReadingsEvent;

public class MoviePlayer extends AppCompatActivity implements StartDialogFragment.NoticeDialogListener {

    @BindView(R.id.pulse_chart) XYPlot pulseChart;
    @BindView(R.id.status_text) TextView statusText;
    private Long initialTime = null;
    private Long movieStartTime = null;
    private SimpleXYSeries pulseHistorySeries;
    private static final int HISTORY_SIZE = 300;
    private Subscription sensorEventsSubscription;
    private LinkedList<SensorDataInstance> dataValues = new LinkedList<>();
    private boolean moviePlaying = false;
    private final String dataFileName = "HeartReadings_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_player);
        ButterKnife.bind(this);

        configurePulseChart();

        Log.i("MoviePlayer","Starting to initialize sensor connection");

        Button finishButton = (Button) findViewById(R.id.finish_button);
        finishButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finishRecording();
            }
        });


        ConnectableObservable<SensorEvent> sensorEvents = requestLocationPerm()
                .flatMap(new Func1<Boolean, Observable<? extends Boolean>>() {
                    @Override
                    public Observable<? extends Boolean> call(Boolean p) {
                        return MoviePlayer.this.askUserWhetherToUseRealSensor();
                    }
                })
                .flatMap(new Func1<Boolean, Observable<? extends SensorEvent>>() {
                    @Override
                    public Observable<? extends SensorEvent> call(Boolean u) {
                        return u ? new BTSensor(MoviePlayer.this).getEvents() : new MockSensor(false).getEvents();
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .publish();

        // Use a centralised subscription using .publish() and .connect()
        // (unsubscribing is done in onDestroy)
        sensorEventsSubscription = sensorEvents.connect();

        sensorEvents.ofType(SearchingDeviceEvent.class).subscribe(new Action1<SearchingDeviceEvent>() {
            @Override
            public void call(SearchingDeviceEvent e) {
                MoviePlayer.this.showSearchingDevice();
            }
        });
        sensorEvents.ofType(ConnEstablishedEvent.class).subscribe(new Action1<ConnEstablishedEvent>() {
            @Override
            public void call(ConnEstablishedEvent e) {
                MoviePlayer.this.readyToStartMovie();
            }
        });
        sensorEvents.ofType(ConnLostEvent.class).subscribe(new Action1<ConnLostEvent>() {
            @Override
            public void call(ConnLostEvent e) {
                MoviePlayer.this.pauseMovieAndInformOfError();
            }
        });
        sensorEvents.ofType(BluetoothFailureEvent.class).subscribe(new Action1<BluetoothFailureEvent>() {
            @Override
            public void call(BluetoothFailureEvent e) {
                MoviePlayer.this.informOfBluetoothFailure();
            }
        });
        sensorEvents.ofType(ValueReadingsEvent.class).subscribe(new Action1<ValueReadingsEvent>() {
            @Override
            public void call(ValueReadingsEvent e) {
                MoviePlayer.this.addReadingToGraph(e);
            }
        });
    }

    Observable<Boolean> askUserWhetherToUseRealSensor() {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(final Subscriber<? super Boolean> subscriber) {
                final AlertDialog ad = new AlertDialog.Builder(MoviePlayer.this)
                        .setTitle("Which sensor implementation do you want to use?")
                        .setPositiveButton("BlueTooth", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                subscriber.onNext(true);
                                subscriber.onCompleted();
                            }
                        })
                        .setNegativeButton("Mock", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                subscriber.onNext(false);
                                subscriber.onCompleted();
                            }
                        })
                        .create();

                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        ad.dismiss();
                    }
                }));
                ad.show();
            }
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
            if (moviePlaying) dataValues.addLast(new SensorDataInstance(reading.time-movieStartTime,reading.value));
        }
    }

    private void showSearchingDevice() {
        // Currently not being called, but not sure do i bother to fix it :)
        // Still, this is the state in the beginning any way, so maybe not needed
        // I keep it because I maybe want to use it with the real sensor

    }

    private void readyToStartMovie() {
        statusText.setText("Connection established!");
        if (initialTime == null) {
            initialTime = System.currentTimeMillis();
        }
        showStartMovieDialog();
    }

    private void writeReadingsToFile() {
        FileOutputStream outputStream;

        String fileName = dataFileName + ((int)(Math.random() * 90000 + 10000)) + ".txt";

        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), fileName);

        try {
            outputStream = new FileOutputStream(file);
            PrintWriter pw = new PrintWriter(outputStream);

            for (SensorDataInstance s : dataValues) {
                String reading = "(" + s.getTime() + "," + s.getValue() + ")";
                pw.println(reading);
            }
            outputStream.close();
            Toast.makeText(this, "Wrote sensor data to file " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void finishRecording() {
        writeReadingsToFile();
        finish();
    }

    private void pauseMovieAndInformOfError() {
        statusText.setText("Connection lost, trying to reconnect to the sensor...");
    }

    private void informOfBluetoothFailure() {
        statusText.setText("Bluetooth system failure, try restarting the app.");
    }

    private void showStartMovieDialog() {
        DialogFragment dialog = new StartDialogFragment();
        dialog.show(getSupportFragmentManager(), "NoticeDialogFragment");
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {

        if (movieStartTime == null) {
            movieStartTime = System.currentTimeMillis();
        }
        moviePlaying = true;
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
        finish();
    }

}