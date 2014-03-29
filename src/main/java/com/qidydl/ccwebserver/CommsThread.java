package com.qidydl.ccwebserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;

/**
 * The Communications Thread for ComputerCraft WebServer.
 * 
 * This thread is responsible for listening for external connections, receiving
 * data, and passing it to the appropriate WebModem. It must also receive data
 * from a WebModem and transmit it back.
 * 
 * @author qidydl
 */
public class CommsThread extends Thread {
	
	private Semaphore terminate;
	private Selector sel;
	private int listenPort;
	private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );
	
	/**
	 * Create a new Communications Thread.
	 * 
	 * @param port The port to listen for connections on.
	 */
	public CommsThread(int port)
	{
		terminate = new Semaphore(0);
		try
		{
			sel = Selector.open();
		}
		catch (IOException e)
		{
			// We're basically screwed if this happens.
			// Initialization in the run() method checks for this, because
			// blowing up in the constructor is annoying at best.
			sel = null;
		}
		listenPort = port;
	}

	/**
	 * Tell the communications thread to stop processing and close all open
	 * connections.
	 */
	public void shutdown()
	{
		// Releasing the semaphore allows the thread to exit the loop
		terminate.release();

		// Waking the selector breaks us out of the select() call
		sel.wakeup();
	}

	@Override
	public void run() {
		boolean terminated = false;
		boolean initialized = false;

		ServerSocketChannel servsock = null;

		// Initialize I/O and start listening for connections
		try
		{
			// Don't bother trying to initialize if we couldn't create the Selector
			if (sel != null)
			{
				servsock = ServerSocketChannel.open();
				servsock.bind(new InetSocketAddress(listenPort));
				servsock.configureBlocking(false);
				servsock.register(sel, SelectionKey.OP_ACCEPT);
				initialized = true;
			}
		}
		catch (IOException e)
		{
			// We're basically screwed if this happens.
			FMLLog.log(Level.SEVERE, e, "CCWebServer Mod: CommsThread: Could not listen for connections!");
			terminated = true;
		}

		// Main processing loop - listen for connections or new data
		while (!terminated)
		{
			try
			{
				// Block until something happens. Shutdown will release us from this.
				int num = sel.select();
				
				if (num > 0)
				{
					// Look through all the items that should have something available
					for (SelectionKey key : sel.selectedKeys())
					{
						if (key.isAcceptable())
						{
							// Accept the incoming connection.
							SocketChannel conn = ((ServerSocketChannel)key.channel()).accept();
							conn.configureBlocking(false);
							conn.register(sel, SelectionKey.OP_READ);

							//DEBUG
							System.out.println("Received connection from " + conn.socket().getRemoteSocketAddress().toString());
						}
						else // Everything else is UN-ACCEPTABLE
						if (key.isReadable())
						{
							SocketChannel conn = (SocketChannel)key.channel();
							
							if (!processInput(conn))
							{
								// We couldn't read any data. This *might* be an error, but most likely it's socket
								// closure, so clean up.
								key.cancel();
								conn.socket().close();
								
								//DEBUG
								System.out.println("Connection closed from " + conn.socket().getRemoteSocketAddress().toString());
							}
						}
					}
					// Once we're done processing, clear out the set.
					sel.selectedKeys().clear();
				}

				// Check the semaphore to see if we're shutting down, but don't block.
				terminated = terminate.tryAcquire();
			}
			catch (ClosedSelectorException e)
			{
				// We're done, for whatever reason.
				FMLLog.log(Level.WARNING, e, "CCWebServer: CommsThread: Selector closed unexpectedly.");
				terminated = true;
			}
			catch (IOException e)
			{
				// May or may not be able to recover from some situations, for now just give up.
				// In the future this should be moved inside the selectedKeys loop to cancel any keys that
				// are causing problems.
				FMLLog.log(Level.WARNING, e, "CCWebServer: CommsThread: Communications failure!");
				terminated = true;
			}
		}

		// Clean-up
		if (initialized)
		{
			try
			{
				servsock.close();
				sel.close();
			}
			catch (IOException e)
			{
				// We don't care, we're shutting down anyway.
			}
		}
	}
	
	/**
	 * Process data received from a remote connection.
	 * 
	 * @param sc The connection to receive data from.
	 * @return True if data was actually received, false otherwise.
	 * @throws IOException If an error occurs while reading the data.
	 */
	private boolean processInput(SocketChannel sc) throws IOException
	{
		// Read received data into the buffer
		buffer.clear();
		sc.read(buffer);

		if (buffer.position() == 0)
		{
			return false;
		}

		// "Flip" the buffer to transmit the data we just received
		buffer.flip();
		sc.write(buffer);
		return true;
	}
}
