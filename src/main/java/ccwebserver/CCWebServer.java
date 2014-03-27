package ccwebserver;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;

@Mod(modid = "qidydlCCWebServer", name = "ComputerCraft WebServer", version = "0.0.1")
@NetworkMod(serverSideRequired = true, clientSideRequired = true)
public class CCWebServer
{
	// The instance of your mod that Forge uses.
	@Instance(value = "qidydlCCWebServer")
	public static CCWebServer instance;

	// Says where the client and server 'proxy' code is loaded.
	@SidedProxy(clientSide="ccwebserver.client.ClientProxy", serverSide="ccwebserver.CommonProxy")
	public static CommonProxy proxy;

	private static final Item itemWebModem = new ItemWebModem(12345);

	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		// Stub Method
	}

	@EventHandler
	public void load(FMLInitializationEvent event)
	{
		proxy.registerRenderers();

		LanguageRegistry.addName(itemWebModem, "Web Modem");

		ItemStack stoneStack = new ItemStack(Block.stone);
		ItemStack enderPearlStack = new ItemStack(Item.enderPearl);
		ItemStack diamondStack = new ItemStack(Item.diamond);
		ItemStack webModemStack = new ItemStack(itemWebModem);

		GameRegistry.addRecipe(webModemStack,
				"xxx", "xyx", "xzx",
				'x', stoneStack, 'y', enderPearlStack, 'z', diamondStack);
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event)
	{
		// Stub Method
	}
}
