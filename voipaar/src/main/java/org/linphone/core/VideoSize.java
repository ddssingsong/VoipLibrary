/*
VideoSize.java
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

/**
 * @author Guillaume Beraudo
 */
public final class VideoSize {
	public static final int QCIF = 0;
	public static final int CIF = 1;
	public static final int HVGA = 2;
	public static final int QVGA = 3;
	public static final org.linphone.core.VideoSize VIDEO_SIZE_QCIF = new org.linphone.core.VideoSize(176,144);
	public static final org.linphone.core.VideoSize VIDEO_SIZE_CIF = new org.linphone.core.VideoSize(352,288);
	public static final org.linphone.core.VideoSize VIDEO_SIZE_QVGA = new org.linphone.core.VideoSize(320,240);
	public static final org.linphone.core.VideoSize VIDEO_SIZE_HVGA = new org.linphone.core.VideoSize(320,480);
	public static final org.linphone.core.VideoSize VIDEO_SIZE_VGA = new org.linphone.core.VideoSize(640,480);
	public static final org.linphone.core.VideoSize VIDEO_SIZE_720P = new org.linphone.core.VideoSize(1280,720);
	public static final org.linphone.core.VideoSize VIDEO_SIZE_1020P = new org.linphone.core.VideoSize(1920,1080);

	public int width;
	public int height;

	public VideoSize() {}
	public VideoSize(int width, int height) {
		this.width = width;
		this.height = height;
	}

	@Deprecated
	public static final org.linphone.core.VideoSize createStandard(int code, boolean inverted) {
		switch (code) {
		case QCIF:
			return inverted? new org.linphone.core.VideoSize(144, 176) : new org.linphone.core.VideoSize(176, 144);
		case CIF:
			return inverted? new org.linphone.core.VideoSize(288, 352) : new org.linphone.core.VideoSize(352, 288);
		case HVGA:
			return inverted? new org.linphone.core.VideoSize(320,480) : new org.linphone.core.VideoSize(480, 320);
		case QVGA:
			return inverted? new org.linphone.core.VideoSize(240, 320) : new org.linphone.core.VideoSize(320, 240);
		default:
			return new org.linphone.core.VideoSize(); // Invalid one
		}
	}

	public boolean isValid() {
		return width > 0 && height > 0;
	}

	// Generated
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + height;
		result = prime * result + width;
		return result;
	}
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		org.linphone.core.VideoSize other = (org.linphone.core.VideoSize) obj;
		if (height != other.height)
			return false;
		if (width != other.width)
			return false;
		return true;
	}

	public String toDisplayableString() {
		return width + "x" + height;
	}
	public String toString() {
		return "width = "+width + " height = " + height;
	}
	public boolean isPortrait() {
		return height >= width;
	}
	public org.linphone.core.VideoSize createInverted() {
		return new org.linphone.core.VideoSize(height, width);
	}
	
}
