package com.pblabs.profiler;

import java.util.*;

import org.eclipse.swt.widgets.TreeItem;

public class ExecutionSample {
	public Dictionary<String, ExecutionSample> subSamples = new Hashtable<String, ExecutionSample>();

	public int totalCount = 0;
	public int selfCount = 0;

	public int alloc = 0, free = 0;

	public int totalAlloc = 0;
	
	public int cumulativeTime = 0;

	TreeItem displayNode;
	
	public ExecutionSample getChild(String name)
	{
		ExecutionSample child = subSamples.get(name);
		
		if(child == null)
		{
			child = new ExecutionSample();
			subSamples.put(name, child);			
		}
		
		return child;
	}
	
	public void insert(int time, int alloc, int free, SampleStack stack)
	{
		ExecutionSample walk = this;
		for(int i=0; i<stack.frameList.size(); i++)
		{
			if(time != 0)
				walk.totalCount++;
			walk.totalAlloc += alloc;
			walk = walk.getChild(stack.frameList.get(i).name);
		}
		
		if(time != 0)
		{
			walk.cumulativeTime += time;
			walk.totalCount++;
			walk.selfCount++;
		}
		
		walk.alloc += alloc;
		walk.free += free;
	}	
}
