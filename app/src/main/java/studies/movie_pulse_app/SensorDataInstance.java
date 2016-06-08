package studies.movie_pulse_app;

/**
 * Created by znigeln on 2016-06-08.
 */
public class SensorDataInstance {

    private long time;
    private byte value;

    public SensorDataInstance(long time, byte value) {
        this.time = time;
        this.value = value;
    }

    public long getTime() { return time;}

    public byte getValue() { return value;}
}
