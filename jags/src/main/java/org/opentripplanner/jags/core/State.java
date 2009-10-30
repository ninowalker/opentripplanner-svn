package org.opentripplanner.jags.core;
import java.util.Date;

public class State {
	
  private long _time;

	public boolean justTransfered = false;
	
	public State() {
		this(System.currentTimeMillis());
	}
	
	public State(long time) {
		_time = time;
	}
	
	public long getTime() {
	  return _time;
	}

  public void incrementTimeInSeconds(int numOfSeconds) {
    _time += numOfSeconds * 1000;
  }
	
    public State clone() {
        State ret = new State();
        ret._time = _time;
        return ret;
    }
    
    public String toString() {
    	return "<State "+new Date(_time)+">";
    }

   
}