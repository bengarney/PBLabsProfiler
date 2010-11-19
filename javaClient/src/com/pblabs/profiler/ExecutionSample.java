package com.pblabs.profiler;

import java.util.*;

import org.eclipse.swt.widgets.TreeItem;
import org.slf4j.LoggerFactory;

public class ExecutionSample {
	public Dictionary<String, ExecutionSample> subSamples = new Hashtable<String, ExecutionSample>();
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ExecutionSample.class);

	
	public int totalCount = 0;
	public int selfCount = 0;

	public int alloc = 0, free = 0;

	public int totalAlloc = 0;
	
	public int cumulativeTime = 0;

	TreeItem displayNode;
	
	private Map<String, Map<Long,Integer>> typesToAllocationByLineMap = new HashMap<String,Map<Long,Integer>>();
	
	/**
	 * Return a displayable string of the allocations for this execution sample.
	 * 
	 * @return
	 */
	public String getAllocations()
	{
		String msg = "";
		for (String key : typesToAllocationByLineMap.keySet()) {
			msg+="Object allocated: "+key+"\n";
			msg+=getLineNumberToCount(typesToAllocationByLineMap.get(key));
		}
		
		return msg;
	}

	public String getLineNumberToCount(Map<Long,Integer> map)
	{
		String msg = "";
		for (Long key : map.keySet()) {
			msg+="   allocations at line "+key;
			msg+=": ";
			msg+=map.get(key);
			msg+="\n";
		}	
		
		return msg;
	}
	
	
	public void addAllocation(long lineNumber, String allocation)
	{
		Map<Long,Integer> lineNumToCount = typesToAllocationByLineMap.get(allocation);

		if (lineNumToCount==null)
		{
			lineNumToCount = new HashMap<Long,Integer>();
			typesToAllocationByLineMap.put(allocation, lineNumToCount);
		}

		Integer count = lineNumToCount.get(lineNumber);
		if (count==null)
		{
			count = new Integer(0);
		}
		count += 1;
		lineNumToCount.put(lineNumber, count);
	}
	
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
		// We walk the sample stack starting at the top of the
		// stack frame and working our way down.
		long lineNumber=-1;
		
		ExecutionSample walk = this;
		for(int i=0; i<stack.frameList.size(); i++)
		{
			if(time != 0)
				walk.totalCount++;
			
			walk = walk.getChild(stack.frameList.get(i).name);

			// We will use the last line linenumber to record
			// the location of the allocation if there is one
			lineNumber = stack.frameList.get(i).fileline;
			
			// Always add the allocation to each node in the stack frame
			walk.totalAlloc += alloc;
		}
		
		if(time != 0)
		{
			walk.cumulativeTime += time;
			walk.totalCount++;
			walk.selfCount++;
		}
		
		// Now we are at the bottom of the stack,
		// add the alloc directly to this node
		walk.alloc += alloc;
		walk.free += free;
		
		// Add allocation to the leaf node
		if (stack.allocType!=null)
		{
			walk.addAllocation(lineNumber, stack.allocType);
		}
	}	
}
