/**
 *   (c) 2013  ILS Automation. All rights reserved. 
 */
package com.ils.blt.common.control;

import com.ils.blt.common.block.TruthValue;
import com.ils.blt.common.connection.Connection;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.BasicQuality;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.model.values.Quality;

/**
 * This class is used to hold value change information coming from the "engine" representing 
 * a new value arriving at an input port. Depending on the type of connector, the class of the
 * value object is one of the following:
 *    - Signal
 *    - TruthValue
 *    - QualifiedValue
 *    - String
 * 
 * This is a property container with no behavior.
 */
public class IncomingNotification {
	private final Connection connection;
	private final Object value;
	
	/**
	 * Constructor. Value is expressed as an object.
	 * 
	 * @param cxn the connection that is the source or target of the value.
	 *              Usage depends on the direction of the information exchange.
	 * @param val the new value
	 */
	public IncomingNotification(Connection cxn, Object val)  {	
		this.connection = cxn;
		this.value = val;
	}
	
	public Connection getConnection()    { return connection; }
	public Object getValue()             { return value; }
	/**
	 * Convert the value to a qualified value. If null, generate
	 * a qualified value of BAD quality.
	 * @return the value cast to a QualifiedValue
	 */
	public QualifiedValue getValueAsQualifiedValue() {
		QualifiedValue result = null;
		if( value!=null ) {
			if( value instanceof QualifiedValue ) result = (QualifiedValue)value;
			else if( value instanceof TruthValue ) {
				result = new BasicQualifiedValue( ((TruthValue)value).name());
			}
			else if( value instanceof Double ||
					 value instanceof Integer||
					 value instanceof String ||
					 value instanceof Boolean) {
				result = new BasicQualifiedValue( value);
			}
			else{
				result = new BasicQualifiedValue(value,new BasicQuality("unrecognized data type",Quality.Level.Bad));
			}
		}
		else {
			result = new BasicQualifiedValue("",new BasicQuality("null value",Quality.Level.Bad));
		}
		return result; 
	}
	/**
	 * Convert the value to a signal. If null, return
	 * an empty signal.
	 * @return the value cast to a signal
	 */
	public Signal getValueAsSignal() {
		Signal result = null;
		if( value instanceof Signal ) result = (Signal)value;
		else result = new Signal("","","");
		return result; 
	}
	/**
	 * Convert the value to a truth value. If null, return
	 * a UNKNOWN state.
	 * @return the value cast to a TruthValue
	 */
	public TruthValue getValueAsTruthValue() {
		TruthValue result = TruthValue.UNKNOWN;
		if( value!=null) {
			if( value instanceof TruthValue ) result = (TruthValue)value;
			else if( value instanceof QualifiedValue ) {
				if( ((QualifiedValue)value).getValue()!=null ) {
					String val = ((QualifiedValue)value).getValue().toString().toUpperCase();
					try {
						result = TruthValue.valueOf(val);
					}
					catch(IllegalArgumentException iae) {}
				}
			}
			else {
				String val = value.toString().toUpperCase();
				try {
					result = TruthValue.valueOf(val);
				}
				catch(IllegalArgumentException iae) {}
			}
		}
		return result; 
	}
}
