package com.qidydl.ccwebserver;

import java.util.logging.Level;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.Configuration;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import dan200.computercraft.api.ComputerCraftAPI;

@Mod(modid = "qidydlCCWebServer", name = "ComputerCraft WebServer", dependencies = "required-after:ComputerCraft@[1.6,)")
@NetworkMod(serverSideRequired = true, clientSideRequired = true)
public class CCWebServer
{
	// The instance of your mod that Forge uses.
	@Instance(value = "qidydlCCWebServer")
	public static CCWebServer instance;

	// Says where the client and server 'proxy' code is loaded.
	@SidedProxy(clientSide="com.qidydl.ccwebserver.client.ClientProxy", serverSide="com.qidydl.ccwebserver.CommonProxy")
	public static CommonProxy proxy;

	// The port to listen on for incoming requests.
	public static int LISTEN_PORT = 60000;

	// Standard Java instance variables
	public static int blockWebModemID = 1234;
	public static Block blockWebModem;
	private CommsThread commsThread;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		Version.init(event.getVersionProperties());
		event.getModMetadata().version = Version.fullVersionString();

		Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
		try
		{
			cfg.load();
			blockWebModemID = cfg.getBlock("webModem", blockWebModemID).getInt();
			LISTEN_PORT = cfg.get(Configuration.CATEGORY_GENERAL, "listenPort", LISTEN_PORT).getInt();
		}
		catch (Exception e)
		{
			FMLLog.log(Level.WARNING, e, "ComputerCraft WebServer has a problem loading its configuration");
		}
		finally
		{
			if (cfg.hasChanged())
			{
				cfg.save();
			}
		}

		blockWebModem = new BlockWebModem(blockWebModemID);
		GameRegistry.registerBlock(blockWebModem, "blockWebModem");
	}

	@EventHandler
	public void load(FMLInitializationEvent event)
	{
		proxy.registerRenderers();

		LanguageRegistry.addName(blockWebModem, "Web Modem");

		GameRegistry.registerTileEntity(TileEntityWebModem.class, "tileEntityWebModem");

		ItemStack stoneStack = new ItemStack(Block.stone);
		ItemStack enderPearlStack = new ItemStack(Item.enderPearl);
		ItemStack diamondStack = new ItemStack(Item.diamond);
		ItemStack webModemStack = new ItemStack(blockWebModem);

		GameRegistry.addRecipe(webModemStack,
				"xxx", "xyx", "xzx",
				'x', stoneStack, 'y', enderPearlStack, 'z', diamondStack);

		ComputerCraftAPI.registerPeripheralProvider((BlockWebModem)blockWebModem);
	}

	@EventHandler
	public void serverStarting(FMLServerStartingEvent event)
	{
		commsThread = new CommsThread(LISTEN_PORT);
		CommsThread.setInstance(commsThread);
		commsThread.start();
	}

	@EventHandler
	public void serverStopping(FMLServerStoppingEvent event)
	{
		commsThread.shutdown();
		try
		{
			commsThread.join();
		}
		catch (InterruptedException e)
		{
			// We don't care, we're shutting down anyway.
		}
	}
}
