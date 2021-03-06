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
  * TopoRenderer
  * 
  * Renders a 2-D topographic map of a trip in a specified panel using the HTML Canvas element.  
  * TopoRenderer is created by Planner.
  */

otp.planner.TopoRenderer = {
	
	panel : null,
	
    /** */
    initialize : function(config)
    {
        otp.configure(this, config);
    },
    
    draw : function(itin, tree) 
    {
        if (!otp.planner.Utils.supportsCanvas()) {
            // No sense going on if we can't draw the topo map
            return;
        }
        
        var leg = itin.m_legStore.getAt(0);
        var steps = leg.data.steps;
        
        var distance = 0, minElev = 100000, maxElev = -1000;
        
        // calculate the distance and elevation range
        for (var i = 0; i < steps.length; i++)
        {
            distance += steps[i].distance;
            if (typeof steps[i].elevation == 'undefined') {
                continue;
            }
            var elevArr = steps[i].elevation.split(",");
            for (var j = 1; j < elevArr.length; j+=2) {
                var elevFt = elevArr[j] * 3.2808399;
                if (elevFt < minElev) {
                    minElev = elevFt;
                }
                if (elevFt > maxElev) {
                    maxElev = elevFt;
                }
            }       
        }
        minElev = 100*Math.floor(minElev/100);
        maxElev = 100*Math.ceil(maxElev/100);
        var height = this.panel.getEl().getHeight()-20; // adjust to allow for scrollbar
        var topRowHeight = 24, res = 5; 
        var subDivisions = (maxElev-minElev)/100;
        var axisWidth = 45, topoWidth = (distance*3.2808399)/res;
                        
        var canvas = this.createCanvasAndContainer(axisWidth + topoWidth, height);
        var ctx = canvas.getContext('2d'); 
     
        var divHeight = (height-topRowHeight)/subDivisions;

        //render the y-axis elevation labels
        ctx.fillStyle = "rgb(47,79,79)"; 
        ctx.fillRect(0, 0, axisWidth, height);
        for (var d = 0; d < subDivisions+1; d++) {
            ctx.fillStyle = "rgb(255,255,255)"; 
            ctx.beginPath();
            var y = height - d * divHeight;
            ctx.moveTo(5, y);
            ctx.lineTo(axisWidth, y);
            ctx.lineTo(axisWidth-12, y-12);
            ctx.lineTo(5, y-12);
            ctx.closePath();
            ctx.fill();
            ctx.fillStyle = "rgb(47,79,79)"; 
            ctx.font = "bold 8pt sans-serif";
            this.fillText(ctx, (minElev+d*100) + "'", 7, y-2);
        }
        
        //render the graduated-blue background
        if (subDivisions == 1) {
            ctx.fillStyle = "rgb(135,206,255)"; 
            ctx.fillRect (axisWidth, topRowHeight, topoWidth, height-topRowHeight);
        } else {
            for (var d = 0; d < subDivisions; d++) {
                var r = 135 + 69*(1-d/(subDivisions-1));
                var g = 206 + 39*(1-d/(subDivisions-1));
                ctx.fillStyle = "rgb("+Math.round(r)+","+Math.round(g)+",255)"; 
                ctx.fillRect(axisWidth, topRowHeight+divHeight*d, topoWidth, divHeight);
            }
        }
     
        
        // main segment loop
        var currentX = axisWidth;
        var xCoords = new Array(), yCoords = new Array();
        
        for (i = 0; i < steps.length; i++) {
            var step = steps[i];
            var segWidth = (step.distance*3.2808399)/res;

            // render the top row
            var gradient = ctx.createLinearGradient(currentX, 0, currentX+segWidth, 0);
            gradient.addColorStop(0, "rgb(47,79,79)");
            gradient.addColorStop(1, "rgb(128,128,128)");
            ctx.fillStyle = gradient; 
            ctx.fillRect (currentX, 0, segWidth, topRowHeight);

            ctx.fillStyle = "rgb(47,79,79)"; 
            ctx.fillRect(currentX, 0, 2, height);
     
            ctx.fillStyle = "rgb(255,255,255)";
            ctx.font = "bold 8pt sans-serif";
            this.fillText(ctx, step.streetName, currentX+5, 10);
            ctx.font = "8pt sans-serif";
            this.fillText(ctx, prettyDistance(step.distance) + '', currentX+5, 21);
            
            if (step.elevation != undefined) {
                var elevArr = steps[i].elevation.split(",");
                var stepLenM = elevArr[elevArr.length-2]; 
                for (var j = 0; j < elevArr.length-1; j+=2) {
                    var posM = elevArr[j];
                    var elevFt = elevArr[j+1] * 3.2808399;
                    var x = currentX + (posM/stepLenM)*segWidth;
                    var y = height-(height-topRowHeight)*(elevFt-minElev)/(maxElev-minElev);
                    xCoords.push(x);
                    yCoords.push(y);
                }       
            }
            
            currentX += segWidth;
        }
                
        // draw the "ground"
        ctx.fillStyle = "rgba(0,128,0, 0.5)"; 
        ctx.beginPath();
        for (i = 0; i < xCoords.length; i++) {
            if (i == 0) {
                ctx.moveTo(xCoords[i], yCoords[i]);
            } else {
                ctx.lineTo(xCoords[i], yCoords[i]);
            }
        }
        ctx.lineTo(axisWidth+topoWidth, height);
        ctx.lineTo(axisWidth+1, height);
        ctx.closePath();
        ctx.fill();

        ctx.strokeStyle = "rgb(128,0,0)"; 
        ctx.lineWidth = 2;
        ctx.beginPath();
        for (i = 0; i < xCoords.length; i++) {
            if (i == 0) {
                ctx.moveTo(xCoords[i], yCoords[i]);
            } else {
                ctx.lineTo(xCoords[i], yCoords[i]);
            }
        }
        ctx.stroke();
    },

    /*
     * Creates a new canvas and container div and returns the container element.
     */
    createCanvasAndContainer : function(width, height) {
        var containerDiv = document.createElement('div');
        var canvas = document.createElement('canvas');
        
        // Width and height must be set *before* the IE hack below.
        canvas.setAttribute('height', height);
        canvas.setAttribute('width', width);
        
        // Hack required for IE canvas because our <canvas> element is created
        // dynamically.
        if (typeof G_vmlCanvasManager != 'undefined') {
            canvas = G_vmlCanvasManager.initElement(canvas);
        }
        containerDiv.setAttribute('class', 'canvas-container');
        containerDiv.appendChild(canvas);
        
        // Remove all existing elements from the topo panel and add the new div
        var panelEl = this.panel.getEl();
        while (panelEl.first()) { 
            panelEl.first().remove();
        }
        panelEl.appendChild(containerDiv);
        
        return canvas;
    },
    
    fillText : function(ctx, str, x, y) {
        // TODO: Use http://code.google.com/p/canvas-text instead
        if (typeof ctx.fillText == 'undefined') {
            if (typeof ctx.mozDrawText != 'undefined') {
                //for FF 3.0.
                ctx.save();
                ctx.moveTo(x, y);
                ctx.mozDrawText(str);
                ctx.restore();
            }
        } else {
            ctx.fillText(str, x, y);
        }
    },

    CLASS_NAME: "otp.planner.TopoRenderer"
};

otp.planner.TopoRenderer = new otp.Class(otp.planner.TopoRenderer);