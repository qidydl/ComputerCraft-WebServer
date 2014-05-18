package com.qidydl.ccwebserver;

import static net.minecraftforge.common.ForgeDirection.EAST;
import static net.minecraftforge.common.ForgeDirection.NORTH;
import static net.minecraftforge.common.ForgeDirection.SOUTH;
import static net.minecraftforge.common.ForgeDirection.WEST;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;

//TODO: Turn into a Forge micropart?

public class BlockWebModem extends Block implements ITileEntityProvider, IPeripheralProvider
{
	private static final int DIR_NORTH = 4;
	private static final int DIR_SOUTH = 3;
	private static final int DIR_EAST  = 1;
	private static final int DIR_WEST  = 2;

	public BlockWebModem(int id)
	{
		super(id, Material.ground);
		setCreativeTab(CreativeTabs.tabMisc);
		setUnlocalizedName("blockWebModem");
		setBlockBounds(0.8125F, 0.0625F, 0.0625F, 1.0F, 0.9375F, 0.9375F);
	}

	/**
	 * Is this block (a) opaque and (b) a full 1m cube?  This determines whether or not to render the shared face of two
	 * adjacent blocks and also whether the player can attach torches, redstone wire, etc to this block.
	 */
	@Override
	public boolean isOpaqueCube()
	{
		return false;
	}

	/**
	 * If this block doesn't render as an ordinary block it will return False (examples: signs, buttons, stairs, etc)
	 */
	@Override
	public boolean renderAsNormalBlock()
	{
		return false;
	}

	/**
	 * Get a new Tile Entity associated with this block.
	 */
	@Override
	public TileEntity createNewTileEntity(World world)
	{
		return new TileEntityWebModem();
	}

	/**
	 * Checks to see if it's valid to put this block on the side of a block in the specified direction of the specified coordinates.
	 */
	@Override
	public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int direction)
	{
		ForgeDirection dir = ForgeDirection.getOrientation(direction);
		return	(dir == NORTH && world.isBlockSolidOnSide(x,     y, z + 1, NORTH)) ||
				(dir == SOUTH && world.isBlockSolidOnSide(x,     y, z - 1, SOUTH)) ||
				(dir == EAST  && world.isBlockSolidOnSide(x - 1, y, z,     EAST)) ||
				(dir == WEST  && world.isBlockSolidOnSide(x + 1, y, z,     WEST));
	}

	/**
	 * Checks to see if it's valid to put this block at the specified coordinates.
	 */
	@Override
	public boolean canPlaceBlockAt(World world, int x, int y, int z)
	{
		return	(world.isBlockSolidOnSide(x,     y, z + 1, NORTH)) ||
				(world.isBlockSolidOnSide(x,     y, z - 1, SOUTH)) ||
				(world.isBlockSolidOnSide(x - 1, y, z,     EAST)) ||
				(world.isBlockSolidOnSide(x + 1, y, z,     WEST));
	}

	/**
	 * Called when a block is placed using its ItemBlock.
	 * Determines how the block is oriented and saves that as meta-data.
	 */
	@Override
	public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int data)
	{
		int meta = world.getBlockMetadata(x, y, z);

		// The side of the block we are attaching to
		ForgeDirection dir = ForgeDirection.getOrientation(side);

		if (dir == NORTH && world.isBlockSolidOnSide(x, y, z + 1, NORTH))
		{
			meta = DIR_NORTH;
		}
		else if (dir == SOUTH && world.isBlockSolidOnSide(x, y, z - 1, SOUTH))
		{
			meta = DIR_SOUTH;
		}
		else if (dir == EAST && world.isBlockSolidOnSide(x - 1, y, z, EAST))
		{
			meta = DIR_EAST;
		}
		else if (dir == WEST && world.isBlockSolidOnSide(x + 1, y, z, WEST))
		{
			meta = DIR_WEST;
		}
		else
		{
			meta = this.getOrientation(world, x, y, z);
		}

		world.setBlockMetadataWithNotify(x, y, z, meta, 2);
		setBlockBoundsBasedOnState(world, x, y, z);
		world.markBlockForUpdate(x, y, z);

		return meta;
	}

	/**
	 * Fall-back mechanism for determining orientation.
	 */
	private int getOrientation(World world, int x, int y, int z)
	{
		if (world.isBlockSolidOnSide(x,     y, z + 1, NORTH)) { return DIR_NORTH; }
		if (world.isBlockSolidOnSide(x,     y, z - 1, SOUTH)) { return DIR_SOUTH; }
		if (world.isBlockSolidOnSide(x - 1, y, z,     EAST))  { return DIR_EAST; }
		if (world.isBlockSolidOnSide(x + 1, y, z,     WEST))  { return DIR_WEST; }
		return DIR_EAST;
	}

	/**
	 * Updates the block's bounds based on its current state.
	 * The direction of the block we're attached to determines the block's shape.
	 */
	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z)
	{
		// The direction of the block we're attached to
		int meta = world.getBlockMetadata(x, y, z);

		if (meta == DIR_EAST)//	minX		minY		minZ		maxX		maxY		maxZ
		{					//	east		bottom		south		west		top			north
			this.setBlockBounds(0.0F,		0.125F,		0.125F,		0.1875F,	0.875F,		0.875F);
		}
		else if (meta == DIR_WEST)
		{
			this.setBlockBounds(0.8125F,	0.125F,		0.125F,		1.0F,		0.875F,		0.875F);
		}
		else if (meta == DIR_SOUTH)
		{
			this.setBlockBounds(0.125F,		0.125F,		0.0F,		0.875F,		0.875F,		0.1875F);
		}
		else if (meta == DIR_NORTH)
		{
			this.setBlockBounds(0.125F,		0.125F,		0.8125F,	0.875F, 	0.875F,		1.0F);
		}
	}

	/**
	 * Use a standard boundary for rendering the block as an Item (in inventories and floating in the world).
	 */
	@Override
	public void setBlockBoundsForItemRender()
	{
		this.setBlockBounds(0.5F - 0.1875F,		0.125F,		0.125F,		0.5F,	0.875F,		0.875F);
	}

	@SideOnly(Side.CLIENT)
	public static Icon faceIcon;

	@SideOnly(Side.CLIENT)
	public static Icon sideIcon;

	@SideOnly(Side.CLIENT)
	public static Icon sideInvertedIcon;

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister registry)
	{
		faceIcon = registry.registerIcon("ccwebserver:webModemFace");
		sideIcon = registry.registerIcon("ccwebserver:webModemSide");
		sideInvertedIcon = registry.registerIcon("ccwebserver:webModemSideInvert");
	}

	/**
	 * How to texture the block as an Item (in inventories and floating in the world).
	 */
	@Override
	@SideOnly(Side.CLIENT)
	public Icon getIcon(int side, int metadata) {
		if (side == 4)
		{
			return faceIcon;
		}
		else
		{
			return sideIcon;
		}
	}

	/**
	 * Retrieves the block texture to use based on the display side.
	 */
	@Override
	@SideOnly(Side.CLIENT)
	public Icon getBlockTexture(IBlockAccess world, int x, int y, int z, int side)
	{
		ForgeDirection dir = ForgeDirection.getOrientation(side);
		int meta = world.getBlockMetadata(x, y, z);

		if ((meta == DIR_NORTH && dir == NORTH) ||
			(meta == DIR_SOUTH && dir == SOUTH) ||
			(meta == DIR_EAST  && dir == EAST)  ||
			(meta == DIR_WEST  && dir == WEST))
		{
			return faceIcon;
		}
		else if ((dir == NORTH) || (dir == EAST))
		{
			return sideIcon;
		}
		else
		{
			return sideInvertedIcon;
		}
	}

	@Override
	public IPeripheral getPeripheral(World world, int x, int y, int z, int side)
	{
		return (TileEntityWebModem)world.getBlockTileEntity(x, y, z);
	}
}
