package org.opentripplanner.jags.test;

import junit.framework.TestCase;

import org.opentripplanner.jags.core.Edge;
import org.opentripplanner.jags.core.Graph;
import org.opentripplanner.jags.core.Vertex;
import org.opentripplanner.jags.edgetype.Street;

public class TestGraph extends TestCase {
    public void testBasic() throws Exception {
        Graph gg = new Graph();
        assertNotNull(gg);
    }

    public void testAddVertex() throws Exception {
        Graph gg = new Graph();
        Vertex a = gg.addVertex("A", 5, 5);
        assertEquals(a.label, "A");
    }

    public void testGetVertex() throws Exception {
        Graph gg = new Graph();
        Vertex a = gg.addVertex("A", 5, 5);
        Vertex b = gg.getVertex("A");
        assertEquals(a, b);
    }

    public void testAddEdge() throws Exception {
        Graph gg = new Graph();
        gg.addVertex("A", 5, 5);
        gg.addVertex("B", 6, 6);
        Edge ee = gg.addEdge("A", "B", new Street(1));
        assertNotNull(ee);
    }
}
