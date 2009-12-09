/* This program is free software: you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public License
   as published by the Free Software Foundation, either version 3 of
   the License, or (at your option) any later version.
   
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   
   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

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
package org.opentripplanner.api.ws;

import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jettison.json.JSONException;
import org.opentripplanner.api.model.Fare;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.Place;
import org.opentripplanner.api.model.RelativeDirection;
import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.model.WalkStep;
import org.opentripplanner.api.ws.RequestInf.OptimizeType;
import org.opentripplanner.routing.services.PathService;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.spt.SPTEdge;
import org.opentripplanner.routing.spt.SPTVertex;
import org.opentripplanner.routing.core.Edge;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.edgetype.Street;
import org.opentripplanner.util.PolylineEncoder;
import org.springframework.beans.factory.annotation.Autowired;

import com.sun.jersey.api.spring.Autowire;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;

/**
 *
 */
// NOTE - /ws/plan is the full path -- see web.xml
@Path("/plan")
@XmlRootElement
@Autowire
public class Planner {

    private PathService pathservice;

    @Autowired
    public void setPathService(PathService pathService) {
        this.pathservice = pathService;
    }

    @GET
    @Produces( { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public Response getItineraries(
            @QueryParam(RequestInf.FROM) String fromPlace,
            @QueryParam(RequestInf.TO) String toPlace,
            @QueryParam(RequestInf.DATE) String date,
            @QueryParam(RequestInf.TIME) String time,
            @QueryParam(RequestInf.ARRIVE_BY) Boolean arriveBy,
            @QueryParam(RequestInf.WALK) Double walk,
            @QueryParam(RequestInf.OPTIMIZE) List<OptimizeType> optList,
            @QueryParam(RequestInf.MODE) TraverseModeSet modes,
            @QueryParam(RequestInf.NUMBER_ITINERARIES) Integer max,
            @DefaultValue(MediaType.APPLICATION_JSON) @QueryParam(RequestInf.OUTPUT_FORMAT) String of)
            throws JSONException {

        /* create request */
        Request request = new Request();
        request.setFrom(fromPlace);
        request.setTo(toPlace);
        request.setDateTime(date, time);

        if (max != null)
            request.setNumItineraries(max);
        if (walk != null)
            request.setWalk(walk);
        if (arriveBy != null && arriveBy)
            request.setArriveBy(true);

        if (optList != null && optList.size() > 0)
            request.addOptimize(optList);

        request.setModes(modes);
        request.setOutputFormat(MediaType.valueOf(of));

        /* use request to generate trip */

        TripPlan plan = generatePlan(request);
        Response response = new Response(request, plan);
        return response;
    }

    /**
     * Generates a TripPlan from a Request;
     * 
     * @param request
     * @return
     */

    private TripPlan generatePlan(Request request) {
        TraverseModeSet modeSet = request.getModeSet();
        assert (modeSet.isValid());
        TraverseOptions options = new TraverseOptions(modeSet);
        options.back = request.isArriveBy();
        List<GraphPath> paths = pathservice.plan(request.getFrom(), request.getTo(), request
                .getDateTime(), options);

        Vector<SPTVertex> vertices = paths.get(0).vertices;
        SPTVertex tripStartVertex = vertices.firstElement();
        SPTVertex tripEndVertex = vertices.lastElement();
        Place from = new Place(tripStartVertex.getX(), tripStartVertex.getY(), request.getFrom());
        Place to = new Place(tripEndVertex.getX(), tripEndVertex.getY(), request.getTo());

        TripPlan plan = new TripPlan(from, to, request.getDateTime());

        for (GraphPath path : paths) {

            Itinerary itinerary = new Itinerary();
            plan.addItinerary(itinerary);

            SPTVertex startVertex = path.vertices.firstElement();
            State startState = startVertex.state;
            SPTVertex endVertex = path.vertices.lastElement();
            State endState = endVertex.state;

            itinerary.startTime = new Date(startState.getTime());
            itinerary.endTime = new Date(endState.getTime());
            itinerary.duration = endState.getTime() - startState.getTime();
            itinerary.fare = new Fare();
            itinerary.fare.addFare(Fare.FareType.regular, Currency.getInstance("USD"), 225);

            Leg leg = null;
            TraverseMode mode = null;
            Geometry geometry = null;
            String name = null;

            int startWalk = -1;
            int i = -1;
            for (SPTEdge edge : path.edges) {
                i++;
                Edge graphEdge = edge.payload;

                TraverseMode edgeMode = graphEdge.getMode();
                double edgeTime = edge.tov.state.getTime() - edge.fromv.state.getTime();

                if (!edgeMode.isTransit() && edgeMode != TraverseMode.ALIGHTING) {
                    if (edgeMode != mode
                            || (!mode.isOnStreetNonTransit() && graphEdge.getName() != name)) {
                        name = graphEdge.getName();
                        if (leg != null) {
                            /* finalize leg */
                            if (startWalk != -1) {
                                leg.walkSteps = getWalkSteps(path.edges.subList(startWalk, i));
                            }
                            Vertex fromv = graphEdge.getFromVertex();
                            Coordinate endCoord = fromv.getCoordinate();
                            leg.to = new Place(endCoord.x, endCoord.y, fromv.getName());
                            leg.endTime = new Date(edge.tov.state.getTime());
                            leg.legGeometry = PolylineEncoder.createEncodings(geometry);
                            leg.duration = edge.tov.state.getTime() - leg.startTime.getTime();
                            leg = null;
                        }

                        leg = new Leg();
                        itinerary.addLeg(leg);

                        leg.startTime = new Date(edge.fromv.state.getTime());
                        leg.route = graphEdge.getName();
                        mode = graphEdge.getMode();
                        leg.mode = mode.toString();
                        if (mode == TraverseMode.WALK) {
                            startWalk = i;
                        } else {
                            startWalk = -1;
                        }
                        leg.distance = edge.getDistance();
                        Vertex fromv = graphEdge.getFromVertex();
                        Coordinate endCoord = fromv.getCoordinate();
                        leg.from = new Place(endCoord.x, endCoord.y, fromv.getName());
                    }
                }
                Geometry edgeGeometry = graphEdge.getGeometry();
                if (geometry == null) {
                    geometry = edgeGeometry;
                } else {
                    if (edgeGeometry != null) {
                        geometry = geometry.union(edgeGeometry);
                    }
                }

                if (edgeMode == TraverseMode.TRANSFER) {

                    itinerary.transfers++;
                    itinerary.walkTime += edgeTime;
                    itinerary.walkDistance += graphEdge.getDistance();
                    continue;
                }

                if (edgeMode == TraverseMode.BOARDING) {
                    itinerary.waitingTime += edgeTime;
                    continue;
                }

                if (edgeMode == TraverseMode.WALK) {
                    itinerary.walkTime += edgeTime;
                    itinerary.walkDistance += graphEdge.getDistance();
                }

                if (edgeMode.isTransit()) {
                    itinerary.transitTime += edgeTime;
                    mode = graphEdge.getMode();
                    leg.mode = mode.toString();
                    leg.route = graphEdge.getName();
                }
            }

            SPTEdge edge = path.edges.lastElement();
            Edge graphEdge = edge.payload;

            if (leg != null) {
                /* finalize leg */
                Vertex tov = graphEdge.getToVertex();
                Coordinate endCoord = tov.getCoordinate();
                leg.to = new Place(endCoord.x, endCoord.y, tov.getName());
                leg.endTime = new Date(edge.tov.state.getTime());
                leg.legGeometry = PolylineEncoder.createEncodings(geometry);
                leg.duration = edge.tov.state.getTime() - leg.startTime.getTime();
                if (startWalk != -1) {
                    leg.walkSteps = getWalkSteps(path.edges.subList(startWalk, i + 1));
                }
                leg = null;
            }

        }
        return plan;
    }

    /**
     * Converts a list of street edges to a list of turn-by-turn directions.
     * 
     * @param edges
     *            : A list of street edges
     * @return
     */
    private List<WalkStep> getWalkSteps(List<SPTEdge> edges) {

        List<WalkStep> steps = new ArrayList<WalkStep>();

        WalkStep step = null;

        double lastAngle = 0;

        for (SPTEdge sptEdge : edges) {
            Edge edge = sptEdge.payload;
            String streetName = edge.getName();
            if (step == null) {
                step = new WalkStep();
                steps.add(step);
                step.absoluteDirection = edge.getDirection();
                step.streetName = streetName;
            } else if (!step.streetName.equals(streetName)) {
                step = new WalkStep();
                steps.add(step);
                step.absoluteDirection = edge.getDirection();
                step.streetName = streetName;
                double thisAngle = getFirstAngle(edge.getGeometry());
                step.setRelativeDirection(lastAngle, thisAngle);
            } else {
                /* generate turn-to-stay-on directions, where needed */
                double thisAngle = getFirstAngle(edge.getGeometry());
                RelativeDirection direction = WalkStep.getRelativeDirection(lastAngle, thisAngle);
                if (direction != RelativeDirection.CONTINUE) {
                    // figure out if there was another way we could have turned
                    Vertex start = edge.getFromVertex();
                    boolean foundAlternatePaths = false;
                    for (Edge out : start.getOutgoing()) {
                        if (out == edge) {
                            continue;
                        }
                        if (!(out instanceof Street)) {
                            continue;
                        }
                        // there were paths we didn't take.
                        foundAlternatePaths = true;
                        break;
                    }
                    if (foundAlternatePaths) {
                        step = new WalkStep();
                        steps.add(step);
                        step.absoluteDirection = edge.getDirection();
                        step.streetName = streetName;
                        step.relativeDirection = direction;
                        step.stayOn = true;
                    }
                }
            }

            step.distance += edge.getDistance();

            lastAngle = getLastAngle(edge.getGeometry());
        }
        return steps;
    }

    /**
     * Computes the angle of the last segment of a LineString or MultiLineString
     * 
     * @param geometry
     *            a LineString or a MultiLineString
     * @return
     */
    private double getLastAngle(Geometry geometry) {
        LineString line;
        if (geometry instanceof MultiLineString) {
            line = (LineString) geometry.getGeometryN(geometry.getNumGeometries() - 1);
        } else {
            assert geometry instanceof LineString;
            line = (LineString) geometry;
        }
        int numPoints = line.getNumPoints();
        Coordinate coord0 = line.getCoordinateN(numPoints - 2);
        Coordinate coord1 = line.getCoordinateN(numPoints - 1);
        return Math.atan2(coord1.y - coord0.y, coord1.x - coord0.x);
    }

    /**
     * Computes the angle of the first segment of a LineString or MultiLineString
     * 
     * @param geometry
     *            a LineString or a MultiLineString
     * @return
     */
    private double getFirstAngle(Geometry geometry) {
        LineString line;
        if (geometry instanceof MultiLineString) {
            line = (LineString) geometry.getGeometryN(0);
        } else {
            assert geometry instanceof LineString;
            line = (LineString) geometry;
        }

        Coordinate coord0 = line.getCoordinateN(0);
        Coordinate coord1 = line.getCoordinateN(1);
        return Math.atan2(coord1.y - coord0.y, coord1.x - coord0.x);
    }
}
