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
  * otp/planner/Forms.js 
  * 
  * Purpose is to manage the Ext input forms for the trip planner.
  * 
  */
otp.planner.StaticForms = {

    FIELD_ANCHOR     : '95%',

    // external (config) objects
    locale           : otp.locale.English,
    planner          : null,
    contextMenu      : null,
    poi              : null,

    url              : '/opentripplanner-api-webapp/ws/plan',

    // forms & stores
    m_panel          : null,

    m_fromErrorStore : null,
    m_toErrorStore   : null,
    m_geoErrorPopup  : null,

    m_fromForm       : null,
    m_toForm         : null,
    m_toPlace        : null,
    m_fromPlace      : null,
    m_intermediatePlaces : null,

    fromToOverride   : null,  // over-ride me to get rid of From / To from with something else

    m_date           : null,
    m_time           : null,
    m_arriveByStore     : null,
    m_arriveByForm      : null,

    m_maxWalkDistanceStore      : null,
    m_maxWalkDistanceForm       : null,
    m_optimizeStore  : null,
    m_optimizeForm   : null,
    m_modeStore      : null,
    m_modeForm       : null,
    m_wheelchairForm : null,
    m_wheelchairStore: null,

    // buttons
    m_submitButton   : null,

    m_fromCoord      : otp.util.Constants.BLANK_LAT_LON,
    m_toCoord        : otp.util.Constants.BLANK_LAT_LON,

    m_xmlRespRecord  : null, 

    /**
     * @consturctor
     * @param {Object} config
     */
    initialize : function(config)
    {
        // step 1: bit of init (before configure override happens)
        otp.configure(this, config);

        // step 2: setup
        if(this.m_xmlRespRecord == null)
        this.m_xmlRespRecord  = new Ext.data.XmlReader({
                record: 'response',
                success: '@success'
            },
            ['date', 'time', 'from', 'to', 'locations', 'fromList', 'toList']
        );

        this.makeMainPanel();

        // step 3: set the singleton & statis stuff to this 
        otp.planner.StaticForms = this;
    },

    /**
     * 
     */
    getPanel : function()
    {
        return this.m_panel;
    },

    /**
     *
     */
    submit : function()
    {
        // hide stuff that might be open
        this.collapseComboBoxes();
        this.hideErrorDialogs();

        otp.util.ExtUtils.setTripPlannerCookie();

        this.m_panel.form.submit( {
            method : 'GET',
            url : this.url,
            waitMsg : this.locale.tripPlanner.labels.submitMsg
        });

        // analytics
        otp.util.AnalyticsUtils.gaEvent(otp.util.AnalyticsUtils.TRIP_SUBMIT);
    },

    /**
     * pre submit does some necessary bookkeeping for the form. the real work
     * happens in the (overridden) submit method
     */
    preSubmit : function(form, action)
    {
        // step 1: save off the state of the from & to text forms (eg: remember input into these fields)
        this.m_fromForm.persist();
        this.m_toForm.persist();

         // step 2: hide stuff that might be open
        this.collapseComboBoxes();
        this.hideErrorDialogs();

        // step 3: fixe up some of the form values before sending onto the trip planner web service
        form.setValues({
            fromPlace: this.getFrom(),
            toPlace:   this.getTo()
        });
    },

    /** */
    submitSuccess : function(form, action)
    {
        var result = this.planner.newTripPlan(action.response.responseXML, this.getFormData());
        if (!result)
        {
            this.tripRequestError(action.response.responseXML);
            return;
        }
        if(this.poi) this.poi.clearTrip();
        otp.util.AnalyticsUtils.gaEvent(otp.util.AnalyticsUtils.TRIP_SUCCESS);
    },

    /** */
    submitFailure : function(form, action)
    {
        this.m_submitButton.focus();
        this.tripRequestError(action.response.responseXML);
    },


    /** error handler */
    tripRequestError : function(xml)
    {
        var message  = null;
        var code     = -111;
        var options  = null;
        var fromGrid = null;
        var toGrid   = null;
        
        // load xml to see what errors we have
        try
        {
            var gridCols = [
                {
                    header:    'Name',
                    width:     .75,
                    sortable:   true,
                    dataIndex: 'description'
                },
                {
                    header:    'City',
                    width:     .25,
                    sortable:true,
                    dataIndex: 'areaValue'
                }
            ];
            
            // try to populate the from & to form stores (in case of ambiguous results of geocoding)
            var to = Ext.DomQuery.selectNode('toList', xml);
            if(to != null)
            {
                var toStore = otp.util.ExtUtils.makeLocationStore();
                toStore.loadData(to);
                toGrid   = otp.util.ExtUtils.makeGridView(toStore, gridCols, {title:this.locale.tripPlanner.error.geoToMsg, iconCls:'end-icon'});
                toGrid.on(
                        'rowclick', function(g, i, e)
                        {
                            var n = otp.util.ExtUtils.gridClick(g, i, {description:'', x:'', y:''});
                            this.setTo(n.description, n.x, n.y, true);
                        },
                        this);
                toGrid.on(
                        'rowdblclick', function(g, i, e)
                        {
                            var n = otp.util.ExtUtils.gridClick(g, i, {description:'', x:'', y:''});
                            this.setTo(n.description, n.x, n.y, true);
                            this.m_geoErrorPopup.close();
                        },
                        this);
                options = true;
            }

            var from = Ext.DomQuery.selectNode('fromList', xml);
            if(from != null)
            {
                var fStore = otp.util.ExtUtils.makeLocationStore();
                fStore.loadData(from);
                fromGrid = otp.util.ExtUtils.makeGridView(fStore, gridCols, {title:this.locale.tripPlanner.error.geoFromMsg, iconCls:'start-icon'});
                fromGrid.on(
                        'rowclick', function(g, i, e)
                        {
                            var n = otp.util.ExtUtils.gridClick(g, i, {description:'', x:'', y:''});
                            this.setFrom(n.description, n.x, n.y, true);
                        },
                        this);
                fromGrid.on(
                        'rowdblclick', function(g, i, e)
                        {
                            var n = otp.util.ExtUtils.gridClick(g, i, {description:'', x:'', y:''});
                            this.setFrom(n.description, n.x, n.y, true);
                            this.m_geoErrorPopup.close();
                        },
                        this);
                options = true;
            }

            // if not ambiguous results, then show a dialog
            if(options)
            {
                // put the panel(s) into an array for the parent panel
                var errorWindowHeight = 0;
                var e  = new Array();
                if(fromGrid)
                {
                    errorWindowHeight += 185;
                    e.push(fromGrid);
                } 
                if(toGrid) 
                {
                    errorWindowHeight += 185;
                    e.push(toGrid);
                }
                if(errorWindowHeight < 200) errorWindowHeight = 220;

                // create the geo error popup
                var zz = new Ext.Panel({layout:'anchor', items:e});
                this.m_geoErrorPopup = otp.util.ExtUtils.makePopup({layout:'anchor',items:[zz]}, this.locale.tripPlanner.error.title, true, 360, errorWindowHeight, true, false, 50, 170);
                message = null;
                otp.util.AnalyticsUtils.gaEvent(otp.util.AnalyticsUtils.TRIP_GEO_ERROR + "/from/" + (from != null) + "/to/" + (to != null));
            }
            else
            {
                var err  = Ext.DomQuery.selectNode('error', xml);
                message  = Ext.DomQuery.selectValue('msg', err);
                code     = Ext.DomQuery.selectValue('id', err);
                if (!message && code)
                {
                    try
                    {
                        code = parseInt(code);
                    }
                    catch (e)
                    {
                        code = 500;
                    }
                    message = this.locale.tripPlanner.msgcodes[code] || this.locale.tripPlanner.msgcodes[500];
                }
                otp.util.AnalyticsUtils.gaEvent(otp.util.AnalyticsUtils.TRIP_ERROR + "/" + code);
            }
        } 
        catch(e) 
        {
            if(message == null || message == '')
                message = this.locale.tripPlanner.error.deadMsg;
        }
        
        if(message != null && message.length > 0)
        {
            // show the error
            Ext.MessageBox.show({
                title:    this.locale.tripPlanner.error.title,
                msg:      message,
                buttons:  Ext.Msg.OK,
                icon:     Ext.MessageBox.ERROR,
                animEl:   'tp-error-message',
                minWidth: 170,
                maxWidth: 270
            });

            // kill the message box after 5 seconds
            setTimeout(function()
            {
                try {
                    Ext.MessageBox.hide();
                } catch(e) {}
            }, 5000);
        }
    },


    /**
     * 
     */
    clear : function()
    {
        this.collapseComboBoxes();
        this.hideErrorDialogs();
        this.clearFrom(true);
        this.clearTo(true);
        this.m_submitButton.enable();
        this.m_submitButton.focus();
    },

    /** */
    collapseComboBoxes : function()
    {
        try
        {
            this.m_fromForm.collapse();
            this.m_toForm.collapse();
        }
        catch(e)
        {
            console.log("Forms.collapseComboBox " + e);
        }
    },

    /** */
    hideErrorDialogs : function()
    {
        // hide the default message box
        try {Ext.MessageBox.hide();        } catch(e){}
        try {this.m_geoErrorPopup.close();  } catch(e){}
    },

    /** wrapper (with try / catch) for planner focus() */
    focus : function()
    {
        this.planner.focus();
    },


    /** will look in text forms first, then hidden form variable, then coordinate for value */
    /** TODO: from & to form values must be re-thought we only allow one value x,y -or- string value */ 
    getFrom : function()
    {
        var retVal = this.m_fromForm.getRawValue();
        if(retVal == null || retVal.length < 1)
            retVal = this.m_fromPlace.getRawValue();
        if(retVal == null || retVal.length < 1)
            retVal = this.m_fromCoord;
        return retVal;
    },


    /**
     * set from form with either/both a string and X/Y
     * NOTE: x & y are reversed -- really are lat,lon
     *
     */
    setFrom : function(fString, x, y, moveMap, noPoi)
    {
        this.focus();
        otp.util.ExtUtils.formSetRawValue(this.m_fromForm, fString);
        if(x && x > -181.1 && y && y > -181.1) 
        {
            this.m_fromCoord = x + ',' + y;
            this.setRawInput(this.m_fromCoord, this.m_fromForm);
            this.setRawInput(this.m_fromCoord, this.m_fromPlace);
            if(this.poi && !noPoi) this.poi.setFrom(y, x, fString, moveMap);
        }
    },

    /** will look in text forms first, then hidden form variable, then coordinate for value */
    getTo : function()
    {
        var retVal = this.m_toForm.getRawValue();
        if(retVal == null || retVal.length < 1)
            retVal = this.m_toPlace.getRawValue();
        if(retVal == null || retVal.length < 1)
            retVal = this.m_toCoord;
        return retVal;
    },

    /**
     * set to form with either/both a string and X/Y
     * NOTE: x & y are reversed -- really are lat,lon
     */
    setTo : function(tString, x, y, moveMap, noPoi)
    {
        this.focus();
        otp.util.ExtUtils.formSetRawValue(this.m_toForm, tString);
        if(x && x > -180.1 && y && y > -181.1) 
        {
            this.m_toCoord = x + ',' + y;
            this.setRawInput(this.m_toCoord, this.m_toForm);
            this.setRawInput(this.m_toCoord, this.m_toPlace);
            if(this.poi && !noPoi) this.poi.setTo(y, x, tString, moveMap);
        }
    },

    /** a simple helper class to set data in a form */
    setRawInput : function(p, f)
    {
        var retVal = false;

        if(p != null 
        && p !== true 
        && p != "true" 
        && p.match('Address, .*Stop ID') == null
        )
        {
            otp.util.ExtUtils.formSetRawValue(f, p);
            retVal = true;
        }

        return retVal;
    },

    /** */
    populate : function(params)
    {
        var forms = this;
        if(params)
        {
            this.clearFrom();
            this.clearTo();

            this.setRawInput(params.Orig,      forms.m_fromForm);
            this.setRawInput(params.Orig,      forms.m_fromPlace);
            this.setRawInput(params.Dest,      forms.m_toForm);
            this.setRawInput(params.Dest,      forms.m_toPlace);
            this.setRawInput(params.from,      forms.m_fromForm);
            this.setRawInput(params.from,      forms.m_fromPlace);
            this.setRawInput(params.to,        forms.m_toForm);
            this.setRawInput(params.to,        forms.m_toPlace);
            this.setRawInput(params.fromPlace, forms.m_fromForm);
            this.setRawInput(params.fromPlace, forms.m_fromPlace);
            this.setRawInput(params.toPlace,   forms.m_toForm);
            this.setRawInput(params.toPlace,   forms.m_toPlace);

            if(params.fromCoord && params.fromCoord.indexOf('0.0') != 0)
                this.m_fromCoord = params.fromCoord;
            if(params.toCoord && params.toCoord.indexOf('0.0') != 0)
                this.m_toCoord = params.toCoord;

            // TODO - should find a way to make the xxx (String value, vs coded value) work properly in submit
            var time=false;
            var date=false;

            if(params.date)
            {
                forms.m_date.setRawValue(params.date);
                date = true;
            }
            else if(params.on)
            {
                forms.m_date.setRawValue(params.on);
                date = true;
            }

            // arrive by parameter 
            if(params.arrParam && (params.arrParam.indexOf("rive") > 0 || params.arrParam == "A"))
                forms.m_arriveByForm.setValue('A');
            if(params.arr && (params.arr.indexOf("rive") > 0 || params.arr == "A"))
                forms.m_arriveByForm.setValue('A');
            if(params.Arr && (params.Arr.indexOf("rive") > 0 || params.Arr == "A"))
                forms.m_arriveByForm.setValue('A');
            if(params.arrParam && (params.arrParam.indexOf("part") > 0 || params.arrParam == "D"))
                forms.m_arriveByForm.setValue('D');
            if(params.arr && (params.arr.indexOf("part") > 0 || params.arr == "D"))
                forms.m_arriveByForm.setValue('D');
            if(params.Arr && (params.Arr.indexOf("part") > 0 || params.Arr == "D"))
                forms.m_arriveByForm.setValue('D');
            if(params.after)
            {
                time = params.after.replace(/\./g, "");
                forms.m_time.setRawValue(time);
                forms.m_arriveByForm.setValue('D');
                time = true;
            }
            else if(params.by)
            {
                time = params.by.replace(/\./g, "");
                forms.m_time.setRawValue(time);
                forms.m_arriveByForm.setValue('A');
                time = true;
            }

            if(params.time)
            {
                time = params.time.replace(/\./g, "");
                forms.m_time.setRawValue(time);
                time = true;
            }
            if(params.opt)
                forms.m_optimizeForm.setValue(params.opt);
            if(params.min)
                forms.m_optimizeForm.setValue(params.min);
            if(params.maxWalkDistance)
                forms.m_maxWalkDistanceForm.setValue(params.maxWalkDistance);
            if(params.mode)
                forms.m_modeForm.setValue(params.mode);
            if(params.wheelchair)
                forms.m_wheelchairForm.setValue(params.wheelchair);

            // stupid trip planner form processing...

            // Hour=7&Minute=02&AmPm=pm
            if(!time && params.Hour && params.Minute && params.AmPm)
            {
                time = params.Hour + ":" +  params.Minute + " " + params.AmPm.toLowerCase();
                time = time.replace(/\./g, "");
                forms.m_time.setRawValue(time);
                time = true;
            }

            // &month=Oct&day=7
            if(!date && params.month && params.day)
            {
                var d = new Date();
                var y = d.getFullYear();
                var n = d.getMonth();
                var m = otp.util.DateUtils.getMonthAsInt(params.month);
                if(m < n) // assume a month that is prior to now means next year
                    y++;

                if(params.day.length == 1) 
                    params.day = "0" + params.day;

                date = m + "/" + params.day + "/" + y;
                forms.m_date.setRawValue(date);
                date = true;
            }
        }
    },


    /**
     * returns current state (values) of the trip planner forms
     * used for creating a URL populated with values to the trip planner
     */
    getFormData : function(url)
    {
        var retVal = {};
        retVal.url       = url;
        retVal.itinID    = "1";
        retVal.fromPlace = this.getFrom();
        retVal.toPlace   = this.getTo();
        retVal.date      = this.m_date.getRawValue();
        retVal.time      = this.m_time.getRawValue();
        retVal.arriveBy  = this.m_arriveByForm.getRawValue();
        retVal.opt       = this.m_optimizeForm.getValue();
        retVal.maxWalkDistance = this.m_maxWalkDistanceForm.getValue();
        retVal.mode            = this.m_modeForm.getValue();
        retVal.wheelchair      = this.m_wheelchairForm.getValue();
        retVal.intermediate_places = ''; //TODO: intermediate stops

        // break up the from coordinate into lat & lon
        var coord = this.m_fromCoord;
        if(coord == otp.util.Constants.BLANK_LAT_LON)
            coord = retVal.fromPlace;
        retVal.fromLat = this.getLat(coord);
        retVal.fromLon = this.getLon(coord);

        // break up the to coordinate into lat & lon
        coord = this.m_toCoord;
        if(coord == otp.util.Constants.BLANK_LAT_LON)
            coord = retVal.toPlace;
        retVal.toLat   = this.getLat(coord);
        retVal.toLon   = this.getLon(coord);

        try
        {
            retVal.time = retVal.time.replace(/\./g, "");
        }
        catch(e)
        {
        }

        return retVal;
    },

    /** returns the second value from a comma separated string eg: 0.0,0.returnMe*/
    getLon : function(coord) {
        var retVal = null;

        try
        {
            retVal = coord.substring(coord.indexOf(',') + 1); 
        }
        catch(e)
        {
        }

        return retVal;
    },

    /** returns the first value from a comma separated string eg: 0.returnMe,0.0 */
    getLat : function(coord) {
        var retVal = null;

        try
        {
            retVal = coord.substring(0, coord.indexOf(',')); 
        }
        catch(e)
        {
        }

        return retVal;
    },

    /**
     * 
     */
    clearFrom : function(formToo)
    {
        this.m_fromCoord = otp.util.Constants.BLANK_LAT_LON; 

        if(formToo && formToo === true)
        {
            this.m_fromForm.clear();
        }
    },

    /** 
     */
    clearTo : function(formToo)
    {
        this.m_toCoord = otp.util.Constants.BLANK_LAT_LON; 

        if(formToo && formToo === true)
        {
            this.m_toForm.clear();
        }
    },

    /** */
    selectFrom : function(combo, record, num)
    {
         this.m_fromCoord = otp.util.ExtUtils.getCoordinate(record);
    },

    /** */
    selectTo : function(combo, record, num)
    {
        this.m_toCoord = otp.util.ExtUtils.getCoordinate(record);
    },

    /** */
    getContextMenu : function(cm)
    {
        if(cm != null)
            this.contextMenu = cm;

        var retVal = [
        {
            text    : this.locale.contextMenu.fromHere,
            iconCls : 'cFromHere',
            scope   : this,
            handler : function () {
                var ll = this.contextMenu.getMapCoordinate();
                this.setFrom(ll.lat+ "," + ll.lon, ll.lat, ll.lon);
            }
        }
        ,
        {
            text    : this.locale.contextMenu.toHere,
            iconCls : 'cToHere',
            scope   : this,
            handler : function () {
                var ll = this.contextMenu.getMapCoordinate();
                this.setTo(ll.lat + "," + ll.lon, ll.lat, ll.lon);
            }
        }
        ];
        return retVal;
    },


    /**
     * from & to form creation
     */
    makeMainPanel : function()
    {
        var fromToForms = this.makeFromToForms();
        var dateTime    = this.makeDateTime();
        var fromToArray = [fromToForms, dateTime];

        // override will override the display of the from & to form
        if(this.fromToOverride)
        {
            fromToArray = [this.fromToOverride, dateTime];
        }

        var fromToFP = new Ext.form.FieldSet({
            labelWidth:  40,
            border:      false,
            items:       fromToArray
        });

        var optForms = this.makeOptionForms();
        var optFP = new Ext.form.FieldSet({
            labelWidth:  110,
            border:      false,
            items:       optForms
        });

        this.m_submitButton = new Ext.Button({
            text:    this.locale.tripPlanner.labels.planTrip,
            id:      'trip-submit',
            scope:   this,
            style:'background-color:FF0000;',
            handler: this.submit
        });

        this.m_toPlace   = new Ext.form.Hidden({name: 'toPlace',   value: ''});
        this.m_fromPlace = new Ext.form.Hidden({name: 'fromPlace', value: ''});
        this.m_intermediatePlaces = new Ext.form.Hidden({name: 'intermediatePlaces', value: ''});

        var conf = {
            title:       this.locale.tripPlanner.labels.tabTitle,
            id:          'form-tab',
            buttonAlign: 'center',
            border:      false,
            keys:        {key: [10, 13], scope: this, handler: this.submit},
            items:       [  fromToFP,
                            optFP,
                            this.m_toPlace,
                            this.m_fromPlace,
                            this.m_intermediatePlaces,
                            this.m_submitButton
                         ],

            // configure how to read the XML Data
            reader:      this.m_xmlRespRecord,

            // reusable error reader class defined at the end of this file
            errorReader: new Ext.form.XmlErrorReader()
        };
        this.m_panel = new Ext.FormPanel(conf);

        this.m_panel.on({
                scope:           this,
                beforeaction:    this.preSubmit,
                actionfailed:    this.submitFailure,
                actioncomplete:  this.submitSuccess
        });
    },


    /**
     * from & to form creation
     * NOTE: defining fromToOverride (needs to be at least a Template, of not another form) will 
     *       override the display of the from & to defined here
     */
    makeFromToForms : function()
    {
        // step 1: these give the from & to forms 'memory' -- all submitted strings are saved off in the cookie for latter use
        Ext.state.Manager.setProvider(new Ext.state.CookieProvider());
        Ext.state.Manager.getProvider();

        // step 2: create the forms
        this.m_fromForm = new otp.core.ComboBox({layout: 'anchor', id: 'from.id', name: 'from', emptyText: this.locale.tripPlanner.labels.from, label: '', cls: 'nudgeRight'});
        this.m_toForm   = new otp.core.ComboBox({layout: 'anchor', id: 'to.id',   name: 'to',   emptyText: this.locale.tripPlanner.labels.to, label: '',   cls: 'nudgeRight'});
        var rev  = new Ext.Button({
            tooltip:   this.locale.buttons.reverseMiniTip,
            id:        "form.reverse.id",
            iconCls:   "reverse-button",
            cls:      'formReverseButton', 
            hideLabel: true,
            scope:     this,
            tabIndex:  -1,
            handler : function(obj)
            {
                this.m_fromForm.reverse(this.m_toForm);
                otp.util.AnalyticsUtils.gaEvent(otp.util.AnalyticsUtils.TRIP_FORM_REVERSE);
            }
        });

        // change events will clear out the from/to coordinate forms (don't want coords from old places getting confused with new requests)
        this.m_fromForm.getComboBox().on({scope: this, focus  : this.clearFrom  });
        this.m_fromForm.getComboBox().on({scope: this, select : this.selectFrom });
        this.m_toForm.getComboBox().on(  {scope: this, focus  : this.clearTo    });
        this.m_toForm.getComboBox().on(  {scope: this, select : this.selectTo   });

        var inputPanel = {
            xtype:    'panel',
            border:    false,
            layout:    'column',
            items: [
                {
                    columnWidth: 0.90,
                    layout: 'form',
                    border: false,
                    items: [
                        this.m_fromForm.getComboBox(),
                        this.m_toForm.getComboBox()
                    ]
                }
                ,
                {
                    bodyStyle: 'padding-top:15px',
                    columnWidth: 0.07,
                    layout: 'anchor',
                    border: false,
                    items: [rev]
                }
            ]
        };

        return inputPanel;
    },


    /**
     * from & to form creation
     */
    makeDateTime : function()
    {
        this.m_date = new Ext.form.DateField({
            id:         'trip-date-form',
            fieldLabel: this.locale.tripPlanner.labels.date,
            name:       'date',
            format:     'm/d/Y',
            allowBlank: false,
            msgTarget:  'qtip',
            anchor:     "95%",
            value:      new Date().format('m/d/Y')
        });

        this.m_arriveByStore = otp.util.ExtUtils.makeStaticPullDownStore(this.locale.tripPlanner.arriveDepart);
        this.m_arriveByForm  = new Ext.form.ComboBox({
                id:             'trip-arrive-form',
                name:           'arriveBy',
                hiddenName:     'arriveBy',
                fieldLabel:     this.locale.tripPlanner.labels.when,
                store:          this.m_arriveByStore,
                value:          this.m_arriveByStore.getAt(0).get('opt'),
                displayField:   'text',
                valueField:     'opt',
                anchor:         this.FIELD_ANCHOR,
                mode:           'local',
                triggerAction:  'all',
                editable:       false,
                allowBlank:     false,
                lazyRender:     false,
                typeAhead:      true,
                forceSelection: true,
                selectOnFocus:  true
        });

        this.m_time = new Ext.ux.form.Spinner({
                id         : 'trip-time-form',
                fieldLabel : this.locale.tripPlanner.labels.when,
                accelerate : true,
                width      : 85,
                msgTarget  : 'qtip',
                value      : new Date().format('g:i a'),
                strategy   : new Ext.ux.form.Spinner.TimeStrategy({format:'g:i a'}),
                name       : 'time'
        });

        var timePanel = {
            xtype:    'panel',
            border:    false,
            layout:    'column',
            autoWidth: true,
            items: [
                {
                    columnWidth: 0.37,
                    layout: 'form',
                    border: false,
                    items: [this.m_arriveByForm]
                }
                ,
                {
                    columnWidth: 0.33,
                    layout: 'anchor',
                    border: false,
                    items: [this.m_date]
                }
                ,
                {
                    columnWidth: 0.30,
                    layout: 'anchor',
                    border: false,
                    //labelWidth: 5,
                    items: [this.m_time]
                }
            ]
        };

        return timePanel;
    },


    /**
     * makes the forms for things like walk distance and mode (bus / train / both) 
     */
    makeOptionForms : function()
    {
        this.m_maxWalkDistanceStore  = otp.util.ExtUtils.makeStaticPullDownStore(this.locale.tripPlanner.maxWalkDistance);
        this.m_optimizeStore = otp.util.ExtUtils.makeStaticPullDownStore(this.locale.tripPlanner.options);
        this.m_modeStore     = otp.util.ExtUtils.makeStaticPullDownStore(this.locale.tripPlanner.mode);
        this.m_wheelchairStore     = otp.util.ExtUtils.makeStaticPullDownStore(this.locale.tripPlanner.wheelchair);

        this.m_optimizeForm = new Ext.form.ComboBox({
            id:             'trip-optimize-form',
            name:           'optimize',
            hiddenName:     'optimize',
            fieldLabel:     this.locale.tripPlanner.labels.minimize,
            store:          this.m_optimizeStore,
            value:          this.m_optimizeStore.getAt(1).get('opt'),
            displayField:   'text',
            valueField:     'opt',
            anchor:         this.FIELD_ANCHOR,
            mode:           'local',
            triggerAction:  'all',
            editable:       false,
            allowBlank:     false,
            lazyRender:     false,
            typeAhead:      true,
            forceSelection: true,
            selectOnFocus:  true
        });

        this.m_maxWalkDistanceForm = new Ext.form.ComboBox({
            id:             'trip-walking-form',
            name:           'maxWalkDistance',
            hiddenName:     'maxWalkDistance',
            fieldLabel:     this.locale.tripPlanner.labels.maxWalkDistance,
            store:          this.m_maxWalkDistanceStore,
            value:          this.m_maxWalkDistanceStore.getAt(2).get('opt'),
            displayField:   'text',
            valueField:     'opt',
            anchor:         this.FIELD_ANCHOR,
            mode:           'local',
            triggerAction:  'all',
            editable:       false,
            allowBlank:     false,
            lazyRender:     false,
            typeAhead:      true,
            forceSelection: true,
            selectOnFocus:  true
        });

        this.m_modeForm  = new Ext.form.ComboBox({
            id:             'trip-mode-form',
            name:           'mode',
            hiddenName:     'mode',
            fieldLabel:     this.locale.tripPlanner.labels.mode,
            store:          this.m_modeStore,
            value:          this.m_modeStore.getAt(0).get('opt'),
            displayField:   'text',
            valueField:     'opt',
            anchor:         this.FIELD_ANCHOR,
            mode:           'local',
            triggerAction:  'all',
            editable:       false,
            allowBlank:     false,
            lazyRender:     false,
            typeAhead:      true,
            forceSelection: true,
            selectOnFocus:  true
        });

        this.m_wheelchairForm = new Ext.form.ComboBox({
            id:             'trip-wheelchair-form',
            name:           'wheelchair',
            hiddenName:     'wheelchair',
            fieldLabel:     this.locale.tripPlanner.labels.wheelchair,
            store:          this.m_wheelchairStore,
            value:          this.m_wheelchairStore.getAt(0).get('opt'),
            displayField:   'text',
            valueField:     'opt',
            anchor:         this.FIELD_ANCHOR,
            mode:           'local',
            triggerAction:  'all',
            editable:       false,
            allowBlank:     false,
            lazyRender:     false,
            typeAhead:      true,
            forceSelection: true,
            selectOnFocus:  true
        });

        return [this.m_optimizeForm, this.m_maxWalkDistanceForm, this.m_modeForm, this.m_wheelchairForm];
    },

    CLASS_NAME: "otp.planner.Forms"
};

otp.planner.Forms = new otp.Class(otp.planner.StaticForms);
