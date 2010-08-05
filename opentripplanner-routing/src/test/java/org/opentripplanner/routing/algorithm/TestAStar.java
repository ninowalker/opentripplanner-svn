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

import junit.framework.TestCase;

import org.opentripplanner.ConstantsForTests;
import org.opentripplanner.gtfs.GtfsContext;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.algorithm.AStar;
import org.opentripplanner.routing.core.Graph;
import org.opentripplanner.routing.core.RouteSpec;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.edgetype.PatternBoard;
import org.opentripplanner.routing.edgetype.factory.GTFSPatternHopFactory;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.spt.SPTEdge;
import org.opentripplanner.routing.spt.SPTVertex;
import org.opentripplanner.routing.spt.ShortestPathTree;

public class TestAStar extends TestCase {
    public void testBasic() throws Exception {

        GtfsContext context = GtfsLibrary.readGtfs(new File(ConstantsForTests.CALTRAIN_GTFS));

        TraverseOptions options = new TraverseOptions();
        options.setGtfsContext(context);

        Graph gg = new Graph();
        GTFSPatternHopFactory factory = new GTFSPatternHopFactory(context);
        factory.run(gg);

        ShortestPathTree spt;
        GraphPath path = null;
        long startTime = new GregorianCalendar(2009, 8, 7, 12, 0, 0).getTimeInMillis();
        spt = AStar.getShortestPathTree(gg, "Caltrain_Millbrae Caltrain",
                "Caltrain_Mountain View Caltrain", new State(startTime), options);

        path = spt.getPath(gg.getVertex("Caltrain_Mountain View Caltrain"));

        long endTime = new GregorianCalendar(2009, 8, 7, 13, 29).getTimeInMillis();

        assertEquals(path.vertices.lastElement().state.getTime(), endTime);

        /* test backwards traversal */
        options.setArriveBy(true);
        spt = AStar.getShortestPathTreeBack(gg, "Caltrain_Millbrae Caltrain",
                "Caltrain_Mountain View Caltrain", new State(endTime), options);

        path = spt.getPath(gg.getVertex("Caltrain_Millbrae Caltrain"));

        long expectedStartTime = new GregorianCalendar(2009, 8, 7, 12, 39).getTimeInMillis();

        path.reverse();

        SPTVertex start = path.vertices.firstElement();
        assertEquals(start.state.getTime(), expectedStartTime);

        assertEquals(start, path.edges.firstElement().getFromVertex());
        assertEquals(start.mirror, path.edges.firstElement().payload.getFromVertex());

    }

    public void testBannedRoutes() {

        Graph graph;
        GtfsContext context;

        graph = ConstantsForTests.getInstance().getPortlandGraph();
        context = ConstantsForTests.getInstance().getPortlandContext();

        Vertex start = graph.getVertex("TriMet_8371");
        TraverseOptions options = new TraverseOptions();
        options.setGtfsContext(context);
        GregorianCalendar startTime = new GregorianCalendar(2009, 11, 1, 12, 34, 25);
        ShortestPathTree spt = null;

        Vertex end = graph.getVertex("TriMet_8374");

        /*
         * The MAX Red, Blue, and Green lines all run along the same trackage between the stops 8374
         * and 8371. Together, they form the white line. No, wait, that's light.  They make
         * a pretty good test case for banned routes, since if one is banned, you can always take 
         * another.
         */
        String[] maxLines = { "MAX Red Line", "MAX Blue Line", "MAX Green Line" };
        for (int i = 0; i < maxLines.length; ++i) {
            String line = maxLines[i];
            options.bannedRoutes.add(new RouteSpec("TriMet", line));
            spt = AStar.getShortestPathTree(graph, start, end, new State(startTime
                    .getTimeInMillis()), options);
            GraphPath path = spt.getPath(end);
            for (SPTEdge e : path.edges) {
                if (e.payload instanceof PatternBoard) {
                    assertFalse(e.getName().equals(line));
                    boolean foundMaxLine = false;
                    for (int j = 0; j < maxLines.length; ++j) {
                        if (j != i) {
                            if (e.getName().equals(maxLines[j])) {
                                foundMaxLine = true;
                            }
                        }
                    }
                    assertTrue(foundMaxLine);
                }
            }
            options.bannedRoutes.clear();
        }
    }

    public void testMaxTime() {

        Graph graph;
        GtfsContext context;

        graph = ConstantsForTests.getInstance().getPortlandGraph();
        context = ConstantsForTests.getInstance().getPortlandContext();

        Vertex start = graph.getVertex("TriMet_8371");
        TraverseOptions options = new TraverseOptions();
        options.setGtfsContext(context);
        GregorianCalendar startTime = new GregorianCalendar(2009, 11, 1, 12, 34, 25);
        options.worstTime = startTime.getTimeInMillis() + 1000 * 60 * 60; //one hour is way too much time

        Vertex end = graph.getVertex("TriMet_8374");
        ShortestPathTree spt = AStar.getShortestPathTree(graph, start, end, new State(startTime.getTimeInMillis()),
                options);
        GraphPath path = spt.getPath(end);
        assertNotNull(path);
        
        options.worstTime = startTime.getTimeInMillis() + 1000 * 60; //but one minute is not enough

        spt = AStar.getShortestPathTree(graph, start, end, new State(startTime.getTimeInMillis()),
                options);
        path = spt.getPath(end);
        assertNull(path);        
    }

    public void testPerformance() throws Exception {

        Graph graph;
        GtfsContext context;

        graph = ConstantsForTests.getInstance().getPortlandGraph();
        context = ConstantsForTests.getInstance().getPortlandContext();
        GregorianCalendar startTime = new GregorianCalendar(2009, 11, 1, 12, 34, 25);
        TraverseOptions options = new TraverseOptions();
        options.setGtfsContext(context);

        Vertex airport = graph.getVertex("TriMet_10579");

        long startClock, endClock;

        final int n_trials = 100;
        String random[] = new String[n_trials];
        for (int i = 0; i < n_trials; ++i) {
            String label;
            while (true) {
                int rand_id = (int) (Math.random() * 10000);
                label = "TriMet_" + rand_id;
                if (graph.getVertex(label) != null) {
                    break;
                }
            }
            random[i] = label;
        }

        /* time A* */
        startClock = System.nanoTime();
        ShortestPathTree spt = null;
        for (int i = 0; i < n_trials; ++i) {
            spt = AStar.getShortestPathTree(graph, random[i], airport.getLabel(), new State(
                    startTime.getTimeInMillis()), options);
        }

        endClock = System.nanoTime();
        long aStarTime = endClock - startClock;

        GraphPath path = spt.getPath(airport);
        assertNotNull(path);
        double time = aStarTime / n_trials / 1000000000.0;
        assertTrue("Actual time " + time + "s greater than 500 ms", time <= 0.5);

    }
}
