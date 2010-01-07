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

package org.opentripplanner.routing.location;

import java.util.ArrayList;

import org.opentripplanner.routing.core.Edge;
import org.opentripplanner.routing.core.GenericVertex;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.edgetype.Street;
import org.opentripplanner.routing.impl.DistanceLibrary;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.PrecisionModel;

/**
 * Represents a location on a street, somewhere between the two corners.
 * This is used when computing the first and last segments of a trip, for 
 * trips that start or end between two intersections.
 */
public class StreetLocation extends GenericVertex {
    
    private static final long serialVersionUID = 1L;

    public Edge street;

    public double location; /* a number from 0 to 1 representing how far along the street the
                               location is; 0 means the start vertex and 1 means the end vertex */

    ArrayList<Edge> incoming = new ArrayList<Edge>();

    ArrayList<Edge> outgoing = new ArrayList<Edge>();

    /**
     * Creates a StreetLocation on the given street.  How far along is
     * controlled by the location parameter, which represents a distance 
     * along the edge between 0 (the from vertex) and 1 (the to vertex).
     *   
     * @param name
     * @param street
     * @param location
     * @param incoming true if the StartLocation is a target vertex, false 
     *                 if it is an origin vertex
     * @return the new StreetLocation
     */
    public static StreetLocation createStreetLocation(String name, Edge street, double location) {

        Vertex fromv = street.getFromVertex();
        Vertex tov = street.getToVertex();
        Coordinate startCoord = fromv.getCoordinate();
        Coordinate endCoord = tov.getCoordinate();
        double x = startCoord.x * (1 - location) + endCoord.x * location;
        double y = startCoord.y * (1 - location) + endCoord.y * location;

        return new StreetLocation(name, street, location, x, y);
    }

    private StreetLocation(String name, Edge street, double location, double x,
            double y) {
        super(name, x, y);

        assert 0 <= location;
        assert location <= 1;

        Vertex fromv = street.getFromVertex();
        Vertex tov = street.getToVertex();
        Coordinate startCoord = fromv.getCoordinate();
        Coordinate endCoord = tov.getCoordinate();
        this.street = street;
        this.location = location;

        String streetName = street.getName();

        double weight1 = DistanceLibrary.distance(y, x, startCoord.y, startCoord.x);
        double weight2 = DistanceLibrary.distance(y, x, endCoord.y, endCoord.x);

        Street e1 = new Street(fromv, this, streetName, streetName, weight1);
        e1.setGeometry(toLineString(fromv.getCoordinate(), this.getCoordinate()));
        addIncoming(e1);

        Street e2 = new Street(tov, this, streetName, streetName, weight2);
        e2.setGeometry(toLineString(tov.getCoordinate(), this.getCoordinate()));
        addIncoming(e2);

        Street e3 = new Street(this, fromv, streetName, streetName, weight1);
        addOutgoing(e3);
        e3.setGeometry(toLineString(this.getCoordinate(), fromv.getCoordinate()));

        Street e4 = new Street(this, tov, streetName, streetName, weight2);
        addOutgoing(e4);
        e4.setGeometry(toLineString(this.getCoordinate(), tov.getCoordinate()));
    }

    private LineString toLineString(Coordinate start, Coordinate end) {
        Coordinate[] coords = { start, end };
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING),
                4326);
        return factory.createLineString(coords);
    }
}
