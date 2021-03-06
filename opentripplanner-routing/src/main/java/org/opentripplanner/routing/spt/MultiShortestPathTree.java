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

package org.opentripplanner.routing.spt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.Vertex;

public class MultiShortestPathTree implements ShortestPathTree {
    private static final long serialVersionUID = -3899613853043676031L;

    HashMap<Vertex, Collection<SPTVertex>> vertexSets;

    public MultiShortestPathTree() {
        vertexSets = new HashMap<Vertex, Collection<SPTVertex>>();
    }

    public Collection<SPTVertex> getVertices() {
        throw new UnsupportedOperationException();
    }
    
    public SPTVertex addVertex(Vertex vertex, State state, double weightSum, TraverseOptions options) {

        Collection<SPTVertex> vertices = vertexSets.get(vertex);
        if (vertices == null) {
            vertices = new ArrayList<SPTVertex>();
            vertexSets.put(vertex, vertices);
            SPTVertex ret = new SPTVertex(vertex, state, weightSum, options);
            vertices.add(ret);
            return ret;
        }

        long time = state.getTime();

        Iterator<SPTVertex> it = vertices.iterator();

        if (options.isArriveBy()) {
            while (it.hasNext()) {
                SPTVertex v = it.next();
                double old_w = v.weightSum;
                long old_t = v.state.getTime();

                if (old_w <= weightSum && old_t >= time) {
                    /* an existing vertex strictly dominates the new vertex */
                    return null;
                }

                if (old_w > weightSum && old_t < time) {
                    /* the new vertex strictly dominates an existing vertex */
                    it.remove();
                }
            }

        } else {
            while (it.hasNext()) {
                SPTVertex v = it.next();
                double old_w = v.weightSum;
                long old_t = v.state.getTime();

                if (old_w <= weightSum && old_t <= time) {
                    /* an existing vertex strictly dominates the new vertex */
                    return null;
                }

                if (old_w > weightSum && old_t > time) {
                    /* the new vertex strictly dominates an existing vertex */
                    it.remove();
                } 
            }
        }
        SPTVertex ret = new SPTVertex(vertex, state, weightSum, options);
        vertices.add(ret);
        return ret;
    }

    public GraphPath getPath(Vertex dest) {
        return getPath(dest, true);
    }

    public GraphPath getPath(Vertex dest, boolean optimize) {
        SPTVertex end = null;
        Collection<SPTVertex> set = vertexSets.get(dest);
        if (set == null) {
            return null;
        }
        for (SPTVertex v : set) {
            if (end == null || v.weightSum < end.weightSum) {
                end = v;
            }
        }

        GraphPath ret = new GraphPath();
        while (true) {
            ret.vertices.add(0, end);
            if (end.incoming == null) {
                break;
            }
            ret.edges.add(0, end.incoming);
            end = end.incoming.fromv;
        }
        if (optimize) {
            ret.optimize();
        }
        return ret;
    }

    public String toString() {
        return "SPT " + this.vertexSets.size();
    }

    public void removeVertex(SPTVertex vertex) {
        Collection<SPTVertex> set = this.vertexSets.get(vertex.mirror);
        set.remove(vertex);
    }

}