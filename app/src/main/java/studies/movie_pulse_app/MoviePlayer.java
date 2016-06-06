package studies.movie_pulse_app;

import android.graphics.Color;
import android.renderscript.Sampler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import studies.movie_pulse_app.sensor.MockSensor;
import studies.movie_pulse_app.sensor.Sensor;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_player);
        ButterKnife.bind(this);

        configurePulseChart();

        Observable<SensorEvent> sensorEvents = new MockSensor(true, 110).getEvents()
                .observeOn(AndroidSchedulers.mainThread());

        sensorEvents.ofType(SearchingDeviceEvent.class).subscribe(e -> this.showSearchingDevice());
        sensorEvents.ofType(ConnEstablishedEvent.class).subscribe(e -> this.startMovie());
        sensorEvents.ofType(ConnLostEvent.class).subscribe(e -> this.pauseMovieAndInformOfError());
        sensorEvents.ofType(ValueReadingsEvent.class).subscribe(this::addReadingToGraph);

        sensorEvents.subscribe(e -> Log.i("LULLERTS", e.getClass().getCanonicalName()));
    }

    private void configurePulseChart() {
        // The sensor library is not very good (doesn't support float values for the x axis),
        // but currently use it for the sake of prototyping
        pulseHistorySeries = new SimpleXYSeries("Pulse reading");
        pulseChart.setRangeBoundaries(0, 255, BoundaryMode.FIXED);
        pulseChart.setDomainBoundaries(0, 10000, BoundaryMode.FIXED);
        pulseChart.addSeries(pulseHistorySeries, new LineAndPointFormatter(Color.rgb(100, 100, 200), Color.BLACK, Color.BLACK, null));
        //pulseChart.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void addReadingToGraph(ValueReadingsEvent e) {
        statusText.setText("Fetched " + e.valueReadings.size() + " readings.");
        for (ValueReading reading : e.valueReadings) {
            Log.i("LULZ", "Current time: " + (reading.time - initialTime));
            if (pulseHistorySeries.size() > HISTORY_SIZE) {
                pulseHistorySeries.removeFirst();
            }

            long timeSinceBeginning = reading.time - initialTime;
            pulseHistorySeries.addLast(timeSinceBeginning, reading.value);

            pulseChart.redraw();

            pulseChart.setDomainBoundaries(Math.max(0, timeSinceBeginning - 5000), timeSinceBeginning, BoundaryMode.FIXED);
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
        statusText.setText("Connection lost, trying to recover...");
    }
}
