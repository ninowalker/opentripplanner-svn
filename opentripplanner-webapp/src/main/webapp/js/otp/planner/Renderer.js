/* This program is free software: you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public License
   as published by the Free Software Foundation, either version 3 of
   the License, or (at your option) any later version.
   
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   
   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

otp.namespace("otp.planner");

/**
  * Web Map / TripPlanner
  * 
  * Will render a trip itinerary into various mediums (Ext Trees, OL Map Layers, plain html page, email, etc...).
  * 
  * Renderer is created by Planner.  It is used by Planner & TripTab to produce output of the trip for the given endpoint.
  */
otp.planner.Renderer = {

    map                     : null,
    locale                  : null,

    m_markerLayer : null,
    m_vectorLayer : null,

    // these members are set & re-set by the TripTab routines 
    m_tree        : null,
    m_itinerary   : null,

    /** */
    initialize : function(config)
    {
        console.log("enter planner.Renderer constructor");
        otp.configure(this, config);
        console.log("exit planner.Renderer constructor");
    },

    /** */
    draw : function(itin, tree)
    {
        console.log("enter planner.Renderer.draw");
        if(itin != null)
        {
            this.m_itinerary = itin;
            this.m_tree      = tree;
            this.drawItineraryOntoMap();
        }
        console.log("exit planner.Renderer.draw");
    },

    /** */
    clear : function()
    {
        console.log("enter planner.Renderer.clear");

        this.map.removeAllFeatures();

        console.log("exit planner.Renderer.clear");
    },

    /** */
    drawItineraryOntoMap : function()
    {
        console.log("enter Renderer.drawItineraryOntoMap");
        if(this.m_vectorLayer == null)
        {
            var vectorLayerOptions = {
                    isBaseLayer: false,
                    isFixed: false,
                    visibility: true,
                    projection: this.map.dataProjection
            };
            this.m_vectorLayer = new OpenLayers.Layer.Vector('trip-vector-layer', vectorLayerOptions);
            this.map.getMap().addLayer(this.m_vectorLayer);
            this.m_vectorLayer.setZIndex(222);   // HACK: sets click index of trip back for clicability of other map layers
                                    
            var style = otp.util.OpenLayersUtils.getMarkerStyle();
            var styleMap = new OpenLayers.StyleMap(style);
            var uniqueValueRules = otp.util.OpenLayersUtils.getMarkerUniqueValueRules();
            styleMap.addUniqueValueRules("default", "type", uniqueValueRules);
            
            var markerLayerOptions = {
                    isBaseLayer: false,
                    rendererOptions: {yOrdering: true},
                    projection: this.map.dataProjection,
                    styleMap: styleMap
            };
            this.m_markerLayer = new OpenLayers.Layer.Vector('trip-marker-layer', markerLayerOptions);
            this.map.getMap().addLayer(this.m_markerLayer);
            this.m_markerLayer.setZIndex(223);   // HACK: sets click index of trip back for clickability of other map layers
        }

        // draw graphic plan on the map
        this.clear();
        this.m_itinerary.draw(this.m_vectorLayer, this.m_markerLayer);
        this.map.zoomToExtent(this.m_itinerary.getExtent());

        // draw text plan on the ui (tree)
        otp.util.ExtUtils.clearTreeNodes(this.m_tree);
        var n = this.m_itinerary.getTreeNodes(this.legClick, this);
        this.m_tree.root.appendChild(n);

        console.log("exit Renderer.drawItineraryOntoMap");
    },

     /** */
    drawItineraryIntoPrinter : function()
    {
        console.log("enter planner.Renderer.drawItinerayIntoPrinter");
        console.log("exit planner.Renderer.drawItinerayIntoPrinter");
    },

    /** */
    drawItineraryIntoEmail : function()
    {
        console.log("enter planner.Renderer.drawItinerayIntoEmail");
        console.log("exit planner.Renderer.drawItinerayIntoEmail");
    },

    /**
     * callback to the itinerary legs for mouse-click / zoom purposes
     */
    legClick : function(node, event)
    {
        console.log("enter Renderer.legClick");

        if(node.id.indexOf(otp.planner.Utils.TRIP_ID) >= 0)
        {
            this.map.zoomToExtent(this.m_markerLayer.getDataExtent());
        }
        else
        {
            var coord = null;
            if(node.id.indexOf(otp.planner.Utils.FROM_ID) >= 0)
            {
                coord = this.m_itinerary.getFrom();
            }
            else if(node.id.indexOf(otp.planner.Utils.TO_ID) >= 0)
            {
                coord = this.m_itinerary.getTo();
            }
            else 
            {
                coord = this.m_itinerary.getLegStartPoint(node.id);
            }
            coord = coord.get('geometry');
            this.map.zoom(coord.x, coord.y);
        }

        console.log("exit Renderer.legClick");
    },

    CLASS_NAME: "otp.planner.Renderer"
}

otp.planner.Renderer = new otp.Class(otp.planner.Renderer);
