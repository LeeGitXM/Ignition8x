/**
 *   (c) 2020  ILS Automation. All rights reserved. 
 */
package com.ils.block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.math3.stat.descriptive.moment.GeometricMean;
import org.apache.commons.math3.stat.descriptive.moment.Kurtosis;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.SecondMoment;
import org.apache.commons.math3.stat.descriptive.moment.Skewness;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Min;
import org.apache.commons.math3.stat.descriptive.summary.Product;
import org.apache.commons.math3.stat.descriptive.summary.Sum;
import org.apache.commons.math3.stat.descriptive.summary.SumOfLogs;
import org.apache.commons.math3.stat.descriptive.summary.SumOfSquares;

import com.ils.block.annotation.ExecutableBlock;
import com.ils.blt.common.ProcessBlock;
import com.ils.blt.common.block.AnchorDirection;
import com.ils.blt.common.block.AnchorPrototype;
import com.ils.blt.common.block.BindingType;
import com.ils.blt.common.block.BlockConstants;
import com.ils.blt.common.block.BlockDescriptor;
import com.ils.blt.common.block.BlockProperty;
import com.ils.blt.common.block.BlockStyle;
import com.ils.blt.common.block.PropertyType;
import com.ils.blt.common.block.StatFunction;
import com.ils.blt.common.connection.ConnectionType;
import com.ils.blt.common.control.ExecutionController;
import com.ils.blt.common.notification.BlockPropertyChangeEvent;
import com.ils.blt.common.notification.IncomingNotification;
import com.ils.blt.common.notification.OutgoingNotification;
import com.ils.blt.common.serializable.SerializableBlockStateDescriptor;
import com.ils.common.FixedSizeQueue;
import com.ils.common.watchdog.TestAwareQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataQuality;

/**
 * This class computes a specified statistic on the last "n" readings.
 */
@ExecutableBlock
public class StatisticsSample extends AbstractProcessBlock implements ProcessBlock {
	private final String CLSS = "StatisticsSample";
	private final static int DEFAULT_BUFFER_SIZE = 1;
	
	private final FixedSizeQueue<QualifiedValue> queue;
	private int sampleSize = DEFAULT_BUFFER_SIZE;
	private boolean clearOnReset = false;
	private StatFunction function = StatFunction.RANGE;
	private BlockProperty valueProperty = null;  // The most recent statistical result
	
    private final GeometricMean gmeanfn = new GeometricMean();
    private final Kurtosis kurtfn = new Kurtosis();
	private final Max maxfn = new Max();
	private final Mean meanfn = new Mean();
    private final Median medianfn = new Median();
    private final Min minfn = new Min();
    private final Product prodfn = new Product();
    private final SecondMoment smfn = new SecondMoment();
    private final Skewness skewfn = new Skewness();
    private final StandardDeviation sdfn = new StandardDeviation();
    private final Sum sumfn= new Sum();
    private final SumOfLogs solfn = new SumOfLogs();
    private final SumOfSquares sosfn = new SumOfSquares();
    private final Variance varfn = new Variance();
	
	/**
	 * Constructor: The no-arg constructor is used when creating a prototype for use in the palette.
	 */
	public StatisticsSample() {
		queue = new FixedSizeQueue<QualifiedValue>(DEFAULT_BUFFER_SIZE);
		initialize();
		initializePrototype();
	}
	
	/**
	 * Constructor. Custom properties are limit, standardDeviation
	 * 
	 * @param ec execution controller for handling block output
	 * @param parent universally unique Id identifying the parent of this block
	 * @param block universally unique Id for the block
	 */
	public StatisticsSample(ExecutionController ec,UUID parent,UUID block) {
		super(ec,parent,block);
		queue = new FixedSizeQueue<QualifiedValue>(DEFAULT_BUFFER_SIZE);
		initialize();
	}
	
	/**
	 * Add properties that are new for this class.
	 * Populate them with default values.
	 */
	private void initialize() {	
		setName("StatisticalSample");

		BlockProperty clearProperty = new BlockProperty(BlockConstants.BLOCK_PROPERTY_CLEAR_ON_RESET,new Boolean(clearOnReset),PropertyType.BOOLEAN,true);
		setProperty(BlockConstants.BLOCK_PROPERTY_CLEAR_ON_RESET, clearProperty);
		BlockProperty sizeProperty = new BlockProperty(BlockConstants.BLOCK_PROPERTY_SAMPLE_SIZE,new Integer(sampleSize),PropertyType.INTEGER,true);
		setProperty(BlockConstants.BLOCK_PROPERTY_SAMPLE_SIZE, sizeProperty);
		BlockProperty statProperty = new BlockProperty(BlockConstants.BLOCK_PROPERTY_STATISTICS_FUNCTION,function,PropertyType.STATISTICS,true);
		setProperty(BlockConstants.BLOCK_PROPERTY_STATISTICS_FUNCTION, statProperty);
		valueProperty = new BlockProperty(BlockConstants.BLOCK_PROPERTY_VALUE,new Double(Double.NaN),PropertyType.DOUBLE,false);
		valueProperty.setBindingType(BindingType.ENGINE);
		setProperty(BlockConstants.BLOCK_PROPERTY_VALUE, valueProperty);
		
		
		// Define a single input.
		AnchorPrototype input = new AnchorPrototype(BlockConstants.IN_PORT_NAME,AnchorDirection.INCOMING,ConnectionType.DATA);
		input.setIsMultiple(false);
		anchors.add(input);

		// Define the main output, a truth value.
		AnchorPrototype output = new AnchorPrototype(BlockConstants.OUT_PORT_NAME,AnchorDirection.OUTGOING,ConnectionType.DATA);
		anchors.add(output);
	}
	
	@Override
	public void reset() {
		super.reset();
		if( clearOnReset) {
			queue.clear();
			valueProperty.setValue(new Double(Double.NaN));
		}
	}
	
	/**
	 * A new value has arrived. Add it to the queue.
	 * @param vcn incoming new value.
	 */
	@Override
	public void acceptValue(IncomingNotification vcn) {
		super.acceptValue(vcn);
		String port = vcn.getConnection().getDownstreamPortName();
		if( port.equals(BlockConstants.IN_PORT_NAME) ) {
			QualifiedValue qv = vcn.getValue();
			log.debugf("%s.acceptValue: Received %s",getName(),qv.getValue().toString());
			if( qv.getQuality().isGood() ) {
				queue.add(qv);
				
			}
			else {
				// Bad value received. Set value property to Nan. Clear the buffer.
				if( !isLocked() ) {
					valueProperty.setValue(Double.NaN);
					controller.sendPropertyNotification(getBlockId().toString(), BlockConstants.BLOCK_PROPERTY_VALUE,qv);
				}
				queue.clear();
			}
			evaluate();
		}
	}
	/**
	 * Evaluate the buffer and place the result on the output.
	 * The buffer should have only good values.
	 */
	@Override
	public void evaluate() {
		if( !isLocked() && queue.size() >= sampleSize) {
			while(queue.size() > sampleSize ) {  
				queue.remove();
			}
			double result = computeStatistic();
			// Result gets good quality and a new timestamp
			lastValue = new TestAwareQualifiedValue(timer,new Double(result),DataQuality.GOOD_DATA);
			OutgoingNotification nvn = new OutgoingNotification(this,BlockConstants.OUT_PORT_NAME,lastValue);
			controller.acceptCompletionNotification(nvn);
			notifyOfStatus(lastValue);

			valueProperty.setValue(result);
			controller.sendPropertyNotification(getBlockId().toString(), BlockConstants.BLOCK_PROPERTY_VALUE,lastValue);
		}
	}
	/**
	 * Send status update notification for our last latest state.
	 */
	@Override
	public void notifyOfStatus() {
		QualifiedValue qv = new TestAwareQualifiedValue(timer,valueProperty.getValue());
		notifyOfStatus(qv);
		
	}
	private void notifyOfStatus(QualifiedValue qv) {
		controller.sendPropertyNotification(getBlockId().toString(), BlockConstants.BLOCK_PROPERTY_VALUE,qv);
		controller.sendConnectionNotification(getBlockId().toString(), BlockConstants.OUT_PORT_NAME, qv);
	}

	/**
	 * Handle a changes to the various attributes.
	 */
	@Override
	public void propertyChange(BlockPropertyChangeEvent event) {
		super.propertyChange(event);
		String propertyName = event.getPropertyName();
		if(propertyName.equalsIgnoreCase(BlockConstants.BLOCK_PROPERTY_CLEAR_ON_RESET)) {
			clearOnReset = fcns.coerceToBoolean(event.getNewValue().toString());
		}
		else if(propertyName.equalsIgnoreCase(BlockConstants.BLOCK_PROPERTY_SAMPLE_SIZE) ) {
			// Trigger an evaluation
			try {
				int val = Integer.parseInt(event.getNewValue().toString());
				if( val>0 ) {
					sampleSize = val;
					queue.setBufferSize(sampleSize);
					evaluate();
				}
			}
			catch(NumberFormatException nfe) {
				log.warnf("%s: propertyChange Unable to convert sample size to an integer (%s)",getName(),nfe.getLocalizedMessage());
			}
		}
		else if( propertyName.equalsIgnoreCase(BlockConstants.BLOCK_PROPERTY_STATISTICS_FUNCTION)) {
			try {
				function = StatFunction.valueOf(event.getNewValue().toString());
				evaluate();
			}
			catch(IllegalArgumentException nfe) {
				log.warnf("%s: propertyChange Unable to convert %s to a function (%s)",CLSS,event.getNewValue().toString(),nfe.getLocalizedMessage());
			}
		}
		// Activity buffer size handled in superior method
		else if( !propertyName.equals(BlockConstants.BLOCK_PROPERTY_ACTIVITY_BUFFER_SIZE) ){
			log.warnf("%s.propertyChange:Unrecognized property (%s)",getName(),propertyName);
		}
	}

	/**
	 * @return a block-specific description of internal statue
	 */
	@Override
	public SerializableBlockStateDescriptor getInternalStatus() {
		SerializableBlockStateDescriptor descriptor = super.getInternalStatus();
		List<Map<String,String>> buffer = descriptor.getBuffer();
		for( QualifiedValue qv:queue) {
			Map<String,String> qvMap = new HashMap<>();
			qvMap.put("Value", qv.getValue().toString());
			qvMap.put("Quality", qv.getQuality().toString());
			qvMap.put("Timestamp", qv.getTimestamp().toString());
			buffer.add(qvMap);
		}

		return descriptor;
	}
	
	/**
	 * Augment the palette prototype for this block class.
	 */
	private void initializePrototype() {
		prototype.setPaletteIconPath("Block/icons/palette/statisticsn.png");
		prototype.setPaletteLabel("Statistics(n)");
		prototype.setTooltipText("Compute the selected statistic over the n most recent results.");
		prototype.setTabName(BlockConstants.PALETTE_TAB_STATISTICS);
		
		BlockDescriptor desc = prototype.getBlockDescriptor();
		desc.setBlockClass(getClass().getCanonicalName());
		desc.setEmbeddedIcon("Block/icons/embedded/statisticsn.png");
		desc.setPreferredHeight(70);
		desc.setPreferredWidth(70);
		desc.setStyle(BlockStyle.DIAMOND);
		desc.setBackground(BlockConstants.BLOCK_BACKGROUND_LIGHT_GRAY);
	}
	/**
	 * Compute the statistic, presumably because of a new input.
	 */
	private double computeStatistic() {
		double result = Double.NaN;
		int size = queue.size();
		double[] values = new double[size];
		int index = 0;
		for( QualifiedValue qv:queue) {
			values[index] = fcns.coerceToDouble(qv.getValue());
			index++;
		}
		switch(function) {
			case GEOMETRIC_MEAN:result = gmeanfn.evaluate(values); break;
			case KURTOSIS:result = kurtfn.evaluate(values); break;
			case MAXIMUM: result = maxfn.evaluate(values); break;
			case MEAN: 	  result = meanfn.evaluate(values); break;
			case MEDIAN:  result = medianfn.evaluate(values); break;
			case MINIMUM: result = minfn.evaluate(values); break;
			case PRODUCT: result = prodfn.evaluate(values); break;
			case RANGE:   result = maxfn.evaluate(values)-minfn.evaluate(values); break;      
			case SECOND_MOMENT:     result = smfn.evaluate(values); break; 
			case SKEW:    result = skewfn.evaluate(values); break; 
			case STANDARD_DEVIATION: result = sdfn.evaluate(values); break;
			case SUM:     result = sumfn.evaluate(values); break; 
			case SUM_OF_LOGS:    result = solfn.evaluate(values); break;
			case SUM_OF_SQUARES: result = sosfn.evaluate(values); break; 
			case VARIANCE: result = varfn.evaluate(values); break;
		}
		return result;	
	}
	
}