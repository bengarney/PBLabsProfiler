package com.pblabs.profiler;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.Vector;

public class SampleStack {
	public Vector<SampleStackFrame> frameList = new Vector<SampleStackFrame>();
	
	public void read(DataInputStream messageBytes) throws CharacterCodingException, IOException
	{
		short stackCount = messageBytes.readShort();
		
		for (int i=0; i<stackCount; i++)
		{
			SampleStackFrame frame = new SampleStackFrame();
			
			// Get the name.
			frame.name = FlashIoUtils.readFlashString(messageBytes);

			// Filename/line are optionally present.
			frame.filename = FlashIoUtils.readFlashString(messageBytes);
			if(frame.filename != null)
				frame.fileline = messageBytes.readInt();
			
			// Add to the beginning of the stack.
			frameList.add(0, frame);
		}		
	}
	
}
