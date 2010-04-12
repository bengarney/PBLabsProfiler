package com.pblabs.profiler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import org.apache.mina.common.IoSession;
import org.apache.mina.handler.StreamIoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfilerServerHandler extends StreamIoHandler{
	
    protected void processStreamIo(IoSession session, InputStream in, OutputStream out)
    {
    	new Worker(in, out, session).start();
    }
    
    public class Worker extends Thread {
    	public FlashProfilerDataWindow dataWindow;
    	
    	public int memoryUsed;
    	public ExecutionSample sampleRoot = new ExecutionSample();
    	public Logger log;
    	
    	public int expectedSamples = 0;
    	public int samplesOnClient = 0;
    	public int receivedSamples = 0;
    	
        private final InputStream in;
        private final OutputStream out;

        public Worker(InputStream in, OutputStream out, IoSession session) {
            setDaemon(true);
            this.in = in;
            this.out = out;

            log = LoggerFactory.getLogger("Profiler Session " + session.getRemoteAddress().toString());
    		log.info("Got connection!");

    		// Set up the window.
    		dataWindow = new FlashProfilerDataWindow(this);
        }
        
        public void run()
        {
        	DataInputStream messageBytes = new DataInputStream(in);
        	DataOutputStream commandBytes = new DataOutputStream(out);
        	
        	try
        	{
            	// Send a sampling start.
        		log.info("Sending sampling start.");
            	commandBytes.writeShort(0x4204);
            	commandBytes.flush();

/*            	// Sleep for ten seconds...
            	sleep(60*1000);
            	
            	// Send a sampling stop.
        		log.info("Sending sampling pause.");
            	commandBytes.writeShort(0x4206);
            	commandBytes.flush();
            	
            	// Sleep for a touch.
            	sleep(1000);

            	// And request a dump.
        		log.info("Sending sampling dump request.");
            	commandBytes.writeShort(0x420a);
            	commandBytes.flush();*/
        	}
        	catch(Exception e)
        	{
				log.error(e.toString());
				return;        		
        	}
        	
        	// Parse messages.
    		while(true)
    		{
    			messageBytes.mark(2048);
    			
    			try
    			{
        			parseMessage(messageBytes);
    			}
    			catch(EOFException e)
    			{
    				log.error("Ran off end of stream; terminating.");
    				break;
    			}
    			catch(Exception e)
    			{
    				try
    				{
    					log.error("Resetting due to :" + e.toString());
        				messageBytes.reset();    					
    				}
    				catch(IOException e2)
    				{
    					log.error("Failed to reset stream due to " + e2.toString());
    					break;
    				}
    			}
    		}
        }
        
        public void parseMessage(DataInputStream messageBytes) throws CharacterCodingException, IOException
        {
    			// Get the message type.
    			int type = messageBytes.readUnsignedShort();

    			switch(type)
    			{
    			case 0x4201: // HELLO
    				long seconds = messageBytes.readInt();
    				int remainder = messageBytes.readUnsignedShort();
    				long timer = messageBytes.readInt();
    				log.info("Got hello world with seconds=" + seconds + ", remainder=" + remainder + ",timer=" + timer);
    				break;
    				
    			case 0x4203: // STATS
    				long timer1 = messageBytes.readInt();
    				long memory = messageBytes.readInt();
    				log.info("Get stats with timer=" + timer1 + ", memory=" + memory);
    				break;
    				
    			case 0x4205: // sampling start ack.
    				log.info("Sampling started.");
    				break;
    				
    			case 0x4207: // sampling pause ack.
    				log.info("Sampling paused.");
    				break;
    				
    			case 0x4209: // sampling stop ack.
    				log.info("Sampling stopped.");
    				break;
    				
    			case 0x420b: // Results from sampler.
    				long numIncoming = messageBytes.readInt();
    				expectedSamples += numIncoming;
    				log.info("Getting " + numIncoming + " more samples, expecting " + expectedSamples + " more.");
    				break;
    				
    			case 0x4210: // new object sample.
    				receivedSamples++;
    				samplesOnClient = messageBytes.readInt();
    				messageBytes.readInt();
    				messageBytes.readInt();
    				FlashIoUtils.readFlashString(messageBytes);
    				
    				SampleStack ss = new SampleStack();
    				ss.read(messageBytes);

    				// Accumulate the data.
    				synchronized(sampleRoot)
    				{
        				sampleRoot.insert(0, 1, 0, ss);    					
    				}
    				
    				break;
    				
    			case 0x4211: // delete object sample.
    				receivedSamples++;
    				samplesOnClient = messageBytes.readInt();
    				messageBytes.readInt();
    				messageBytes.readInt();
    				messageBytes.readInt();

    				SampleStack ss1 = new SampleStack();
    				ss1.read(messageBytes);
    				
    				break;
    				
    			case 0x4212: // execution sample.
    				receivedSamples++;
    				samplesOnClient = messageBytes.readInt();
    				int time = messageBytes.readInt();
    				SampleStack ss2 = new SampleStack();
    				ss2.read(messageBytes);

    				// Accumulate the data.
    				synchronized(sampleRoot)
    				{
        				sampleRoot.insert(time, 0, 0, ss2);    					
    				}
    				
    				break;
    			}
    			
    			if(receivedSamples % 100 == 0)
    			{
    				// Note process.
    				log.info("Got " + receivedSamples + " so far. " + samplesOnClient + " left on client.");
    				receivedSamples++;
    				
    				// Refresh UI.
    				FlashProfiler.display.asyncExec(new Runnable()
    				{
    					public void run()
    					{
    	    				dataWindow.rebuildTreeView(sampleRoot);    						
    					}
    				});
    			}
    		}        	
        }
    }

