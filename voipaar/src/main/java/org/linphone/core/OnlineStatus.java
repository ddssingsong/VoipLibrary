/*
OnlineStatus.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.linphone.core;

import java.util.Vector;


/**
 * Enum describing remote friend status
 * @deprecated Use #PresenceModel and #PresenceActivity instead
 */

@Deprecated
public class OnlineStatus {
	
	static private Vector<OnlineStatus> values = new Vector<OnlineStatus>();
	/**
	 * Offline
	 */
	static public org.linphone.core.OnlineStatus Offline = new org.linphone.core.OnlineStatus(0,"Offline");
	/**
	 * Online
	 */
	static public org.linphone.core.OnlineStatus Online = new org.linphone.core.OnlineStatus(1,"Online");
	/**
	 * Busy
	 */
	static public org.linphone.core.OnlineStatus Busy = new org.linphone.core.OnlineStatus(2,"Busy");
	/**
	 * Be Right Back
	 */
	static public org.linphone.core.OnlineStatus BeRightBack = new org.linphone.core.OnlineStatus(3,"BeRightBack");
	/**
	 * Away
	 */
	static public org.linphone.core.OnlineStatus Away = new org.linphone.core.OnlineStatus(4,"Away");
	/**
	 * On The Phone
	 */
	static public org.linphone.core.OnlineStatus OnThePhone = new org.linphone.core.OnlineStatus(5,"OnThePhone");
	/**
	 * Out To Lunch
	 */
	static public org.linphone.core.OnlineStatus OutToLunch  = new org.linphone.core.OnlineStatus(6,"OutToLunch ");
	/**
	 * Do Not Disturb
	 */
	static public org.linphone.core.OnlineStatus DoNotDisturb = new org.linphone.core.OnlineStatus(7,"DoNotDisturb");
	/**
	 * Moved in this sate, call can be redirected if an alternate contact address has been set using function {@link LinphoneCore#setPresenceInfo(int, String, org.linphone.core.OnlineStatus)}
	 */
	static public org.linphone.core.OnlineStatus StatusMoved = new org.linphone.core.OnlineStatus(8,"StatusMoved");
	/**
	 * Using another messaging service
	 */
	static public org.linphone.core.OnlineStatus StatusAltService = new org.linphone.core.OnlineStatus(9,"StatusAltService");
	/**
	 * Pending
	 */
	static public org.linphone.core.OnlineStatus Pending = new org.linphone.core.OnlineStatus(10,"Pending");

	protected final int mValue;
	private final String mStringValue;


	private OnlineStatus(int value,String stringValue) {
		mValue = value;
		values.addElement(this);
		mStringValue=stringValue;
	}
	public static org.linphone.core.OnlineStatus fromInt(int value) {
		for (int i=0; i<values.size();i++) {
			org.linphone.core.OnlineStatus state = (org.linphone.core.OnlineStatus) values.elementAt(i);
			if (state.mValue == value) return state;
		}
		throw new RuntimeException("state not found ["+value+"]");
	}

	public String toString() {
		return mStringValue;
	}

}
