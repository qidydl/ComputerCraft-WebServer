package com.qidydl.ccwebserver;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
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

	private final Semaphore terminate;
	private final int listenPort;
	private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );
	private Selector sel;
	private final ConcurrentMap<Integer, SocketChannel> sockets;
	private final ConcurrentMap<Integer, TileEntityWebModem> modems;

	private static CommsThread s_Instance = null;

	/**
	 * Create a new Communications Thread.
	 *
	 * @param port The port to listen for connections on.
	 */
	public CommsThread(int port)
	{
		terminate = new Semaphore(0);
		listenPort = port;
		sockets = new ConcurrentHashMap<Integer, SocketChannel>();
		modems = new ConcurrentHashMap<Integer, TileEntityWebModem>();
		try
		{
			sel = Selector.open();
		}
		catch (IOException e)
		{
			// We're basically screwed if this happens.
			// Initialization in the run() method checks for this, because
			// blowing up in the constructor is problematic.
			sel = null;
		}
	}

	/**
	 * Set the instance of the CommsThread being used.
	 * @param ct The CommsThread that should be used for communication.
	 */
	public static void setInstance(CommsThread ct)
	{
		s_Instance = ct;
	}

	/**
	 * Get the current instance of the CommsThread being used.
	 * @return The CommsThread that should be used for communication.
	 */
	public static CommsThread getInstance()
	{
		return s_Instance;
	}

	/**
	 * Tell the communications thread to stop processing and close all open
	 * connections.
	 */
	public void shutdown()
	{
		s_Instance = null;

		// Releasing the semaphore allows the thread to exit the loop
		terminate.release();

		// Waking the selector breaks us out of the select() call
		sel.wakeup();
	}

	/**
	 * Tell the communications thread that a new modem has been activated.
	 * @param computerID The computer that is using the modem.
	 * @param modem The modem that was activated.
	 */
	public void registerModem(int computerID, TileEntityWebModem modem)
	{
		modems.put(computerID, modem);
	}

	/**
	 * Tell the communications thread that a modem has been deactivated.
	 * @param computerID The computer that is no longer using the modem.
	 * @param modem The modem that was deactivated.
	 */
	public void unregisterModem(int computerID, TileEntityWebModem modem)
	{
		modems.remove(computerID);
	}

	/**
	 * Transmit a response to a particular connection. This can only be used when the Comms Thread
	 * has told a modem that it has a request.
	 *
	 * @param connection The connection identifier that was provided by the Comms Thread.
	 * @param responseCode The HTTP response code to transmit with the response.
	 * @param data The data to transmit.
	 */
	public void transmitResponse(int connection, int responseCode, String data)
	{
		if (sockets.containsKey(connection))
		{
			try
			{
				SocketChannel conn = sockets.get(connection);

				// Allocating a buffer every time is not optimal, but it might not be a big deal.
				// *If* it proves to be a problem, this can be converted to a shared or per-thread
				// buffer or something like that.
				ByteBuffer outputBuffer = ByteBuffer.wrap(data.getBytes("UTF-8"));
				conn.write(outputBuffer);
			}
			catch (UnsupportedEncodingException e)
			{
				// Should be impossible to get here; UTF-8 is really standard and supports nearly any character
				FMLLog.log(Level.SEVERE, e, "CCWebServer: CommsThread: Unsupported encoding!");
				e.printStackTrace();
			}
			catch (IOException e)
			{
				// Thrown mostly by the socket being closed, which will be detected and handled by
				// the normal processing loop, so we don't need to do anything here.
			}
		}
	}

	/**
	 * The thread entry point.
	 */
	@Override
	public void run() {
		// Set the thread name, mostly for debugging purposes.
		this.setName("CCWebServer.CommsThread");

		ServerSocketChannel servsock = null;
		boolean initialized = false;
		boolean terminated = true;

		// Don't bother trying to initialize if we couldn't create the Selector
		if (sel != null)
		{
			// Initialize I/O and start listening for connections
			try
			{
				servsock = ServerSocketChannel.open();
				servsock.bind(new InetSocketAddress(listenPort));
				servsock.configureBlocking(false);
				servsock.register(sel, SelectionKey.OP_ACCEPT);

				// We only need to do clean-up below if initialization was successful
				initialized = true;

				// Only allow the loop to run if we finished all of the above
				terminated = false;
			}
			catch (IOException e)
			{
				// We're basically screwed if this happens.
				FMLLog.log(Level.SEVERE, e, "CCWebServer Mod: CommsThread: Could not listen for connections!");
			}
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
							sockets.put(conn.hashCode(), conn);

							//DEBUG
							System.out.println("Received connection from " + conn.socket().getRemoteSocketAddress().toString());
						}
						else // Everything else is UN-ACCEPTABLE :P
						if (key.isReadable())
						{
							SocketChannel conn = (SocketChannel)key.channel();

							if (!processInput(conn))
							{
								// We couldn't read any data. This *might* be an error, but most likely it's socket
								// closure, so clean up.
								key.cancel();
								conn.socket().close();
								sockets.remove(conn.hashCode());

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

		// Verify we received some data
		if (buffer.position() == 0)
		{
			return false;
		}

		// Keep reading data until we've got it all
		StringBuilder inputBuilder = new StringBuilder();
		do
		{
			// "Flip" the buffer to access the data we just received
			buffer.flip();

			// Copy the data out of the buffer and convert it to a string
			byte[] validData = Arrays.copyOfRange(buffer.array(), 0, buffer.limit());
			String bufferData = new String(validData, "UTF-8");
			inputBuilder.append(bufferData);

			// Clear the buffer and perform another read
			buffer.clear();
			sc.read(buffer);
		} while (buffer.position() > 0);

		// We have all the data, start processing
		String input = inputBuilder.toString();
		String path = "";
		List<String> params = new ArrayList<String>();

		// First, break apart path and query string
		int split = input.indexOf('?');
		if (split > 0)
		{
			// First part is the URL path
			path = input.substring(0, split);

			// The rest is all parameter data concatenated together
			String paramData = input.substring(split + 1);
			String[] paramPairs = paramData.split("&");
			for (String pair : paramPairs)
			{
				params.add(URLDecoder.decode(pair, "UTF-8"));
			}
		}
		else
		{
			// No parameters specified
			path = input;
		}

		// Remove any leading/trailing whitespace
		path = path.trim();

		// Now we have an absolute path and parameter data, next we need to examine the path.

		//DEBUG
		System.out.println("Received request for [" + path + "] with parameters [" + params.toString() + "]");
		ByteBuffer test = ByteBuffer.allocate(1024);
		test.put("test\n".getBytes("UTF-8"));
		test.flip();
		sc.write(test);
		//DEBUG

		// Strip any leading slashes
		while (path.substring(0, 1) == "/")
		{
			path = path.substring(1);
		}

		// Break the path into computer ID and everything else
		String computerID = "";
		int computerIDint = 0;
		String remainingPath = "";

		// Path format is <computerId>[/optional further path]
		split = path.indexOf('/');

		if (split > 0)
		{
			computerID = path.substring(0, split);
			remainingPath = path.substring(split + 1);
		}
		else
		{
			computerID = path;
		}

		try
		{
			computerIDint = Integer.parseInt(computerID);
		}
		catch (NumberFormatException e)
		{
			transmitResponse(sc.hashCode(), 400, "Bad Request: Computer ID must be a valid integer");
		}

		if (modems.containsKey(computerIDint))
		{
			TileEntityWebModem modem = modems.get(computerIDint);
			modem.receiveRequest(sc.hashCode(), computerIDint, remainingPath, params);
		}
		else
		{
			transmitResponse(sc.hashCode(), 404, "Object Not Found: The specified computer ID does not exist or is not ready.");
		}

		return true;
	}
}
