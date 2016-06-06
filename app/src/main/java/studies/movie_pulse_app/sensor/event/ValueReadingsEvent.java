package studies.movie_pulse_app.sensor.event;

import java.util.List;

/**
 * Created by atte on 06/06/16.
 */
public class ValueReadingsEvent extends SensorEvent {
    public List<ValueReading> valueReadings;

    public ValueReadingsEvent(List<ValueReading> valueReadings) {
        this.valueReadings = valueReadings;
    }
}
