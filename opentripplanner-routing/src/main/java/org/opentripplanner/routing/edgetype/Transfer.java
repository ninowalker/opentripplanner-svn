/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.edgetype;

import org.opentripplanner.routing.core.AbstractEdge;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TransitStop;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.TraverseResult;
import org.opentripplanner.routing.core.Vertex;

import com.vividsolutions.jts.geom.Geometry;

/**
 * A transfer directly between two stops without using the street network.
 *
 */
public class Transfer extends AbstractEdge {

    private static final long serialVersionUID = 1L;
    
    int time = 0;
    
    double distance;

    private Geometry geometry = null;

    private boolean wheelchairAccessible;


    /**
     * @see Transfer(Vertex, Vertex, double, int)
     */
    public Transfer(TransitStop fromv, TransitStop tov, double distance) {
        super(fromv, tov);
        this.distance = distance;
        this.time = (int) distance; //(int) distance * 3;
    }
    
    /**
     * Creates a new Transfer edge.
     * @param fromv     the Vertex where the transfer originates
     * @param tov       the Vertex where the transfer ends
     * @param distance  the distance in meters from the origin Vertex to the destination
     * @param time      the minimum time in seconds it takes to complete this transfer
     */
    public Transfer(Vertex fromv, Vertex tov, double distance, int time) {
        super(fromv, tov);
        this.distance = distance;
        this.time = time;
        /* Assume that if either stop is not accessible from the street, the
         * transfer is not accessible.  This will need to be changed when GTFS
         * and OneBusAway GTFS support accessible transfers.
         */
        wheelchairAccessible = ((TransitStop)fromv).hasWheelchairEntrance() &&
            ((TransitStop)tov).hasWheelchairEntrance();
    }

    public String getDirection() {
        // TODO Auto-generated method stub
        return null;
    }

    public double getDistance() {
        return distance;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public TraverseMode getMode() {
        return TraverseMode.TRANSFER;
    }

    public String getName() {
        // TODO Auto-generated method stub
        return "transfer";
    }

    public TraverseResult traverse(State s0, TraverseOptions wo) {
        if (s0.justTransferred) {
            return null;
        }
        if (wo.wheelchairAccessible && !wheelchairAccessible) {
            return null;
        }
        State s1 = s0.clone();
        s1.incrementTimeInSeconds(time);
        s1.justTransferred = true;
        return new TraverseResult(time, s1);
    }

    public TraverseResult traverseBack(State s0, TraverseOptions wo) {
        if (s0.justTransferred) {
            return null;
        }
        if (wo.wheelchairAccessible && !wheelchairAccessible) {
            return null;
        }
        State s1 = s0.clone();
        s1.incrementTimeInSeconds(-time);
        s1.justTransferred = true;
        return new TraverseResult(time, s1);
    }
    
    public boolean equals(Object o) {
        if (!(o instanceof Transfer)) {
            return false;
        }
        Transfer t = (Transfer) o;
        return t.getToVertex().equals(getToVertex()) && t.getFromVertex().equals(getFromVertex());
    }
    
    public int hashCode() {
        return tov.hashCode() ^ fromv.hashCode();
    }

    public void setGeometry(Geometry geometry) {
        this.geometry  = geometry;
    }

}
