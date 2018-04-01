package org.linphone.core;

import java.util.Vector;

public class Reason {
	static private Vector<Reason> values = new Vector<Reason>();
	/**
	 * None (no failure)
	 */
	static public org.linphone.core.Reason None = new org.linphone.core.Reason(0,"None");
	/**
	 * No response
	 */
	static public org.linphone.core.Reason NoResponse = new org.linphone.core.Reason(1,"NoResponse");
	/**
	 * Bad credentials
	 */
	static public org.linphone.core.Reason BadCredentials = new org.linphone.core.Reason(2,"BadCredentials");
	/**
	 * Call declined
	 */
	static public org.linphone.core.Reason Declined = new org.linphone.core.Reason(3,"Declined");
	/**
	 * Not found
	 */
	static public org.linphone.core.Reason NotFound = new org.linphone.core.Reason(4,"NotFound");
	/**
	 * Call not answered (in time).
	 */
	static public org.linphone.core.Reason NotAnswered = new org.linphone.core.Reason(5,"NotAnswered");
	/**
	 * Call not answered (in time).
	 */
	static public org.linphone.core.Reason Busy = new org.linphone.core.Reason(6,"Busy");
	/**
	 * Incompatible media
	 * */
	static public org.linphone.core.Reason Media = new org.linphone.core.Reason(7,"Media");
	/**
	 * Transport error: connection failures, disconnections etc...
	 * */
	static public org.linphone.core.Reason IOError = new org.linphone.core.Reason(8,"IOError");
	/**
	 * Transport error: connection failures, disconnections etc...
	 * */
	static public org.linphone.core.Reason DoNotDisturb = new org.linphone.core.Reason(9,"DoNotDisturb");
	/**
	 * Operation not authorized because no credentials found
	 * */
	static public org.linphone.core.Reason Unauthorized = new org.linphone.core.Reason(10,"Unauthorized");
	/**
	 * Operation was rejected by remote, for example a LinphoneCore.updateCall()
	 */
	static public org.linphone.core.Reason NotAcceptable = new org.linphone.core.Reason(11,"NotAcceptable");
	/**
	 * Operation was rejected by remote due to request unmatched to any context.
	 */
	static public org.linphone.core.Reason NoMatch = new org.linphone.core.Reason(12,"NoMatch");
	/**
	 * Resource moved permanently
	 */
	static public org.linphone.core.Reason MovedPermanently = new org.linphone.core.Reason(13,"MovedPermanently");
	/**
	 * Resource no longer exists
	 */
	static public org.linphone.core.Reason Gone = new org.linphone.core.Reason(14,"Gone");
	/**
	 * Temporarily unavailable
	 */
	static public org.linphone.core.Reason TemporarilyUnavailable = new org.linphone.core.Reason(15,"TemporarilyUnavailable");
	/**
	 * Address incomplete
	 */
	static public org.linphone.core.Reason AddressIncomplete = new org.linphone.core.Reason(16,"AddressIncomplete");
	/**
	 * Not implemented
	 */
	static public org.linphone.core.Reason NotImplemented = new org.linphone.core.Reason(17,"NotImplemented");
	/**
	 * Bad gateway
	 */
	static public org.linphone.core.Reason BadGateway = new org.linphone.core.Reason(18,"BadGateway");
	/**
	 * Server timeout
	 */
	static public org.linphone.core.Reason ServerTimeout = new org.linphone.core.Reason(19,"ServerTimeout");
	/**
	 * Unknown
	 */
	static public org.linphone.core.Reason Unknown = new org.linphone.core.Reason(20,"Unknown");

	protected final int mValue;
	private final String mStringValue;


	private Reason(int value,String stringValue) {
		mValue = value;
		values.addElement(this);
		mStringValue=stringValue;
	}
	public static org.linphone.core.Reason fromInt(int value) {
		for (int i=0; i<values.size();i++) {
			org.linphone.core.Reason state = (org.linphone.core.Reason) values.elementAt(i);
			if (state.mValue == value) return state;
		}
		throw new RuntimeException("Reason not found ["+value+"]");
	}

	public String toString() {
		return mStringValue;
	}
}
