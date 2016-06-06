package studies.movie_pulse_app.sensor;

import java.util.List;

import rx.Observable;
import studies.movie_pulse_app.sensor.event.SensorEvent;
import studies.movie_pulse_app.sensor.event.ValueReadingsEvent;

/**
 * Created by atte on 06/06/16.
 */
abstract public class Sensor {
    abstract public Observable<SensorEvent> getEvents();
}
