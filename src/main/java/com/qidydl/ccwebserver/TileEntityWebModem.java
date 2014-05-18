package com.qidydl.ccwebserver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public class TileEntityWebModem extends TileEntity implements IPeripheral
{
	private final Map<Integer, IComputerAccess> m_attachedComputers;

	public TileEntityWebModem()
	{
		m_attachedComputers = new HashMap<Integer, IComputerAccess>();
	}

	@Override
	public String getType()
	{
		return "webModem";
	}

	@Override
	public String[] getMethodNames()
	{
		return new String[] { "sendReply" };
	}

	/**
	 * Receives a request from the outside world and passes it to a computer we're attached to.
	 * @param socketHashCode The hash code of the socket the request came in on, used as a connection identifier.
	 * @param computerID The ID of the computer the request is being sent to.
	 * @param path The path information provided in the request.
	 * @param params The additional parameters provided in the request.
	 */
	public void receiveRequest(int socketHashCode, int computerID, String path, List<String> params)
	{
		IComputerAccess computer = m_attachedComputers.get(computerID);

		if (computer == null)
		{
			CommsThread.getInstance().transmitResponse(socketHashCode, 404, "Computer ID " + computerID + " is not available.");
		}
		else
		{
			// Combine the path and the parameters into a single array
			Object[] args = new Object[params.size() + 1];
			args[0] = path;
			System.arraycopy(params, 0, args, 1, params.size());

			computer.queueEvent("webModem_request", args);
		}
	}

	/**
	 * Send a reply back to the outside world.
	 * @param arguments The data provided by the ComputerCraft program.
	 */
	private void sendReply(Object[] arguments)
	{
		if (arguments.length < 3)
		{
			throw new IllegalArgumentException("Must provide connection ID, response code, and response data.");
		}

		int socketHashCode = Integer.parseInt(arguments[0].toString());
		int responseCode = Integer.parseInt(arguments[1].toString());
		String responseData = arguments[2].toString();

		CommsThread.getInstance().transmitResponse(socketHashCode, responseCode, responseData);
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, ILuaContext context,
			int method, Object[] arguments) throws Exception
	{
		switch (method)
		{
		case 0:
			this.sendReply(arguments);
			return null;
		default:
			throw new UnsupportedOperationException("Method ID " + method + " is not defined.");
		}
	}

	@Override
	public void attach(IComputerAccess computer)
	{
		m_attachedComputers.put(computer.getID(), computer);
		CommsThread.getInstance().registerModem(computer.getID(), this);
	}

	@Override
	public void detach(IComputerAccess computer)
	{
		CommsThread.getInstance().unregisterModem(computer.getID(), this);
		m_attachedComputers.remove(computer.getID());
	}

	@Override
	public boolean equals(IPeripheral other) {
		if (other instanceof TileEntityWebModem)
		{
			TileEntityWebModem otherCasted = (TileEntityWebModem)other;
			return this.m_attachedComputers.equals(otherCasted.m_attachedComputers);
		}
		else
		{
			return false;
		}
	}
}
