package org.opentripplanner.jags.test;

import junit.framework.TestCase;

import org.opentripplanner.jags.algorithm.kao.Kao;
import org.opentripplanner.jags.algorithm.kao.KaoGraph;
import org.opentripplanner.jags.algorithm.kao.Tree;
import org.opentripplanner.jags.core.Edge;
import org.opentripplanner.jags.core.Graph;
import org.opentripplanner.jags.core.State;
import org.opentripplanner.jags.core.Vertex;
import org.opentripplanner.jags.core.WalkOptions;
import org.opentripplanner.jags.edgetype.Hop;
import org.opentripplanner.jags.edgetype.loader.GTFSHopLoader;
import org.opentripplanner.jags.gtfs.GtfsContext;
import org.opentripplanner.jags.gtfs.GtfsLibrary;
import org.opentripplanner.jags.spt.ShortestPathTree;

import java.io.File;
import java.util.ArrayList;
import java.util.GregorianCalendar;

public class TestKao extends TestCase {


  public void testBasic() throws Exception {

    GtfsContext context = GtfsLibrary.readGtfs(new File(ConstantsForTests.CALTRAIN_GTFS));

    GregorianCalendar t_0 = new GregorianCalendar(2009, 8, 7, 12, 0, 0);

    KaoGraph kg = new KaoGraph();
    kg.setGtfsContext(context);
    GTFSHopLoader hl = new GTFSHopLoader(kg, context);
    hl.load();

    long delta = 1000000000;
    Vertex mlb = kg.getVertex("Caltrain_Millbrae Caltrain");
    Vertex mtv = kg.getVertex("Caltrain_Mountain View Caltrain");

    Tree tree = Kao.find(kg, t_0, mlb, delta);
    ArrayList<Edge> path = tree.path(mtv);

    assertTrue(((Hop) path.get(path.size() - 1).payload).getEndStopTime().getArrivalTime() == 48540);

    WalkOptions options = new WalkOptions();
    options.setGtfsContext(context);

    Graph gg = new Graph();
    GTFSHopLoader h2 = new GTFSHopLoader(gg, context);
    h2.load();
    ShortestPathTree spt = org.opentripplanner.jags.algorithm.Dijkstra.getShortestPathTree(
        gg, "Caltrain_Millbrae Caltrain", "Caltrain_Mountain View Caltrain",
        new State(t_0), options);

    Vertex vertex = gg.getVertex("Caltrain_Mountain View Caltrain");
    Hop hop = (Hop) spt.getPath(vertex).vertices.lastElement().incoming.payload;
    assertTrue(hop.getEndStopTime().getArrivalTime() == 48540);
  }
}
