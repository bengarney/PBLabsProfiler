package com.pblabs.profiler;

import java.io.BufferedInputStream;
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
    public static int MAX_INT = Integer.MAX_VALUE-1;
    public static String SAMPLE_DONE = "SAMPLE-DONE";
    
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
    	
        private NetStringCache netStringCache = new NetStringCache();
      
    	private DataInputStream messageBytes = null;
    	private DataOutputStream commandBytes = null;
    	
        public Worker(InputStream in, OutputStream out, IoSession session) {
            setDaemon(true);

        	messageBytes = new DataInputStream(new BufferedInputStream(in));
        	commandBytes = new DataOutputStream(out);
        	
            log = LoggerFactory.getLogger("Profiler Session " + session.getRemoteAddress().toString());
    		log.info("Got connection!");

    		// Set up the window.
    		dataWindow = new FlashProfilerDataWindow(this);
        }
        
        public void logAndSetStatus(String msg)
        {
    		log.info(msg);
    		// Refresh UI.
    		final String statusMsg = msg;
			FlashProfiler.display.asyncExec(new Runnable()
			{
				public void run()
				{
		    		dataWindow.setStatus(statusMsg);
				}
			});
        }
        
        public void sendStart() throws IOException
        {
        	logAndSetStatus("Sending sampling start.");
        	commandBytes.writeShort(0x4204);
        	commandBytes.flush();
        }
        
        public void sendPause() throws IOException
        {
    		logAndSetStatus("Sending sampling stop.");
        	commandBytes.writeShort(0x4208);
        	commandBytes.flush();
        }
        
        public void reset()
        {
        	synchronized (sampleRoot) {
            	dataWindow.rebuildTree();
            	sampleRoot = new ExecutionSample();		
				dataWindow.rebuildTreeView(sampleRoot);    	
			}
        }
        
        public void run()
        {

        	
        	try
        	{
            	// Send a sampling start.
        		/*
        		log.info("Sending sampling start.");
            	commandBytes.writeShort(0x4204);
            	commandBytes.flush();
        		*/
        		
        		/*
            	// Sleep for ten seconds...
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
    				e.printStackTrace();
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
        
        /**
         * Return a BitStream for the sample if the complete sample
         * is there. Otherwise, reset the stream and return null.
         * 
         * @param messageBytes
         * @return
         * @throws IOException
         */
        private BitStream getSampleBitStream(DataInputStream messageBytes) throws IOException
        {
 			long incomingSampleSize = 0xFFFFFFFFL & messageBytes.readInt();  					

			// Make sure we have the whole sample
			if (messageBytes.available() < incomingSampleSize)
			{
				//log.info("Not enough bytes for message");
				messageBytes.reset();
				return null;
			}  
			
			byte[] bytes = new byte[(int)incomingSampleSize];
			messageBytes.read(bytes,0,(int)incomingSampleSize);
			
			BitStream bitStream = new BitStream((int)incomingSampleSize, bytes);
			bitStream.setStringCache(netStringCache);  
			
			return bitStream;
        }
        
        public void parseMessage(DataInputStream messageBytes) throws CharacterCodingException, IOException, Exception
        {
        		messageBytes.mark(10);				
    			// Get the message type.
    			int type = messageBytes.readUnsignedShort();
    	        BitStream bitStream = null;
    	        
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
    				logAndSetStatus("Sampling started.");
    				break;
    				
    			case 0x4207: // sampling pause ack.
    				logAndSetStatus("Sampling paused.");
    				break;
    				
    			case 0x4209: // sampling stop ack.
    				logAndSetStatus("Sampling stopped.");
    				break;
    				
    			case 0x420b: // Results from sampler.
    				long numIncoming = messageBytes.readInt();
    				expectedSamples += numIncoming;
    				dataWindow.setStatus("Getting " + numIncoming + " more samples, expecting " + expectedSamples + " more.");
    				break;
    				
    			case 0x4210: // new object sample.
    				bitStream = getSampleBitStream(messageBytes);
    				
    				if (bitStream==null)
    					break;

    				receivedSamples++;
    				
    				samplesOnClient = bitStream.readRangedInt(0, Integer.MAX_VALUE-1);
    				bitStream.readRangedInt(0,MAX_INT);
    				bitStream.readRangedInt(0,MAX_INT);
    				String allocationType = bitStream.readCachedString();   				
    				
    				SampleStack ss = new SampleStack();
    				ss.read(bitStream);
    				
    				ss.allocType = allocationType;
    				
    				// Accumulate the data.
    				synchronized(sampleRoot)
    				{
        				sampleRoot.insert(0, 1, 0, ss);    					
    				}
    				
    				String check = bitStream.readCachedString();
    				// log.info(check);
    				if (check==null || !check.equals(SAMPLE_DONE)) {
    					log.error("Missing message SAMPLE-DONE");
    				}
    				
    				break;
    				
    			case 0x4211: // delete object sample.
    				bitStream = getSampleBitStream(messageBytes);
    				if (bitStream==null)
    					break;
    				
    				receivedSamples++;
    				samplesOnClient = bitStream.readRangedInt(0,MAX_INT);
    				bitStream.readRangedInt(0,MAX_INT);
    				bitStream.readRangedInt(0,MAX_INT);
    				bitStream.readRangedInt(0,MAX_INT);

    				SampleStack ss1 = new SampleStack();
    				ss1.read(bitStream);
    				
    				if (!bitStream.readCachedString().equals(SAMPLE_DONE))
    					throw new Exception("Bad sample!");
    				
    				break;
    				
    			case 0x4212: // execution sample.
    				bitStream = getSampleBitStream(messageBytes);
    				if (bitStream==null)
    					break;
    				
    				receivedSamples++;
    				samplesOnClient = bitStream.readRangedInt(0,MAX_INT);
    				int time = bitStream.readRangedInt(0,MAX_INT);
    				SampleStack ss2 = new SampleStack();
    				ss2.read(bitStream);
    				
    				if (!bitStream.readCachedString().equals(SAMPLE_DONE))
    					throw new Exception("Bad sample!");
    			
    				// Accumulate the data.
    				synchronized(sampleRoot)
    				{
        				sampleRoot.insert(time, 0, 0, ss2);    					
    				}
    				
    				break;
    			}
    			
    			if(receivedSamples > 0 && receivedSamples % 100 == 0)
    			{
    				receivedSamples++;
    				
    				// Refresh UI.
    				FlashProfiler.display.asyncExec(new Runnable()
    				{
    					public void run()
    					{
    	    				dataWindow.rebuildTreeView(sampleRoot);    						
    	    				// Note process
    	    				dataWindow.setStatus("Got " + receivedSamples + " so far. " + samplesOnClient + " left on client.");
    					}
    				});
    			}
        	}
    	}
    }

