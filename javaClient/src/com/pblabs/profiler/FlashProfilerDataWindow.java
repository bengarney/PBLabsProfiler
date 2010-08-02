package com.pblabs.profiler;

import java.io.IOException;
import java.util.Enumeration;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

public class FlashProfilerDataWindow {

	public TreeItem rootItem;
	public Tree profilerTree;
	public ProfilerServerHandler.Worker handler;

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
		shell.setLayout(new FillLayout());
		
	    Menu menu = new Menu(shell, SWT.BAR);
	    shell.setMenuBar(menu);
	    
	    Menu fileMenu = new Menu(menu);

	    MenuItem fileMenuItem = new MenuItem(menu, SWT.CASCADE);
	    fileMenuItem.setText("File");
	    fileMenuItem.setMenu(fileMenu);
	    
	    MenuItem startItem = new MenuItem(fileMenu, SWT.NONE);
	    startItem.setText("Start Sampling");
	    MenuItem pauseItem = new MenuItem(fileMenu, SWT.NONE);
	    pauseItem.setText("Stop Sampling");

	    
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

	    pauseItem.addSelectionListener(new SelectionAdapter()
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
	    
		RowLayout rowLayout = new RowLayout();
  		rowLayout.wrap = true;
  		rowLayout.pack = false;
  		rowLayout.fill = false;
  		rowLayout.justify = false;
  		rowLayout.type = SWT.VERTICAL;
  		rowLayout.marginLeft = 5;
  		rowLayout.marginTop = 5;
  		rowLayout.marginRight = 5;
  		rowLayout.marginBottom = 5;
  		rowLayout.spacing = 0;
  		shell.setLayout(new FillLayout());
		
		profilerTree = new Tree(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		profilerTree.setHeaderVisible(true);

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
	
	public void rebuildTreeView(ExecutionSample sampleRoot)
	{
		// Update the tree.
		recursiveTreeBuild(rootItem, sampleRoot);
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
			
			recursiveTreeBuild(subSample.displayNode, subSample);
		}
	}
	
}
