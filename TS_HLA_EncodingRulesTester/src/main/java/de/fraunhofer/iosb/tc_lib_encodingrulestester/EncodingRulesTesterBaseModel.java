/*
Copyright 2017, Johannes Mulder (Fraunhofer IOSB)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package de.fraunhofer.iosb.tc_lib_encodingrulestester;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import de.fraunhofer.iosb.tc_lib.IVCT_BaseModel;
import de.fraunhofer.iosb.tc_lib.IVCT_RTIambassador;
import de.fraunhofer.iosb.tc_lib.IVCT_TcParam;
import de.fraunhofer.iosb.tc_lib.TcInconclusive;
import hla.rti1516e.AttributeHandle;
import hla.rti1516e.AttributeHandleSet;
import hla.rti1516e.AttributeHandleValueMap;
import hla.rti1516e.FederateAmbassador;
import hla.rti1516e.FederateHandle;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.MessageRetractionHandle;
import hla.rti1516e.ObjectClassHandle;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.OrderType;
import hla.rti1516e.ParameterHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.TransportationTypeHandle;
import hla.rti1516e.exceptions.AttributeNotDefined;
import hla.rti1516e.exceptions.FederateHandleNotKnown;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.FederateServiceInvocationsAreBeingReportedViaMOM;
import hla.rti1516e.exceptions.InteractionClassNotDefined;
import hla.rti1516e.exceptions.InteractionParameterNotDefined;
import hla.rti1516e.exceptions.InvalidAttributeHandle;
import hla.rti1516e.exceptions.InvalidFederateHandle;
import hla.rti1516e.exceptions.InvalidInteractionClassHandle;
import hla.rti1516e.exceptions.InvalidObjectClassHandle;
import hla.rti1516e.exceptions.InvalidParameterHandle;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.ObjectClassNotDefined;
import hla.rti1516e.exceptions.ObjectInstanceNotKnown;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;

/**
 * This class holds common result information for interaction
 * parameters and object attributes
 *
 * @author mul (Fraunhofer IOSB)
 */
class ResultInfo {
	private int correctCount = 0;
	private int incorrectCount = 0;
	private String text = null;
	ResultInfo() {
		this.correctCount = 0;
		this.incorrectCount = 0;
	}

	/**
	 * Stores the result data
	 *
	 * @param correct true is PASSED, false is FAILED
	 * @param text the result verdict text
	 */
	void addInfo(final boolean correct, final String text) {
		// Manage the count of correct/incorrect
		if (correct) {
			this.correctCount += 1;
		} else {
			this.incorrectCount += 1;
		}
		// Store first text message, only overwrite if incorrect
		if (this.text == null) {
			this.text = text;
		} else {
			if (correct == false) {
				this.text = text;
			}
		}
	}

	/**
	 * Gets the number of correct encodings parsed
	 *
	 * @return the number of correct encodings
	 */
	int getCorrectCount() {
		return this.correctCount;
	}
	
	/**
	 * Gets the number of incorrect encodings parsed
	 *
	 * @return the number of incorrect encodings
	 */
	int getIncorrectCount() {
		return this.incorrectCount;
	}

	/**
	 * Get the text associated with the result verdict
	 *
	 * @return the text
	 */
	String getText() {
		return this.text;
	}
}

/**
 * This class adds attribute specific result information
 *
 * @author mul (Fraunhofer IOSB)
 */
class ResultInfoAttribute extends ResultInfo{
	private FederateHandle federateHandle;
	private String federateName;
	private boolean gotFederateName = false;
	void addOwner() {
	}

	/**
	 * Store the federate handle
	 *
	 * @param federateHandle
	 */
	void addFederateHandle(final FederateHandle federateHandle) {
		this.federateHandle = federateHandle;
	}

	/**
	 * Store the federate name
	 *
	 * @param federateName
	 */
	void addFederateName(final String federateName) {
		this.federateName = federateName;
		this.gotFederateName = true;
	}

	/**
	 * Get the federate handle
	 *
	 * @return the federate handle
	 */
	FederateHandle getFederateHandle() {
		return this.federateHandle;
	}

	/**
	 * Get the federate name
	 *
	 * @return the federate name
	 */
	String getFederateName() {
		return this.federateName;
	}

	/**
	 * Check if the federate name is known
	 *
	 * @return whether the federate name is known
	 */
	boolean haveFederateName() {
		return this.gotFederateName;
	}
}

/**
 * @author mul (Fraunhofer IOSB)
 */
public class EncodingRulesTesterBaseModel extends IVCT_BaseModel {
	private boolean                                        errorOccurred = false;
	private String                                         errorText = new String("Encoding error found");

	private int correct = 0;
	private int incorrect = 0;
    private IVCT_RTIambassador                             ivct_rti;
    private IVCT_TcParam ivct_TcParam;
    private Logger                                         logger;
    private Set<InteractionClassHandle> interactionClassHandleSet = new HashSet<InteractionClassHandle>();
    private Map<ParameterHandle, String> parameterHandleDataTypeMap = new HashMap<ParameterHandle, String>();
    private Map<ObjectClassHandle, AttributeHandleSet> objectClassAttributeHandleMap = new HashMap<ObjectClassHandle, AttributeHandleSet>();
	private final Map<InteractionClassHandle, Map<ParameterHandle, ResultInfo>> interactionParameterResultsmap = new HashMap<InteractionClassHandle, Map<ParameterHandle, ResultInfo>>();
    private Map<InteractionClassHandle, Boolean> interactionClassChecked = new HashMap<InteractionClassHandle, Boolean>();
	private final Map<ObjectInstanceHandle, Map<AttributeHandle, ResultInfoAttribute>> objectAttributeResultsmap = new HashMap<ObjectInstanceHandle, Map<AttributeHandle, ResultInfoAttribute>>();
	private Map<AttributeHandle, String> attributeHandleDataTypeMap = new HashMap<AttributeHandle, String>();
	private Map<AttributeHandle, Boolean> attributeHandleChecked = new HashMap<AttributeHandle, Boolean>();
	// FOM/SOM data types
	private HlaDataTypes hlaDataTypes = new HlaDataTypes();

    /**
     * @param logger reference to a logger
     * @param ivct_rti reference to the RTI ambassador
     * @param ivct_TcParam ivct_TcParam
     */
    public EncodingRulesTesterBaseModel(final Logger logger, final IVCT_RTIambassador ivct_rti, final IVCT_TcParam ivct_TcParam) {
        super(ivct_rti, logger, ivct_TcParam);
        this.logger = logger;
        this.ivct_rti = ivct_rti;
        this.ivct_TcParam = ivct_TcParam;
    }

    /**
     * @return returns whether all interactions have been checked
     */
    public boolean getWhetherAllInteractionsChecked() {
		for (Map.Entry<InteractionClassHandle, Boolean> entry : this.interactionClassChecked.entrySet()) {
			if (Boolean.FALSE.equals(entry.getValue())) {
				return false;
			}
		}
    	return true;
    }

    /**
     * @return returns whether all attribute have been checked detected
     */
    public boolean getWhetherAllAttibutesChecked() {
		for (Map.Entry<AttributeHandle, Boolean> entry : this.attributeHandleChecked.entrySet()) {
			if (Boolean.FALSE.equals(entry.getValue())) {
				return false;
			}
		}
    	return true;
    }

    /**
     * @return returns whether an error was detected
     */
    public boolean getErrorOccurred() {
    	return this.errorOccurred;
    }

    /**
     * @return returns the error text
     */
    public String getErrorText() {
    	return this.errorText;
    }

    /**
     * @return returns the failed count
     */
    public int getCorrect() {
    	return this.correct;
    }

    /**
     * @return returns the inconclusive count
     */
    public int getIncorrect() {
    	return this.incorrect;
    }

    /**
     * @param sleepTime time to sleep
     * @return true means problem, false is ok
     */
    public boolean sleepFor(final long sleepTime) {
        try {
            Thread.sleep(sleepTime);
        }
        catch (final InterruptedException ex) {
            return true;
        }

        return false;
    }


    /**
     * @return true means error, false means correct
     * @throws TcInconclusive for some other errors
     */
    public boolean init() throws TcInconclusive {
    	// Read SOM files and process them.
    	processSOM();
    	Boolean b = new Boolean(false);

        // Subscribe interactions
    	this.logger.trace("EncodingRulesTesterBaseModel.init: subscribe interactions");
		try {
			for (InteractionClassHandle ich : this.interactionClassHandleSet) {
				this.logger.trace("EncodingRulesTesterBaseModel.init: subscribe " + this.ivct_rti.getInteractionClassName(ich));
				this.ivct_rti.subscribeInteractionClass(ich);
				interactionClassChecked.put(ich, b);
			}
		}
		catch (FederateServiceInvocationsAreBeingReportedViaMOM | InteractionClassNotDefined | SaveInProgress | RestoreInProgress | FederateNotExecutionMember | NotConnected | RTIinternalError ex1) {
			this.logger.error("EncodingRulesTesterBaseModel.init: cannot subcribe interaction");
			ex1.printStackTrace();
            return true;
		} catch (InvalidInteractionClassHandle e) {
			e.printStackTrace();
            return true;
		}

        // Subscribe object attributes
		this.logger.trace("EncodingRulesTesterBaseModel.init: subscribe object attributes");
		try {
			for (Map.Entry<ObjectClassHandle, AttributeHandleSet> entry : this.objectClassAttributeHandleMap.entrySet()) {
				this.logger.trace("EncodingRulesTesterBaseModel.init: subscribe " + this.ivct_rti.getObjectClassName(entry.getKey()));
				this.ivct_rti.subscribeObjectClassAttributes(entry.getKey(), entry.getValue());
				for (AttributeHandle ah: entry.getValue()) {
		            this.attributeHandleChecked.put(ah, b);
				}
			}
		}
		catch (AttributeNotDefined | ObjectClassNotDefined | SaveInProgress | RestoreInProgress | FederateNotExecutionMember | NotConnected | RTIinternalError | InvalidObjectClassHandle ex) {
			this.logger.error("EncodingRulesTesterBaseModel.init: cannot subscribe object attributes " + ex);
			return true;
		}

        return false;
    }

    /**
     * Read the SOM files and build up an internal data cache to use within this
     * library.
     * 
     * @return true means error occurred
     * @throws TcInconclusive for some other errors
     */
    private boolean processSOM() throws TcInconclusive {
        URL[] somUrls = this.ivct_TcParam.getUrls();

		try {
			DataTreeBuilder dataTreeBuilder = new DataTreeBuilder(this.ivct_rti, this.hlaDataTypes, this.interactionClassHandleSet, this.parameterHandleDataTypeMap, this.objectClassAttributeHandleMap, this.attributeHandleDataTypeMap);
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			
			for (int i = 0; i < somUrls.length; i++) {
				Document document = builder.parse(somUrls[i].toString());
				Element elem = document.getDocumentElement();
				if (dataTreeBuilder.buildData(elem)) {
					return true;
				}
			}
		}
		catch (FactoryConfigurationError e) {
			this.logger.error("EncodingRulesTesterBaseModel.processSOM: unable to get a document builder factory");
            return true;
		} 
		catch (ParserConfigurationException e) {
			this.logger.error("EncodingRulesTesterBaseModel.processSOM: unable to configure parser");
            return true;
		}
		catch (SAXException e) {
			this.logger.error("EncodingRulesTesterBaseModel.processSOM: parsing error");
            return true;
		} 
		catch (IOException e) {
			this.logger.error("EncodingRulesTesterBaseModel.processSOM: i/o error");
            return true;
		}
		
		return false;
    }

    /**
     * @param in byte value to be displayed as string
     * @return the string value corresponding to the byte value
     */
    private static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    /**
     * A function to deal with all the possibilities of adding to a map within a map.
     * 
     * @param theInteraction the HLA interaction handle
     * @param theParameter the HLA parameter handle
     * @param b whether the test was positive
     * @param text the text message
     */
    private void addParameterResult(final InteractionClassHandle theInteraction, final ParameterHandle theParameter, final boolean b, final String text) {
    	this.logger.trace("EncodingRulesTesterBaseModel.addParameterResult: enter");
    	/*
    	 * Check if interaction already managed
    	 */
    	Map<ParameterHandle, ResultInfo> parameterResultMap = this.interactionParameterResultsmap.get(theInteraction);
    	if (parameterResultMap == null) {
        	this.logger.trace("EncodingRulesTesterBaseModel.addParameterResult: A");
    		// Interaction not managed - create all elements
        	ResultInfo resultInfo = new ResultInfo();
        	resultInfo.addInfo(b, text);
        	Map<ParameterHandle, ResultInfo> tmpParameterResultMap = new HashMap<ParameterHandle, ResultInfo>();
        	tmpParameterResultMap.put(theParameter, resultInfo);
        	this.interactionParameterResultsmap.put(theInteraction, tmpParameterResultMap);
        	this.logger.trace("EncodingRulesTesterBaseModel.addParameterResult: A size " + this.interactionParameterResultsmap.size());
    	} else {
        	this.logger.trace("EncodingRulesTesterBaseModel.addParameterResult: B");
    		// Interaction is already managed
    		ResultInfo tmpResultInfo = parameterResultMap.get(theParameter);
    		/*
    		 * Check if parameter is already managed
    		 */
    		if (tmpResultInfo == null) {
            	this.logger.trace("EncodingRulesTesterBaseModel.addParameterResult: C");
    			// Parameter not managed - create result info
            	ResultInfo resultInfo = new ResultInfo();
            	resultInfo.addInfo(b, text);
            	parameterResultMap.put(theParameter, resultInfo);
    		} else {
            	this.logger.trace("EncodingRulesTesterBaseModel.addParameterResult: D");
    			// Parameter is already managed - update.
    			tmpResultInfo.addInfo(b, text);
    		}
    	}
    	this.logger.trace("EncodingRulesTesterBaseModel.addParameterResult: leave");
    }

    /**
     * Print the result data for all interactions parameters
     */
    public void printParameterResults() {
    	final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n\nInteraction Parameter Summary \n");
        if (this.interactionParameterResultsmap.isEmpty()) {
            stringBuilder.append("- No Results -\n");
        	this.logger.info(stringBuilder.toString());
            return;
        }
    	String interactionClassName = null;
    	for (Map.Entry<InteractionClassHandle, Map<ParameterHandle, ResultInfo>> entryInteraction : this.interactionParameterResultsmap.entrySet()) {
    		try {
				interactionClassName = this.ivct_rti.getInteractionClassName(entryInteraction.getKey());
			} catch (InvalidInteractionClassHandle | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
                this.logger.error("EncodingRulesTesterBaseModel.printParameterResults: " + e);
				continue;
			}
    		String parameterName = null;
    		for (Map.Entry<ParameterHandle, ResultInfo> entryParameter : entryInteraction.getValue().entrySet()) {
    			try {
					parameterName = this.ivct_rti.getParameterName(entryInteraction.getKey(), entryParameter.getKey());
				} catch (InteractionParameterNotDefined | InvalidParameterHandle | InvalidInteractionClassHandle
						| FederateNotExecutionMember | NotConnected | RTIinternalError e) {
                    this.logger.error("EncodingRulesTesterBaseModel.printParameterResults: " + e);
				}
    			stringBuilder.append("INTERACTION: " + interactionClassName + " PARAMETER: " + parameterName + " CORRECT: " + entryParameter.getValue().getCorrectCount() + " INCORRECT: " + entryParameter.getValue().getIncorrectCount() + " TEXT: " + entryParameter.getValue().getText() + "\n");
    		}
    	}
    	this.logger.info(stringBuilder.toString());
    }

    /**
     * @param interactionClass the interaction class
     * @param parameterHandle the parameter handle
     * @param b byte field containing attribute data
     * @param errorBool whether to use logger error (true) or debug (false)
     */
    private void displayReceiveParameterValuesMessage(final InteractionClassHandle interactionClass, final ParameterHandle parameterHandle, final byte b[], final boolean errorBool) {
        String interactionName = null;
        String parameterName = null;
        try {
            interactionName = this.ivct_rti.getInteractionClassName(interactionClass);
            parameterName = this.ivct_rti.getParameterName(interactionClass, parameterHandle);
        } catch (InvalidInteractionClassHandle | FederateNotExecutionMember | NotConnected | RTIinternalError | InteractionParameterNotDefined | InvalidParameterHandle e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }
        String sNames = new String("Interaction: " + interactionName + " Parameter: " + parameterName);
        String sIDs = new String("Interaction class handle: " + interactionClass + " Parameter handle: " + parameterHandle);
        String sBytes = new String("Parameter value bytes: " + bytesToHex(b));
        if (errorBool) {
            this.logger.error(sNames);
            this.logger.error(sIDs);
            this.logger.error(sBytes);
            this.logger.error("");
        } else {
            this.logger.debug(sNames);
            this.logger.debug(sIDs);
            this.logger.debug(sBytes);
            this.logger.debug("");
        }
    }

    /**
     * @param interactionClass specify the interaction class
     * @param theParameters specify the parameter handles and values
     */
    private void doReceiveInteraction(final InteractionClassHandle interactionClass, final ParameterHandleValueMap theParameters) {
        this.logger.trace("EncodingRulesTesterBaseModel.doReceiveInteraction: enter");

        Boolean bool =true;
        this.interactionClassChecked.put(interactionClass, bool);

        for (Map.Entry<ParameterHandle, byte[]> entry : theParameters.entrySet()) {
            this.logger.trace("EncodingRulesTesterBaseModel.doReceiveInteraction:  GOT parameter " + entry.getKey());
            this.logger.trace("EncodingRulesTesterBaseModel.doReceiveInteraction: GOT receiveInteraction " + this.parameterHandleDataTypeMap.get(entry.getKey()));
            HlaDataType hdt = this.hlaDataTypes.dataTypeMap.get(this.parameterHandleDataTypeMap.get(entry.getKey()));
            byte b[] = theParameters.get(entry.getKey());
            this.logger.trace("EncodingRulesTesterBaseModel.doReceiveInteraction: length " + b.length);
//            for (int i = 0; i < b.length; i++) {
//                this.logger.trace("EncodingRulesTesterBaseModel.doReceiveInteraction: byte " + b[i]);
//            }
            try {
            	int calculatedLength = hdt.testBuffer(entry.getValue(), 0, this.hlaDataTypes);
				if (calculatedLength != entry.getValue().length) {
					String error = "TEST BUFFER INCORRECT: overall length caculation: " + calculatedLength + " Buffer length: " + entry.getValue().length;
					this.logger.error(error);
		            this.errorOccurred = true;
		            addParameterResult(interactionClass, entry.getKey(), false, error);
		            displayReceiveParameterValuesMessage(interactionClass, entry.getKey(), b, true);
		            this.incorrect += 1;
				} else {
					String ok = "TEST BUFFER CORRECT";
					this.logger.trace(ok);
					addParameterResult(interactionClass, entry.getKey(), true, ok);
		            displayReceiveParameterValuesMessage(interactionClass, entry.getKey(), b, false);
					this.correct += 1;
				}
			} catch (EncodingRulesException e) {
				String error = "TEST BUFFER INCORRECT: " + e.getMessage();
				this.logger.error(error);
	            this.errorOccurred = true;
				addParameterResult(interactionClass, entry.getKey(), false, error);
	            displayReceiveParameterValuesMessage(interactionClass, entry.getKey(), b, true);
				this.incorrect += 1;
			}
        }
        this.logger.trace("EncodingRulesTesterBaseModel.doReceiveInteraction: leave");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveInteraction(final InteractionClassHandle interactionClass, final ParameterHandleValueMap theParameters, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        this.doReceiveInteraction(interactionClass, theParameters);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveInteraction(final InteractionClassHandle interactionClass, final ParameterHandleValueMap theParameters, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final LogicalTime theTime, final OrderType receivedOrdering, final SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        this.doReceiveInteraction(interactionClass, theParameters);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveInteraction(final InteractionClassHandle interactionClass, final ParameterHandleValueMap theParameters, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final LogicalTime theTime, final OrderType receivedOrdering, final MessageRetractionHandle retractionHandle, final SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        this.doReceiveInteraction(interactionClass, theParameters);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void discoverObjectInstance(final ObjectInstanceHandle theObject, final ObjectClassHandle theObjectClass, final String objectName) throws FederateInternalError {

    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void removeObjectInstance(final ObjectInstanceHandle theObject, final byte[] userSuppliedTag, final OrderType sentOrdering, final FederateAmbassador.SupplementalRemoveInfo removeInfo) {

    }

    /**
     * A function to deal with all the possibilities of adding to a map within a map.
     * 
     * @param theObject the HLA object handle
     * @param theAttribute the HLA attribute handle
     * @param b whether the test was positive
     * @param text the text message
     */
    private void addAttributeResult(final ObjectInstanceHandle theObject, final AttributeHandle theAttribute, final boolean b, final String text) {
    	this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: enter");
        ResultInfoAttribute tmpResultInfo;

    	/*
    	 * Check if object already managed
    	 */
        Map<AttributeHandle, ResultInfoAttribute> attributeResultMap = this.objectAttributeResultsmap.get(theObject);
    	if (attributeResultMap == null) {
        	this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: A");
    		// Object not managed - create all elements
            tmpResultInfo = new ResultInfoAttribute();
            tmpResultInfo.addInfo(b, text);
            Map<AttributeHandle, ResultInfoAttribute> tmpAttributeResultMap = new HashMap<AttributeHandle, ResultInfoAttribute>();
            tmpAttributeResultMap.put(theAttribute, tmpResultInfo);
            this.objectAttributeResultsmap.put(theObject, tmpAttributeResultMap);
        	this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: A size " + this.objectAttributeResultsmap.size());
    	} else {
        	this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: B");
    		// Object is already managed
            tmpResultInfo = attributeResultMap.get(theAttribute);
    		/*
    		 * Check if attribute is already managed
    		 */
    		if (tmpResultInfo == null) {
            	this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: C");
    			// Attribute not managed - create result info
                tmpResultInfo = new ResultInfoAttribute();
                tmpResultInfo.addInfo(b, text);
                attributeResultMap.put(theAttribute, tmpResultInfo);
    		} else {
            	this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: D");
    			// Attribute is already managed - update.
    			tmpResultInfo.addInfo(b, text);
    		}
    	}
        if (b == false) {
            try {
                this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: AA");
                if (tmpResultInfo.haveFederateName() == false) {
                    this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: AAA");
                    this.ivct_rti.queryAttributeOwnership(theObject, theAttribute);
                    this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: BBB");
                }
				this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: BB");
            } catch (AttributeNotDefined | ObjectInstanceNotKnown | SaveInProgress | RestoreInProgress
                    | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
                this.logger.error("EncodingRulesTesterBaseModel.addAttributeResult: " + e);
            }
        }
    	this.logger.trace("EncodingRulesTesterBaseModel.addAttributeResult: leave");
    }
    
    public void printAttributeResults() {
    	final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n\nObject Attribute Summary \n");
        if (this.objectAttributeResultsmap.isEmpty()) {
            stringBuilder.append("- No Results -\n");
            this.logger.trace(stringBuilder.toString());
            return;
        }
    	String objectName = null;
    	ObjectClassHandle objectClassHandle;
        for (Map.Entry<ObjectInstanceHandle, Map<AttributeHandle, ResultInfoAttribute>> entryObject : this.objectAttributeResultsmap.entrySet()) {
    		try {
    			objectName = this.ivct_rti.getObjectInstanceName(entryObject.getKey());
        		objectClassHandle = this.ivct_rti.getKnownObjectClassHandle(entryObject.getKey());
			} catch (FederateNotExecutionMember | NotConnected | RTIinternalError | ObjectInstanceNotKnown e) {
                this.logger.error("EncodingRulesTesterBaseModel.printAttributeResults: " + e);
				continue;
			}
    		String attributeName = null;
            for (Map.Entry<AttributeHandle, ResultInfoAttribute> entryAttribute : entryObject.getValue().entrySet()) {
    			try {
					attributeName = this.ivct_rti.getAttributeName(objectClassHandle, entryAttribute.getKey());
				} catch (AttributeNotDefined | InvalidAttributeHandle | InvalidObjectClassHandle
						| FederateNotExecutionMember | NotConnected | RTIinternalError e) {
                    this.logger.error("EncodingRulesTesterBaseModel.printAttributeResults: " + e);
				}
                stringBuilder.append("OBJECT: " + objectName + " ATTRIBUTE: " + attributeName + " CORRECT: " + entryAttribute.getValue().getCorrectCount() + " INCORRECT: " + entryAttribute.getValue().getIncorrectCount());
                if (entryAttribute.getValue().getIncorrectCount() > 0) {
					stringBuilder.append(" TEXT: " + entryAttribute.getValue().getText() + " Federate: " + entryAttribute.getValue().getFederateName());
                }
                stringBuilder.append("\n");
    		}
    	}
    	this.logger.info(stringBuilder.toString());
    }

    /**
     * @param theObjecttheObject the object instance handle
     * @param attributeHandletheObject the attribute handle
     * @param b byte field containing attribute data
     * @param errorBool whether to use logger error (true) or debug (false)
     */
    private void displayReflectAttributeValuesMessage(final ObjectInstanceHandle theObject, final AttributeHandle attributeHandle, final byte b[], final boolean errorBool) {
        String attributeName = null;
        String knownObjectClass = null;
        String objectName = null;
        try {
            objectName = this.ivct_rti.getObjectInstanceName(theObject);
            ObjectClassHandle knownObjectClassHandle = this.ivct_rti.getKnownObjectClassHandle(theObject);
            knownObjectClass = this.ivct_rti.getObjectClassName(knownObjectClassHandle);
            attributeName = this.ivct_rti.getAttributeName(knownObjectClassHandle, attributeHandle);
        } catch (ObjectInstanceNotKnown | FederateNotExecutionMember | NotConnected | RTIinternalError | AttributeNotDefined | InvalidAttributeHandle | InvalidObjectClassHandle e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }
        String sNames = new String("Object name: " + objectName + " Known object class: " + knownObjectClass + " Attribute name: " + attributeName);
        String sIDs = new String("Object handle: " + theObject + " Attribute handle: " + attributeHandle);
        String sBytes = new String("Attribute value bytes: " + bytesToHex(b));
        if (errorBool) {
            this.logger.error(sNames);
            this.logger.error(sIDs);
            this.logger.error(sBytes);
            this.logger.error("");
        } else {
            this.logger.debug(sNames);
            this.logger.debug(sIDs);
            this.logger.debug(sBytes);
            this.logger.debug("");
        }
    }

    /**
     * @param theObject the object instance handle
     * @param theAttributes the map of attribute handle / value
     */
    private void doReflectAttributeValues(final ObjectInstanceHandle theObject, final AttributeHandleValueMap theAttributes) {
        this.logger.trace("EncodingRulesTesterBaseModel.doReflectAttributeValues: enter");

        Boolean bool = new Boolean(true);
        for (Map.Entry<AttributeHandle, byte[]> entry : theAttributes.entrySet()) {
            this.logger.trace("EncodingRulesTesterBaseModel.doReflectAttributeValues: GOT attribute " + entry.getKey());
            this.logger.trace("EncodingRulesTesterBaseModel.doReflectAttributeValues: GOT reflectAttributeValues " + this.attributeHandleDataTypeMap.get(entry.getKey()));

            this.attributeHandleChecked.put(entry.getKey(), bool);

            HlaDataType hdt = this.hlaDataTypes.dataTypeMap.get(this.attributeHandleDataTypeMap.get(entry.getKey()));
            byte b[] = theAttributes.get(entry.getKey());
            this.logger.debug("EncodingRulesTesterBaseModel.doReflectAttributeValues: length " + b.length);
//            for (int i = 0; i < b.length; i++) {
//                this.logger.trace("EncodingRulesTesterBaseModel.doReflectAttributeValues: byte " + b[i]);
//            }
            try {
            	int calculatedLength = hdt.testBuffer(entry.getValue(), 0, this.hlaDataTypes);
				if (calculatedLength != entry.getValue().length) {
					String error = "TEST BUFFER INCORRECT: overall length calculation: " + calculatedLength + " Buffer length: " + entry.getValue().length;
					this.logger.error(error);
		            this.errorOccurred = true;
		            addAttributeResult(theObject, entry.getKey(), false, error);
		            displayReflectAttributeValuesMessage(theObject, entry.getKey(), b, true);
		            this.incorrect += 1;
				} else {
					String ok = "TEST BUFFER CORRECT";
					this.logger.trace(ok);
		            addAttributeResult(theObject, entry.getKey(), true, ok);
		            displayReflectAttributeValuesMessage(theObject, entry.getKey(), b, false);
		            this.correct += 1;
				}
			} catch (EncodingRulesException e) {
				String error = "TEST BUFFER INCORRECT: " + e.getMessage();
				this.logger.error(error);
	            this.errorOccurred = true;
	            addAttributeResult(theObject, entry.getKey(), false, error);
	            displayReflectAttributeValuesMessage(theObject, entry.getKey(), b, true);
	            this.incorrect += 1;
			}
        }
        this.logger.trace("EncodingRulesTesterBaseModel.doReflectAttributeValues: leave");
    }


    /**
     * @param theObject the object instance handle
     * @param theAttributes the map of attribute handle / value
     */
    @Override
    public void reflectAttributeValues(final ObjectInstanceHandle theObject, final AttributeHandleValueMap theAttributes, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        this.doReflectAttributeValues(theObject, theAttributes);
    }


    /**
     * @param theObject the object instance handle
     * @param theAttributes the map of attribute handle / value
     */
    @Override
    public void reflectAttributeValues(final ObjectInstanceHandle theObject, final AttributeHandleValueMap theAttributes, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final LogicalTime theTime, final OrderType receivedOrdering, final SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        this.doReflectAttributeValues(theObject, theAttributes);
    }


    /**
     * @param theObject the object instance handle
     * @param theAttributes the map of attribute handle / value
     */
    @Override
    public void reflectAttributeValues(final ObjectInstanceHandle theObject, final AttributeHandleValueMap theAttributes, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final LogicalTime theTime, final OrderType receivedOrdering, final MessageRetractionHandle retractionHandle, final SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        this.doReflectAttributeValues(theObject, theAttributes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void informAttributeOwnership (
            ObjectInstanceHandle theObject,
            AttributeHandle      theAttribute,
            FederateHandle       theOwner)
                    throws FederateInternalError {
        this.logger.trace("EncodingRulesTesterBaseModel.informAttributeOwnership: ENTER");
        Map<AttributeHandle, ResultInfoAttribute> attributeResultInfoAttribute = this.objectAttributeResultsmap.get(theObject);
        if (attributeResultInfoAttribute != null) {
            ResultInfoAttribute resultInfoAttribute = attributeResultInfoAttribute.get(theAttribute);
            if (resultInfoAttribute != null) {
                resultInfoAttribute.addFederateHandle(theOwner);
                try {
                    this.logger.trace("EncodingRulesTesterBaseModel.informAttributeOwnership: BEFORE");
                    resultInfoAttribute.addFederateName(this.ivct_rti.getFederateName(theOwner));
                    this.logger.trace("EncodingRulesTesterBaseModel.informAttributeOwnership: AFTER");
				} catch (InvalidFederateHandle | FederateHandleNotKnown | FederateNotExecutionMember | NotConnected
						| RTIinternalError e) {
                    this.logger.error("EncodingRulesTesterBaseModel.informAttributeOwnership: " + e);
				}
            }
        }
        this.logger.trace("EncodingRulesTesterBaseModel.informAttributeOwnership: LEAVE");
    }
}
