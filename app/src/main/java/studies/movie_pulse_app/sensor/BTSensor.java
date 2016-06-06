package studies.movie_pulse_app.sensor;

import rx.Observable;
import studies.movie_pulse_app.sensor.event.SensorEvent;

/**
 * Created by atte on 06/06/16.
 */
public class BTSensor extends Sensor {
    public BTSensor() {
        throw new UnsupportedOperationException();
    }
    @Override
    public Observable<SensorEvent> getEvents() {
        throw new UnsupportedOperationException();
    }
}
