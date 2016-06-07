package studies.movie_pulse_app.sensor.event;

/**
 * Created by atte on 06/06/16.
 */
public class ValueReading {
    public long time; // System time in milliseconds
    public byte value; // Pulse in 0-255

    public ValueReading(long time, byte value) {
        this.time = time;
        this.value = value;
    }
}
