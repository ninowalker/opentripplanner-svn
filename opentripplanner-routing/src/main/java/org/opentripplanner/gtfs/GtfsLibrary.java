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

package org.opentripplanner.gtfs;


import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.FareAttribute;
import org.onebusaway.gtfs.model.FareRule;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;

import org.opentripplanner.routing.core.FareRuleSet;
import org.opentripplanner.routing.core.TraverseMode;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;

public class GtfsLibrary {

    public static final char ID_SEPARATOR = '_';

    public static GtfsContext createContext(GtfsRelationalDao dao) {
        CalendarService calendarService = createCalendarService(dao);
        return createContext(dao,calendarService);
    }

    public static GtfsContext createContext(GtfsRelationalDao dao, CalendarService calendarService) {
        return new GtfsContextImpl(dao, calendarService);
    }

    public static GtfsContext readGtfs(File path) throws IOException {
        return readGtfs(path, null);
    }

    public static GtfsContext readGtfs(File path, String defaultAgencyId) throws IOException {

        GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();

        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(path);
        reader.setEntityStore(dao);

        if (defaultAgencyId != null)
            reader.setDefaultAgencyId(defaultAgencyId);

        reader.run();

        CalendarService calendarService = createCalendarService(dao);

        return new GtfsContextImpl(dao, calendarService);
    }

    public static CalendarService createCalendarService(GtfsRelationalDao dao) {

        CalendarServiceDataFactoryImpl factory = new CalendarServiceDataFactoryImpl();
        factory.setGtfsDao(dao);
        CalendarServiceData data = factory.createData();

        CalendarServiceImpl service = new CalendarServiceImpl();
        service.setData(data);
        return service;
    }

    public static AgencyAndId convertIdFromString(String value) {
        int index = value.indexOf(ID_SEPARATOR);
        if (index == -1) {
            throw new IllegalStateException("invalid agency-and-id: " + value);
        } else {
            return new AgencyAndId(value.substring(0, index), value.substring(index + 1));
        }
    }

    public static String convertIdToString(AgencyAndId aid) {
        return aid.getAgencyId() + ID_SEPARATOR + aid.getId();
    }

    public static String getRouteName(Route route) {
        if (route.getShortName() != null)
            return route.getShortName();
        return route.getLongName();
    }

    public static TraverseMode getTraverseMode(Route route) {
        switch (route.getType()) {
        case 0:
            return TraverseMode.TRAM;
        case 1:
            return TraverseMode.SUBWAY;
        case 2:
            return TraverseMode.RAIL;
        case 3:
            return TraverseMode.BUS;
        case 4:
            return TraverseMode.FERRY;
        case 5:
            return TraverseMode.CABLE_CAR;
        case 6:
            return TraverseMode.GONDOLA;
        case 7:
            return TraverseMode.FUNICULAR;
        default:
            throw new IllegalArgumentException("unknown gtfs route type " + route.getType());
        }
    }

    public static long getTimeAsDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static class GtfsContextImpl implements GtfsContext {

        private GtfsRelationalDao _dao;

        private CalendarService _calendar;

        private HashMap<AgencyAndId, FareRuleSet> fareRules = null;
        
        public GtfsContextImpl(GtfsRelationalDao dao, CalendarService calendar) {
            _dao = dao;
            _calendar = calendar;
        }

        @Override
        public GtfsRelationalDao getDao() {
            return _dao;
        }

        @Override
        public CalendarService getCalendarService() {
            return _calendar;
        }

        @Override
        public HashMap<AgencyAndId, FareRuleSet> getFareRules() {
            if (fareRules == null) {
                fareRules = new HashMap<AgencyAndId, FareRuleSet>();
                Collection<FareRule> rules = getDao().getAllFareRules();
                for (FareRule rule: rules) {
                    FareAttribute fare = rule.getFare();
                    AgencyAndId id = fare.getId();
                    FareRuleSet fareRule = fareRules.get(id);
                    if (fareRule == null) {
                        fareRule = new FareRuleSet();
                        fareRules.put(id, fareRule);
                    }
                    String contains = rule.getContainsId();
                    if (contains != null) {
                        fareRule.addContains(contains);
                    }
                    String origin = rule.getOriginId();
                    String destination = rule.getDestinationId();
                    if (origin != null || destination != null) {
                        fareRule.addOriginDestination(origin, destination);
                    }
                    Route route = rule.getRoute();
                    if (route != null) {
                        AgencyAndId routeId = route.getId();
                        fareRule.addRoute(routeId);
                    }
                }
            }
            return fareRules;
        }

        @Override
        public FareAttribute getFareAttribute(AgencyAndId fareId) {
            return getDao().getFareAttributeForId(fareId);
        }
    }
}
