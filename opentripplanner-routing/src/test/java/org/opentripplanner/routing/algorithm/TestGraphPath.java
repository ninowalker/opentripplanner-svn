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

package org.opentripplanner.routing.algorithm;

import java.io.File;
import java.util.GregorianCalendar;

import org.opentripplanner.ConstantsForTests;
import org.opentripplanner.gtfs.GtfsContext;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.core.Graph;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.edgetype.Alight;
import org.opentripplanner.routing.edgetype.FreeEdge;
import org.opentripplanner.routing.edgetype.PatternAlight;
import org.opentripplanner.routing.edgetype.TransferEdge;
import org.opentripplanner.routing.edgetype.factory.GTFSPatternHopFactory;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.spt.SPTEdge;
import org.opentripplanner.routing.spt.ShortestPathTree;

import junit.framework.TestCase;

public class TestGraphPath extends TestCase {
    private Graph graph;

    private GtfsContext context;

    public void setUp() throws Exception {

        context = GtfsLibrary.readGtfs(new File(ConstantsForTests.FAKE_GTFS));
        graph = new Graph();

        GTFSPatternHopFactory hl = new GTFSPatternHopFactory(context);
        hl.run(graph);

    }

    public void testGraphPathOptimize() throws Exception {

        Vertex stop_a = graph.getVertex("agency_A_depart");
        Vertex stop_e = graph.getVertex("agency_E_arrive");

        TraverseOptions options = new TraverseOptions();
        options.setGtfsContext(context);

        ShortestPathTree spt;
        GraphPath path;

        spt = AStar.getShortestPathTree(graph, stop_a.getLabel(), stop_e.getLabel(), new State(
                new GregorianCalendar(2009, 8, 7, 0, 0, 0).getTimeInMillis()), options);

        path = spt.getPath(stop_e, false); /* do not optimize yet, since we are testing optimization */
        assertNotNull(path);
        assertTrue(path.vertices.size() == 12);

        long bestStart = new GregorianCalendar(2009, 8, 7, 0, 20, 0).getTimeInMillis();
        assertNotSame(bestStart, path.vertices.firstElement().state.getTime());
        path.optimize();
        assertEquals(bestStart, path.vertices.firstElement().state.getTime());
    }

    public void testNoConsecutiveTransfers() throws Exception {
        // You should never make a transfer if you have just made one. Put another way, you can only
        // transfer if you have just alighted, or, if traversing backwards, you can only transfer if
        // you have just boarded. 
        Vertex stop_n = graph.getVertex("agency_N_arrive");
        Vertex stop_l = graph.getVertex("agency_L_depart");

        TraverseOptions options = new TraverseOptions();
        options.setGtfsContext(context);

        ShortestPathTree spt;
        GraphPath path;

        // Test depart at trip
        State s0 = new State(new GregorianCalendar(2009, 8, 7, 15, 0, 0).getTimeInMillis());
        spt = AStar.getShortestPathTree(graph, stop_n, stop_l, s0, options);

        path = spt.getPath(stop_l);

        assertNotNull(path);
        boolean transferAllowed = true;
        for (SPTEdge e : path.edges) {
            if (!transferAllowed) {
                assertFalse(e.payload instanceof TransferEdge);
            }
            if (e.payload instanceof Alight || e.payload instanceof PatternAlight || e.payload instanceof FreeEdge) {
                transferAllowed = true; // Can only transfer if we've just alighted
            } else {
                transferAllowed = false;
            }
        }

        // Test arrive by trip
        options.setArriveBy(true);
        State s1 = new State(new GregorianCalendar(2009, 8, 7, 12, 0, 0).getTimeInMillis());
        spt = AStar.getShortestPathTreeBack(graph, stop_n.getLabel(), stop_l.getLabel(), s1,
                options);

        path = spt.getPath(stop_n);
        path.reverse();

        assertNotNull(path);
        transferAllowed = true;
        for (SPTEdge e : path.edges) {
            if (!transferAllowed) {
                assertFalse(e.payload instanceof TransferEdge);
            }
            if (e.payload instanceof Alight || e.payload instanceof PatternAlight || e.payload instanceof FreeEdge) {
                transferAllowed = true;
            } else {
                transferAllowed = false;
            }
        }
    }
}
