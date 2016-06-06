package studies.movie_pulse_app;

import android.graphics.Color;
import android.renderscript.Sampler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

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

    @BindView(R.id.pulse_chart) LineChart pulseChart;
    @BindView(R.id.status_text) TextView statusText;
    private long initialTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_player);
        ButterKnife.bind(this);


        initialTime = System.currentTimeMillis();
        configurePulseChart();

        Observable<SensorEvent> sensorEvents = new MockSensor().getEvents()
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

        LineData data = new LineData();
        pulseChart.setData(data);

        XAxis xl = pulseChart.getXAxis();
        //xl.setTypeface(tf);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setSpaceBetweenLabels(5);
        xl.setEnabled(true);

        YAxis leftAxis = pulseChart.getAxisLeft();
        //leftAxis.setTypeface(tf);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaxValue(255f);
        leftAxis.setAxisMinValue(0f);
        leftAxis.setDrawGridLines(true);

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void addReadingToGraph(ValueReadingsEvent e) {
        statusText.setText("Fetched " + e.valueReadings.size() + " readings.");
        for (ValueReading reading : e.valueReadings) {
            LineData data = pulseChart.getData();

            if (data != null) {

                ILineDataSet set = data.getDataSetByIndex(0);
                // set.addEntry(...); // can be called as well

                if (set == null) {
                    set = createSet();
                    data.addDataSet(set);
                }

                // NOTE: Currently this doesn't take account the "breaks" caused e.g. by a
                // lost connection. Maybe custom plotting should be used instead, which
                // could also lead to much better performance.

                data.addXValue(data.getXValCount() / 20.0 + "");
                data.addEntry(new Entry((float)reading.value, set.getEntryCount()), 0);

                // let the chart know it's data has changed
                pulseChart.notifyDataSetChanged();

                // limit the number of visible entries
                pulseChart.setVisibleXRangeMaximum(105);

                // move to the latest entry
                pulseChart.moveViewToX(Math.max(data.getXValCount() - 105, 0));

                // this automatically refreshes the chart (calls invalidate())
                // mChart.moveViewTo(data.getXValCount()-7, 55f,
                // AxisDependency.LEFT);
            }
        }
    }
    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    private void showSearchingDevice() {
        // Currently not being called, but not sure do i bother to fix it :)
        // Still, this is the state in the beginning any way, so maybe not needed
        // I keep it because I maybe want to use it with the real sensor
    }

    private void startMovie() {
        statusText.setText("Connection established!");
    }

    private void pauseMovieAndInformOfError() {
        statusText.setText("Connection lost, trying to recover...");
    }
}
