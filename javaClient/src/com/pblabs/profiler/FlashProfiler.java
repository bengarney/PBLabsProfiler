package com.pblabs.profiler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoAcceptorConfig;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.FillLayout;
import org.slf4j.LoggerFactory;

public class FlashProfiler {

	private static final int PORT = 42624;
	public static Display display;

	private static final org.slf4j.Logger log = LoggerFactory.getLogger(FlashProfiler.class);
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Display.setAppName("Flash Profiler");
		display = new Display();

		Shell shell = new Shell(display);
		shell.setText("PBLabs Flash Profiler");
		shell.setLayout(new FillLayout());

		// Show the log area.
		final Text consoleLog = new Text(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		
		Logger.getLogger("").addHandler(new Handler()
		{
			public Text consoleOut = consoleLog;
			public SimpleFormatter sf = new SimpleFormatter();
			
			public boolean isLoggable(LogRecord lr)
			{
				return true;
			}
			
			public void publish(LogRecord lr)
			{
				final String msg = lr.getLoggerName() + sf.format(lr);
				final Text consoleOut2 = consoleOut;
				
				display.asyncExec(new Runnable()
				{
					public void run()
					{
						consoleOut2.append(msg);						
					}
				});
			}
			
			public void close()
			{
				consoleOut.append("Done.\n");				
			}
			
			public void flush()
			{
				
			}
		});

		log.info("PBLabs Profiler v1.00");
		
		// Kick up the profiler server.
		try
		{
			initNetPort();			
		}
		catch(IOException ioe)
		{
			log.info("Failed to open port" + PORT + " for profiler connections.");
		}
		
		// Run main loop.
		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) display.sleep();
		}
		display.dispose ();
	}
	
	public static void spawnDataWindow()
	{

	}
	
	public static void initNetPort() throws IOException
	{
        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());
        
        IoAcceptor acceptor = new SocketAcceptor();
        IoAcceptorConfig config = new SocketAcceptorConfig();
        acceptor.bind( new InetSocketAddress(PORT), new ProfilerServerHandler(), config);
        log.info("PBLabs Profiler server started on localhost:" + PORT);
	}
}
