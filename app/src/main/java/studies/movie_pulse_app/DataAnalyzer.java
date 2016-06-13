package studies.movie_pulse_app;

import java.util.LinkedList;

/**
 * Created by znigeln on 2016-06-12.
 */
public class DataAnalyzer {

    private LinkedList<SensorDataInstance> data;
    private LinkedList<Long> heartbeats;
    private boolean wasExciting = false;

    // The amount of heartbeats looked at to calculate the average interval
    private int averageCalcIterationLength = 50;
    // The amount of consecutive heart beats with "exciting" levels reached in a row to evaluate as exciting
    private int highPulseHeartBeatsNeeded = 20;
    // The threshold at which an interval will be deemed as exciting, (If intrvl <= avg / threshold)
    private float excitementThreshold = 1.3f;

    public DataAnalyzer(LinkedList<SensorDataInstance> data) {
        this.data = data;
        calculateHeartbeats();
    }

    public boolean wasExciting() {
        return wasExciting;
    }

    private void calculateHeartbeats() {

        boolean goingUp = false;
        heartbeats = new LinkedList<>();
        byte lastData = 0;

        for (SensorDataInstance s : data) {
            // If we are at a peak
            if (s.getValue() < lastData && goingUp && s.getValue() > -40) {
                heartbeats.addLast(s.getTime());
                goingUp = false;
            } else if (s.getValue() >= lastData) {
                goingUp = true;
            }
            lastData = s.getValue();
        }

        int len = Math.min(averageCalcIterationLength,heartbeats.size());
        int i = len;
        long longestInterval = 0;

        long lastTime = heartbeats.peekFirst();

        for (Long t : heartbeats) {
            if (i == len) {
                i--;
                continue;
            } else if (i > 0) {
                longestInterval = Math.max(longestInterval,(t-lastTime));
                i--;
            } else {
                break;
            }
            lastTime = t;
        }

        lastTime = heartbeats.peekFirst();
        int consecutiveExciting = 0;

        for (Long t : heartbeats) {
            long timeSinceLast = (t-lastTime);
            if (timeSinceLast <= longestInterval/excitementThreshold) {
                consecutiveExciting++;
                if (consecutiveExciting > highPulseHeartBeatsNeeded) {
                    wasExciting = true;
                }
            } else {
                consecutiveExciting = 0;
            }
            lastTime = t;
        }
    }

}
