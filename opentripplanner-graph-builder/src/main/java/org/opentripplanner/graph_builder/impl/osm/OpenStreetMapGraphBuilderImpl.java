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

package org.opentripplanner.graph_builder.impl.osm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.opentripplanner.common.geometry.PackedCoordinateSequence;
import org.opentripplanner.common.geometry.PackedCoordinateSequence.Float;
import org.opentripplanner.common.model.P2;
import org.opentripplanner.graph_builder.model.osm.OSMWithTags;
import org.opentripplanner.graph_builder.model.osm.OSMNode;
import org.opentripplanner.graph_builder.model.osm.OSMRelation;
import org.opentripplanner.graph_builder.model.osm.OSMWay;
import org.opentripplanner.graph_builder.services.GraphBuilder;
import org.opentripplanner.graph_builder.services.StreetUtils;
import org.opentripplanner.graph_builder.services.osm.OpenStreetMapContentHandler;
import org.opentripplanner.graph_builder.services.osm.OpenStreetMapProvider;
import org.opentripplanner.routing.core.Graph;
import org.opentripplanner.routing.core.Intersection;
import org.opentripplanner.routing.core.IntersectionVertex;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.edgetype.Street;
import org.opentripplanner.routing.edgetype.StreetTraversalPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

public class OpenStreetMapGraphBuilderImpl implements GraphBuilder {

    private static Logger _log = LoggerFactory.getLogger(OpenStreetMapGraphBuilderImpl.class);

    private static final GeometryFactory _geometryFactory = new GeometryFactory();

    private List<OpenStreetMapProvider> _providers = new ArrayList<OpenStreetMapProvider>();

    private Map<Object, Object> _uniques = new HashMap<Object, Object>();

    private Map<String, KeyValuePermission> _tagPermissions = new LinkedHashMap<String, KeyValuePermission>();

    private class KeyValuePermission {
        public String key;
        public String value;
        public StreetTraversalPermission permission;

        public KeyValuePermission(String key, String value, StreetTraversalPermission permission) {
            this.key        = key;
            this.value      = value;
            this.permission = permission;
        }
    };

    public void setProvider(OpenStreetMapProvider provider) {
        _providers.add(provider);
    }

    public void setProviders(List<OpenStreetMapProvider> providers) {
        _providers.addAll(providers);
    }

    public void setDefaultAccessPermissions(LinkedHashMap<String, StreetTraversalPermission> mappy) {
        for(String tag : mappy.keySet()) {
            int ch_eq = tag.indexOf("=");

            if(ch_eq < 0){
                _tagPermissions.put(tag, new KeyValuePermission(null, null, mappy.get(tag)));
            } else {
                String key   = tag.substring(0, ch_eq),
                       value = tag.substring(ch_eq + 1);

                _tagPermissions.put(tag, new KeyValuePermission(key, value, mappy.get(tag)));
            }
        }
        if (!_tagPermissions.containsKey("__default__")) {
            _log.warn("No default permissions for osm tags...");
        }
    }

    @Override
    public void buildGraph(Graph graph) {
        Handler handler = new Handler();
        for (OpenStreetMapProvider provider : _providers) {
            _log.debug("gathering osm from provider: " + provider);
            provider.readOSM(handler);
        }
        _log.debug("building osm street graph");
        handler.buildGraph(graph);
    }

    @SuppressWarnings("unchecked")
    private <T> T unique(T value) {
        Object v = _uniques.get(value);
        if (v == null) {
            _uniques.put(value, value);
            v = value;
        }
        return (T) v;
    }

    private class Handler implements OpenStreetMapContentHandler {

        private Map<Integer, OSMNode> _nodes = new HashMap<Integer, OSMNode>();

        private Map<Integer, OSMWay> _ways = new HashMap<Integer, OSMWay>();

        public void buildGraph(Graph graph) {

            // We want to prune nodes that don't have any edges
            Set<Integer> nodesWithNeighbors = new HashSet<Integer>();

            for (OSMWay way : _ways.values()) {
                List<Integer> nodes = way.getNodeRefs();
                if (nodes.size() > 1)
                    nodesWithNeighbors.addAll(nodes);
            }

            // Remove all simple islands
            _nodes.keySet().retainAll(nodesWithNeighbors);

            pruneFloatingIslands();

            HashMap<Coordinate, Intersection> intersectionsByLocation = new HashMap<Coordinate, Intersection>();

            int wayIndex = 0;

            //figure out which nodes that are actually intersections
            Set<Integer> possibleIntersectionNodes = new HashSet<Integer>();
            Set<Integer> intersectionNodes = new HashSet<Integer>();
            for (OSMWay way : _ways.values()) {
                List<Integer> nodes = way.getNodeRefs();
                for (int node : nodes) {
                    if (possibleIntersectionNodes.contains(node)) {
                        intersectionNodes.add(node);
                    } else {
                        possibleIntersectionNodes.add(node);
                    }
                }
            }

            for (OSMWay way : _ways.values()) {

                if (wayIndex % 1000 == 0)
                    _log.debug("ways=" + wayIndex + "/" + _ways.size());
                wayIndex++;

                StreetTraversalPermission permissions = getPermissionsForEntity(way);
                if(permissions == StreetTraversalPermission.NONE)
                    continue;

                List<Integer> nodes = way.getNodeRefs();

                Intersection startIntersection = null, endIntersection = null;

                ArrayList<Coordinate> segmentCoordinates = new ArrayList<Coordinate>();
                GeometryFactory geometryFactory = new GeometryFactory();

                Integer startNode = null;
                OSMNode osmStartNode = null;
                for (int i = 0; i < nodes.size() - 1; i++) {
                    Integer endNode = nodes.get(i + 1);
                    if (osmStartNode == null) {
                        startNode = nodes.get(i);
                        osmStartNode = _nodes.get(startNode);
                    }
                    OSMNode osmEndNode = _nodes.get(endNode);

                    if (osmStartNode == null || osmEndNode == null)
                        continue;

                    LineString geometry;

                    /* skip vertices that are not intersections, except that we use them for geometry */
                    if (segmentCoordinates.size() == 0) {
                        segmentCoordinates.add(getCoordinate(osmStartNode));
                    }

                    if (intersectionNodes.contains(endNode) || i == nodes.size() - 2) {
                        segmentCoordinates.add(getCoordinate(osmEndNode));
                        geometry = geometryFactory.createLineString(segmentCoordinates.toArray(new Coordinate[0]));
                        segmentCoordinates.clear();
                    } else {
                        segmentCoordinates.add(getCoordinate(osmEndNode));
                        continue;
                    }

                    /* generate intersections */
                    if (startIntersection == null) {
                        startIntersection = intersectionsByLocation.get(getCoordinate(osmStartNode));
                        if (startIntersection == null) {
                            startIntersection = new Intersection(getVertexIdForNodeId(startNode), osmStartNode.getLon(), osmStartNode.getLat());
                            intersectionsByLocation.put(startIntersection.getCoordinate(), startIntersection);
                        }
                    } else {
                        startIntersection = endIntersection;
                    }

                    endIntersection = intersectionsByLocation.get(getCoordinate(osmEndNode));
                    if (endIntersection == null) {
                        endIntersection = new Intersection(getVertexIdForNodeId(endNode), osmEndNode.getLon(), osmEndNode.getLat());
                        intersectionsByLocation.put(endIntersection.getCoordinate(), endIntersection);
                    }

                    IntersectionVertex from = new IntersectionVertex(startIntersection, geometry, true);
                    graph.addVertex(from);
                    IntersectionVertex to = new IntersectionVertex(endIntersection, geometry, false);
                    graph.addVertex(to);

                    P2<Street> streets = getEdgesForStreet(from, to, way, permissions, geometry);
                    Street street = streets.getFirst();
                    if (street != null) {
                        to.inStreet = street;
                        from.outStreet = street;
                    }
                    Street backStreet = streets.getSecond();
                    if (backStreet != null) {
                        to.outStreet = backStreet;
                        from.inStreet = backStreet;
                    }

                    startNode = endNode;
                    osmStartNode = _nodes.get(startNode);
                }
            }

            StreetUtils.unify(graph, intersectionsByLocation.values());
        }

        private Coordinate getCoordinate(OSMNode osmNode) {
            return new Coordinate(osmNode.getLon(), osmNode.getLat());
        }

        private void pruneFloatingIslands() {
            Map<Integer, HashSet<Integer>> subgraphs = new HashMap<Integer, HashSet<Integer>>();
            Map<Integer, ArrayList<Integer>> neighborsForNode = new HashMap<Integer, ArrayList<Integer>>();
            for (OSMWay way : _ways.values()) {
                List<Integer> nodes = way.getNodeRefs();
                for (int node : nodes) {
                    ArrayList<Integer> nodelist = neighborsForNode.get(node);
                    if (nodelist == null) {
                        nodelist = new ArrayList<Integer>();
                        neighborsForNode.put(node, nodelist);
                    }
                    nodelist.addAll(nodes);
                }
            }
            /* associate each node with a subgraph */
            for (int node : _nodes.keySet()) {
                if (subgraphs.containsKey(node)) {
                    continue;
                }
                HashSet<Integer> subgraph = computeConnectedSubgraph(neighborsForNode, node);
                for (int subnode : subgraph) {
                    subgraphs.put(subnode, subgraph);
                }
            }
            /* find the largest subgraph */
            HashSet<Integer> largestSubgraph = null;
            for (HashSet<Integer> subgraph : subgraphs.values()) {
                if (largestSubgraph == null || largestSubgraph.size() < subgraph.size()) {
                    largestSubgraph = subgraph;
                }
            }
            /* delete the rest */
            _nodes.keySet().retainAll(largestSubgraph);
        }

        private HashSet<Integer> computeConnectedSubgraph(
                Map<Integer, ArrayList<Integer>> neighborsForNode, int startNode) {
            HashSet<Integer> subgraph = new HashSet<Integer>();
            Queue<Integer> q = new LinkedList<Integer>();
            q.add(startNode);
            while (!q.isEmpty()) {
                int node = q.poll();
                for (int neighbor : neighborsForNode.get(node)) {
                    if (!subgraph.contains(neighbor)) {
                        subgraph.add(neighbor);
                        q.add(neighbor);
                    }
                }
            }
            return subgraph;
        }

        public void addNode(OSMNode node) {

            if (_nodes.containsKey(node.getId()))
                return;

            _nodes.put(node.getId(), node);

            if (_nodes.size() % 1000 == 0)
                _log.debug("nodes=" + _nodes.size());
        }

        public void addWay(OSMWay way) {
            if (_ways.containsKey(way.getId()))
                return;

            if (!(way.getTags().containsKey("highway") || "platform".equals(way.getTags().get(
                    "railway")))) {
                return;
            }

            _ways.put(way.getId(), way);

            if (_ways.size() % 1000 == 0)
                _log.debug("ways=" + _ways.size());
        }

        public void addRelation(OSMRelation relation) {

        }

        private String getVertexIdForNodeId(int nodeId) {
            return "osm node " + nodeId;
        }

        /* Attempt at handling oneway streets, cycleways, and whatnot. See
         * http://wiki.openstreetmap.org/wiki/Bicycle for various scenarios,
         * along with http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing#Oneway. */
        private P2<Street> getEdgesForStreet(Vertex from, Vertex to, OSMWay way,
                StreetTraversalPermission permissions, LineString geometry) {
            double d = from.distance(to);

            Map<String, String> tags = way.getTags();

            if(permissions == StreetTraversalPermission.NONE)
                return new P2<Street>(null, null);;

            Street street = null, backStreet = null;

            /* Three basic cases, 1) bidirectonal for everyone, 2) unidirctional for cars only,
             * 3) biderectional for pedestrians only. */
            if("yes".equals(tags.get("oneway")) &&
                    ("no".equals(tags.get("oneway:bicycle"))
                     || "opposite_lane".equals(tags.get("cycleway"))
                     || "opposite".equals(tags.get("cycleway")))) { // 2.
                street = getEdgeForStreet(from, to, way, d, permissions);
                if(permissions.remove(StreetTraversalPermission.CAR) != StreetTraversalPermission.NONE)
                    backStreet = getEdgeForStreet(to, from, way, d, permissions.remove(StreetTraversalPermission.CAR));
            } else if ("yes".equals(tags.get("oneway")) || "roundabout".equals(tags.get("junction"))) { // 3
                street = getEdgeForStreet(from, to, way, d, permissions);
                if(permissions.allows(StreetTraversalPermission.PEDESTRIAN))
                    backStreet = getEdgeForStreet(to, from, way, d, StreetTraversalPermission.PEDESTRIAN);
            } else { // 1.
               street = getEdgeForStreet(from, to, way, d, permissions);
               backStreet = getEdgeForStreet(to, from, way, d, permissions);
            }

            if (street != null) {
                street.setGeometry(geometry);
            }
            if (backStreet != null) {
                backStreet.setGeometry(geometry.reverse());
            }
            return new P2<Street>(street, backStreet);
        }

        private Street getEdgeForStreet(Vertex from, Vertex to, OSMWay way, double d,
                StreetTraversalPermission permissions) {

            String id = "way " + way.getId();

            id = unique(id);

            String name = way.getTags().get("name");
            if (name == null) {
                name = id;
            }
            Street street = new Street(from, to, id, name, d, permissions);

            /* TODO: This should probably generalized somehow? */
            if( "no".equals(way.getTags().get("wheelchair")) ||
               ("steps".equals(way.getTags().get("highway")) && !"yes".equals(way.getTags().get("wheelchair")))) {
                street.setWheelchairAccessible(false);
            }

            return street;
        }

        private StreetTraversalPermission getPermissionsForEntity(OSMWithTags entity) {
            Map<String, String> tags = entity.getTags();
            StreetTraversalPermission def    = null;
            StreetTraversalPermission permission = null;

            String access = tags.get("access");
            String motorcar = tags.get("motorcar");
            String bicycle = tags.get("bicycle");
            String foot = tags.get("foot");

            for(KeyValuePermission kvp : _tagPermissions.values()) {
                if(tags.containsKey(kvp.key) && kvp.value.equals(tags.get(kvp.key))) {
                    def = kvp.permission;
                    break;
                }
            }

            if(def == null) {
                if(_tagPermissions.containsKey("__default__")) {
                    String all_tags = null;
                    for(String key : tags.keySet()) {
                        String tag = key + "=" + tags.get(key);
                        if(all_tags == null) {
                            all_tags = tag;
                        } else {
                            all_tags += "; " + tag;
                        }
                    }
                    _log.debug("Used default permissions: " + all_tags);
                    def = _tagPermissions.get("__default__").permission;
                } else {
                    def = StreetTraversalPermission.ALL;
                }
            }

            /* Only access=*, motorcar=*, bicycle=*, and foot=* is examined,
             * since those are the only modes supported by OTP
             * (wheelchairs are not of concern here)
             *
             * Only *=no, and *=private are checked for, all other values are
             * presumed to be permissive (=> This may not be perfect, but is
             * closer to reality, since most people don't follow the rules
             * perfectly ;-)
             */
            if(access != null) {
                if("no".equals( access ) || "private".equals( access)) {
                    permission = StreetTraversalPermission.NONE;
                } else {
                    permission = StreetTraversalPermission.ALL;
                }
            } else if (motorcar != null || bicycle != null || foot != null) {
                permission = def;
            }

            if (motorcar != null) {
                if("no".equals(motorcar) || "private".equals(motorcar)) {
                    permission = permission.remove(StreetTraversalPermission.CAR);
                } else {
                    permission = permission.add(StreetTraversalPermission.CAR);
                }
            }

            if (bicycle != null) {
                if("no".equals(bicycle) || "private".equals(bicycle)) {
                    permission = permission.remove(StreetTraversalPermission.BICYCLE);
                } else {
                    permission = permission.add(StreetTraversalPermission.BICYCLE);
                }
            }

            if (foot != null) {
                if("no".equals(foot) || "private".equals(foot)) {
                    permission = permission.remove(StreetTraversalPermission.PEDESTRIAN);
                } else {
                    permission = permission.add(StreetTraversalPermission.PEDESTRIAN);
                }
            }


            if(permission == null)
                return def;

            return permission;
        }
    }
}
