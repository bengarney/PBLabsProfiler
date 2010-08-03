package com.pblabs.profiler;

import java.io.IOException;
import java.util.Enumeration;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

public class FlashProfilerDataWindow {

	public TreeItem rootItem;
	public Tree profilerTree;
	public ProfilerServerHandler.Worker handler;

	public Text additionalInfo;
	
	public FlashProfilerDataWindow(ProfilerServerHandler.Worker _h)
	{
		handler = _h;
		
		// Initialize us on the UI thread.
		final FlashProfilerDataWindow us = this;
		FlashProfiler.display.asyncExec(new Runnable()
		{
			public void run() {
				us.initSwt();				
			}
		});
	}
	
	public void initSwt()
	{
		Shell shell = new Shell(FlashProfiler.display);
		shell.setText("PBLabs Flash Profiler");

		GridLayout layout = new GridLayout();
		layout.numColumns=1;
		shell.setLayout(layout);
		   
		Composite toolBar = new Composite(shell, SWT.BORDER);
		GridLayout toolBarLayout = new GridLayout();
		toolBarLayout.numColumns=3;
		toolBar.setLayout(toolBarLayout);
	    Button startItem = new Button(toolBar, SWT.PUSH);
	    startItem.setText("Start");
	    Button stopItem = new Button(toolBar, SWT.PUSH);
	    stopItem.setText("Stop");
	    Button resetItem = new Button(toolBar, SWT.PUSH);
	    resetItem.setText("Reset");
	    
	    GridData toolbarData = new GridData();
	    toolbarData.horizontalAlignment=GridData.FILL;
	    toolBar.setLayoutData(toolbarData);

	    SashForm sashForm = new SashForm(shell, SWT.VERTICAL);

	    GridData sashData = new GridData();
	    sashData.grabExcessHorizontalSpace=true;
	    sashData.grabExcessVerticalSpace=true;
	    sashData.horizontalAlignment=GridData.FILL;
	    sashData.verticalAlignment=GridData.FILL;
	    sashForm.setLayoutData(sashData);

	    startItem.addSelectionListener(new SelectionAdapter()
	    {
	    	@Override
	    	public void widgetSelected(SelectionEvent e) {
	    		try {
	    			handler.sendStart();
	    		} catch (IOException ex) {
	    			ex.printStackTrace();
	    		}
	    	}
	    });

	    stopItem.addSelectionListener(new SelectionAdapter()
	    {
	    	@Override
	    	public void widgetSelected(SelectionEvent e) {
	    		try {
	    			handler.sendPause();
	    		} catch (IOException ex) {
	    			ex.printStackTrace();
	    		}
	    	}
	    });  		

	    resetItem.addSelectionListener(new SelectionAdapter()
	    {
	    	@Override
	    	public void widgetSelected(SelectionEvent e) {
	    		handler.reset();
	    	}
	    }); 
	    
		profilerTree = new Tree(sashForm, SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		profilerTree.setHeaderVisible(true);

		// Window to display additional info
  		additionalInfo = new Text(sashForm, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
  		additionalInfo.setEditable(false);
  		
		final FlashProfilerDataWindow fpdw = this;
		profilerTree.addKeyListener(new KeyListener()
		{

			public void keyPressed(KeyEvent e) {
				synchronized(handler.sampleRoot)
				{
					fpdw.rebuildTreeView(handler.sampleRoot);				
				}
			}

			public void keyReleased(KeyEvent e) {
				// TODO Auto-generated method stub
				
			}
			
		});
		
		TreeColumn tcName = new TreeColumn(profilerTree, SWT.LEFT);
		tcName.setText("Name");
		tcName.setWidth(600);
		
		TreeColumn tcPercent = new TreeColumn(profilerTree, SWT.RIGHT);
		tcPercent.setText("Total");
		tcPercent.setWidth(50);

		TreeColumn tcPercent1 = new TreeColumn(profilerTree, SWT.RIGHT);
		tcPercent1.setText("Self");
		tcPercent1.setWidth(50);

		TreeColumn tcPercent2 = new TreeColumn(profilerTree, SWT.RIGHT);
		tcPercent2.setText("Total Allocs");
		tcPercent2.setWidth(50);

		TreeColumn tcPercent3 = new TreeColumn(profilerTree, SWT.RIGHT);
		tcPercent3.setText("Self Allocs");
		tcPercent3.setWidth(50);
		
		rootItem = new TreeItem(profilerTree, 0);
		rootItem.setText(new String[] { "Root",  "", "", "", ""});
		
		shell.open();
	}
	
	public void rebuildTree()
	{
		profilerTree.removeAll();
		rootItem = new TreeItem(profilerTree, 0);
		rootItem.setText(new String[] { "Root",  "", "", "", ""});		
	}
	
	public void rebuildTreeView(ExecutionSample sampleRoot)
	{
		// Update the tree.
		recursiveTreeBuild(rootItem, sampleRoot);
		updateAdditionalInfo();
	}
	
	public void updateAdditionalInfo()
	{
		TreeItem[] selectedItems = profilerTree.getSelection();
		if (selectedItems.length>0)
		{
			ExecutionSample sample = (ExecutionSample) selectedItems[0].getData();
			
			if (sample!=null)
			{
				additionalInfo.setText(sample.getAllocations());
			}
		}		
	}
	
	protected void recursiveTreeBuild(TreeItem localRoot, ExecutionSample samples)
	{
		// Build a new tree.
		Enumeration<String> keys = samples.subSamples.keys();
		while(keys.hasMoreElements())
		{
			String key = keys.nextElement();
			ExecutionSample subSample = samples.subSamples.get(key); 
			
			if(subSample.displayNode == null)
				subSample.displayNode = new TreeItem(localRoot, 0);
			
			subSample.displayNode.setText(new String[] 
                 { key,  String.valueOf(subSample.totalCount), String.valueOf(subSample.selfCount),
					String.valueOf(subSample.totalAlloc),
					String.valueOf(subSample.alloc) });			
			
			subSample.displayNode.setData(subSample);
			
			recursiveTreeBuild(subSample.displayNode, subSample);
		}
	}
	
}
