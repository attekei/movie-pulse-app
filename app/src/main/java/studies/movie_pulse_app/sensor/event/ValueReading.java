package studies.movie_pulse_app.sensor.event;

/**
 * Created by atte on 06/06/16.
 */
public class ValueReading {
    public long time; // System time in milliseconds
    public short value; // Pulse in 0-255

    public ValueReading(long time, short value) {
        this.time = time;
        this.value = value;
    }
}
