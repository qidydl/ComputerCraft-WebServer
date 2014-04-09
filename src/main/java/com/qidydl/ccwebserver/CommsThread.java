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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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

	public void registerModem(TileEntityWebModem modem)
	{
		//TODO: Save the modem information
		// Map (player, computerName) pair to a modem so we know where to send input
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

				// Allocating buffers every time is not optimal, but it might not be a big deal.
				// *If* it proves to be a problem, this can be converted to a shared or per-thread
				// buffer or something like that.
				ByteBuffer outputBuffer = ByteBuffer.wrap(data.getBytes("UTF-8"));
				conn.write(outputBuffer);
			}
			catch (UnsupportedEncodingException e)
			{
				// Should be impossible to get here--UTF-8 supports damn near everything, right?
				FMLLog.log(Level.SEVERE, e, "CCWebServer: CommsThread: Unsupported encoding!");
				e.printStackTrace();
			}
			catch (IOException e)
			{
				// Thrown mostly by the socket being closed, which will be detected and handled by
				// the normal processing loop.
			}
		}
	}

	@Override
	public void run() {
		this.setName("CCWebServer.CommsThread");
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
							sockets.put(conn.hashCode(), conn);

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

		String input = inputBuilder.toString();
		String path = "";
		Map<String, String> params = new HashMap<String, String>();

		int split = input.indexOf('?');
		if (split > 0)
		{
			// First line is the URL path
			path = input.substring(0, split);

			// Remaining lines are all parameter data concatenated together
			String paramData = input.substring(split + 1);
			String[] paramPairs = paramData.split("&");
			for (String pair : paramPairs)
			{
				split = pair.indexOf('=');
				String key = pair.substring(0, split);
				String value = pair.substring(split + 1);
				params.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
			}
		}
		else
		{
			// No parameters specified
			path = input;
		}

		// Remove any leading/trailing whitespace
		path = path.trim();

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

		// Break the path into the sequence of "folders"
		String[] pathParts = path.split("/");

		// Make sure they've specified at least the player and computer name
		if (pathParts.length < 2)
		{
			transmitResponse(sc.hashCode(), 404, "Object Not Found: The requested URL did not supply a player and computer name to access.");
		}
		else
		{
			//TODO: look up modem based on player and computer name
		}

		return true;
	}
}
