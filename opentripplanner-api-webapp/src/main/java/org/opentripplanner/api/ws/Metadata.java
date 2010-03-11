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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jettison.json.JSONException;
import org.opentripplanner.routing.core.Graph;
import org.springframework.beans.factory.annotation.Autowired;

import com.sun.jersey.api.spring.Autowire;

@Path("/metadata")
@XmlRootElement
@Autowire
public class Metadata {

    private Graph graph;

    @Autowired
    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    /**
     * Returns metadata about the graph -- presently, this is just the extent of the graph.
     *
     * @return Returns either an XML or a JSON document, depending on the HTTP Accept header of the
     *         client making the request.
     *
     * @throws JSONException
     */
    @GET
    @Produces( { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public GraphMetadata getMetadata() throws JSONException {

        return new GraphMetadata(graph);
    }
}
