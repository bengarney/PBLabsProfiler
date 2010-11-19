package com.pblabs.profiler;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.Vector;

public class SampleStack {
	public Vector<SampleStackFrame> frameList = new Vector<SampleStackFrame>();
    public static int MAX_INT = Integer.MAX_VALUE-1;
    
    public String allocType = null;
	
	public void read(BitStream messageBytes) throws CharacterCodingException, IOException
	{
		int stackCount = messageBytes.readRangedInt(0,MAX_INT);
		
		for (int i=0; i<stackCount; i++)
		{
			SampleStackFrame frame = new SampleStackFrame();
			
			// Get the name.
			frame.name = messageBytes.readCachedString();

			// Filename/line are optionally present.
			frame.filename = messageBytes.readCachedString();
			if(frame.filename != null)
				frame.fileline = messageBytes.readRangedInt(0,MAX_INT);
			
			// Add to the beginning of the stack.
			frameList.add(0, frame);
		}		
	}	
}
