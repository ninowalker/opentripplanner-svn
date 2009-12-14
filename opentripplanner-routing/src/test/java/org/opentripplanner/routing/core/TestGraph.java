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

package org.opentripplanner.routing.core;

import junit.framework.TestCase;

import org.opentripplanner.routing.core.Edge;
import org.opentripplanner.routing.core.Graph;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.edgetype.Street;

public class TestGraph extends TestCase {
    public void testBasic() throws Exception {
        Graph gg = new Graph();
        assertNotNull(gg);
    }

    public void testAddVertex() throws Exception {
        Graph gg = new Graph();
        Vertex a = gg.addVertex("A", 5, 5);
        assertEquals(a.getLabel(), "A");
    }

    public void testGetVertex() throws Exception {
        Graph gg = new Graph();
        Vertex a = gg.addVertex("A", 5, 5);
        Vertex b = gg.getVertex("A");
        assertEquals(a, b);
    }

    public void testAddEdge() throws Exception {
        Graph gg = new Graph();
        Vertex a = gg.addVertex("A", 5, 5);
        Vertex b = gg.addVertex("B", 6, 6);
        Edge ee = new Street(a,b, 1);
        gg.addEdge(ee);
        assertNotNull(ee);
    }
}
