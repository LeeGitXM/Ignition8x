/**
 *   (c) 2020  ILS Automation. All rights reserved. 
 */
package com.ils.blt.common.block;


/**
 * This enumeration class presents names of the statistical functions
 * that can be used by the three statistical blocks.
 */
public enum StatFunction
{	GEOMETRIC_MEAN, 
	KURTOSIS,
	MAXIMUM,
	MEAN,
	MEDIAN,
	MINIMUM,
	PRODUCT, 
	RANGE,      
	SECOND_MOMENT, 
	SKEW, 
	STANDARD_DEVIATION, 
	SUM, 
	SUM_OF_LOGS, 
	SUM_OF_SQUARES, 
	VARIANCE
	;

	/**
	 * @return  a comma-separated list of function names in a single String.
	 */
	public static String names() {
		StringBuffer names = new StringBuffer();
		for (StatFunction type : StatFunction.values())
		{
			names.append(type.name()+", ");
		}
		return names.substring(0, names.length()-2);
	}
}
