package org.opentripplanner.jags.edgetype;

import java.util.Calendar;
import java.util.Date;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.opentripplanner.jags.core.State;
import org.opentripplanner.jags.core.TransportationMode;
import org.opentripplanner.jags.core.TraverseOptions;
import org.opentripplanner.jags.core.TraverseResult;

import com.vividsolutions.jts.geom.Geometry;

public class PatternBoard extends AbstractPayload {

    private static final long serialVersionUID = 1042740795612978747L;

    private static final long MILLI_IN_DAY = 24 * 60 * 60 * 1000;

    private static final int SEC_IN_DAY = 24 * 60 * 60;

    private TripPattern pattern;

    private int stopIndex;

    public PatternBoard(TripPattern pattern, int stopIndex) {
        this.pattern = pattern;
        this.stopIndex = stopIndex;
    }

    public String getDirection() {
        return null;
    }

    public double getDistance() {
        return 0;
    }

    public String getEnd() {
        return null;
    }

    public Geometry getGeometry() {
        return null;
    }

    public TransportationMode getMode() {
        return TransportationMode.BOARDING;
    }

    public String getName() {
        return "leave street network for transit network";
    }

    public String getStart() {
        return null;
    }

    private int computeWait(State state0, TraverseOptions wo) {
        long currentTime = state0.getTime();
        Date serviceDate = getServiceDate(currentTime, wo.calendar);
        Date serviceDateYesterday = getServiceDate(currentTime - MILLI_IN_DAY, wo.calendar);
        int secondsSinceMidnight = (int) ((currentTime - serviceDate.getTime()) / 1000);

        int wait = -1;

        AgencyAndId service = pattern.exemplar.getServiceId();
        if (wo.serviceOn(service, serviceDate)) {
            // try to get the departure time on today's schedule
            wait = pattern.getNextDepartureTime(stopIndex,secondsSinceMidnight) - secondsSinceMidnight;
        }
        if (wo.serviceOn(service, serviceDateYesterday)) {
            // now, try to get the departure time on yesterday's schedule -- assuming that
            // yesterday's is on the same schedule as today. If it's not, then we'll worry about it
            // when we get to the pattern(s) which do contain yesterday.
            int waitYesterday = pattern
                    .getNextDepartureTime(stopIndex,secondsSinceMidnight - SEC_IN_DAY)
                    - (secondsSinceMidnight - SEC_IN_DAY);
            if (waitYesterday != -1 && (wait < 0 || waitYesterday < wait)) {
                // choose the better time
                wait = waitYesterday;
            }
        }

        if (wait < 0) {
            return -1;
        }
        return wait;
    }

    public TraverseResult traverse(State state0, TraverseOptions wo) {
        int wait = computeWait(state0, wo);
        if (wait < 0) {
            return null;
        }
        State state1 = state0.clone();
        state1.incrementTimeInSeconds(wait);
        return new TraverseResult(wait, state1);
    }

    public TraverseResult traverseBack(State state0, TraverseOptions wo) {

        int wait = computeWait(state0, wo);
        if (wait < 0) {
            return null;
        }
        State state1 = state0.clone();
        state1.incrementTimeInSeconds(-wait);
        return new TraverseResult(wait, state1);
    }

    private Date getServiceDate(long currentTime, Calendar c) {
        c.setTimeInMillis(currentTime);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
