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

package org.opentripplanner.api.extended.ws.model;

import java.util.Date;

import javax.xml.bind.annotation.XmlElement;

import org.onebusaway.gtfs.model.Route;

public class TransitServerDeparture implements Comparable<TransitServerDeparture> {
    @XmlElement(name="route")
    private TransitServerDetailedRoute route;
    
    private String headsign;
    private Date date;

    public TransitServerDeparture() {        
    }
    
    public TransitServerDeparture(Route route, String headsign, Date date) {
        this.route = new TransitServerDetailedRoute(route);
        this.setHeadsign(headsign);
        this.setDate(date);
    }


    public void setHeadsign(String headsign) {
        this.headsign = headsign;
    }

    public String getHeadsign() {
        return headsign;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Date getDate() {
        return date;
    }

    @Override
    public int compareTo(TransitServerDeparture other) {
        return this.getDate().compareTo(other.getDate());
    }
}
