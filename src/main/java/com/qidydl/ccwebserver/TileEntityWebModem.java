package com.qidydl.ccwebserver;

import net.minecraft.tileentity.TileEntity;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IPeripheral;

public class TileEntityWebModem extends TileEntity implements IPeripheral
{
	private IComputerAccess m_attachedComputer;

	public TileEntityWebModem()
	{
		m_attachedComputer = null;
	}

	@Override
	public String getType()
	{
		return "webModem";
	}

	@Override
	public String[] getMethodNames()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, ILuaContext context,
			int method, Object[] arguments) throws Exception
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void attach(IComputerAccess computer)
	{
		// TODO Auto-generated method stub
		m_attachedComputer = computer;
	}

	@Override
	public void detach(IComputerAccess computer)
	{
		// TODO Auto-generated method stub
		m_attachedComputer = null;
	}

	@Override
	public boolean equals(IPeripheral other) {
		// TODO Auto-generated method stub
		return false;
	}
}
