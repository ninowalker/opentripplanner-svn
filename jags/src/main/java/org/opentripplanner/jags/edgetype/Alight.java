package org.opentripplanner.jags.edgetype;

import org.opentripplanner.jags.core.State;
import org.opentripplanner.jags.core.TransportationMode;
import org.opentripplanner.jags.core.TraverseOptions;
import org.opentripplanner.jags.core.TraverseResult;

import com.vividsolutions.jts.geom.Geometry;

public class Alight extends AbstractPayload {

    String start_id; // a street vertex's id

    String end_id; // a transit node's GTFS id

    private static final long serialVersionUID = 1L;

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
        // TODO Auto-generated method stub -- need to provide link between
        // location of street node and location of transit node.
        return null;
    }

    public TransportationMode getMode() {
        return TransportationMode.ALIGHTING;
    }

    public String getName() {
        // TODO Auto-generated method stub -- need to say something like,
        // "Exit 7th Avenue Station"
        return "leave transit network for street network";
    }

    public String getStart() {
        return null;
    }

    public TraverseResult traverse(State s0, TraverseOptions wo) {
        State s1 = s0.clone();
        return new TraverseResult(1, s1);
    }

    public TraverseResult traverseBack(State s0, TraverseOptions wo) {
        State s1 = s0.clone();
        return new TraverseResult(1, s1);
    }

}
