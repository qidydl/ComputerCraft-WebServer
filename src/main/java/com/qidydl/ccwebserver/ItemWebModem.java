package com.qidydl.ccwebserver;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public class ItemWebModem extends Item
{
	public ItemWebModem(int id)
	{
		super(id);
		setMaxStackSize(64);
		setCreativeTab(CreativeTabs.tabMisc);
		setUnlocalizedName("itemWebModem");
	}
}
