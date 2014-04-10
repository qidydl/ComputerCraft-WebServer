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

@Mod(modid = "qidydlCCWebServer", name = "ComputerCraft WebServer") // dependencies = "required-after:ComputerCraft@[1.6,]"
@NetworkMod(serverSideRequired = true, clientSideRequired = true)
public class CCWebServer
{
	// The instance of your mod that Forge uses.
	@Instance(value = "qidydlCCWebServer")
	public static CCWebServer instance;

	// Says where the client and server 'proxy' code is loaded.
	@SidedProxy(clientSide="com.qidydl.ccwebserver.client.ClientProxy", serverSide="com.qidydl.ccwebserver.CommonProxy")
	public static CommonProxy proxy;

	private static final Block blockWebModem = new BlockWebModem(1234);

	public static int LISTEN_PORT = 12345;

	// Standard Java instance variables
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
			LISTEN_PORT = cfg.get(Configuration.CATEGORY_GENERAL, "listenPort", 12345).getInt();
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
	}

	@EventHandler
	public void load(FMLInitializationEvent event)
	{
		proxy.registerRenderers();

		LanguageRegistry.addName(blockWebModem, "Web Modem");
		GameRegistry.registerBlock(blockWebModem, "blockWebModem");

		GameRegistry.registerTileEntity(TileEntityWebModem.class, "tileEntityWebModem");

		ItemStack stoneStack = new ItemStack(Block.stone);
		ItemStack enderPearlStack = new ItemStack(Item.enderPearl);
		ItemStack diamondStack = new ItemStack(Item.diamond);
		ItemStack webModemStack = new ItemStack(blockWebModem);

		GameRegistry.addRecipe(webModemStack,
				"xxx", "xyx", "xzx",
				'x', stoneStack, 'y', enderPearlStack, 'z', diamondStack);
	}

	@EventHandler
	public void serverStarting(FMLServerStartingEvent event)
	{
		commsThread = new CommsThread(LISTEN_PORT);
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
