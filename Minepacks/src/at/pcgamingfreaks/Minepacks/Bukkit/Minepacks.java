/*
 *   Copyright (C) 2020 GeorgH93
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package at.pcgamingfreaks.Minepacks.Bukkit;

import at.pcgamingfreaks.Bukkit.MCVersion;
import at.pcgamingfreaks.Bukkit.Message.Message;
import at.pcgamingfreaks.Bukkit.Updater;
import at.pcgamingfreaks.Bukkit.Utils;
import at.pcgamingfreaks.ConsoleColor;
import at.pcgamingfreaks.Minepacks.Bukkit.API.Backpack;
import at.pcgamingfreaks.Minepacks.Bukkit.API.Callback;
import at.pcgamingfreaks.Minepacks.Bukkit.API.MinepacksCommandManager;
import at.pcgamingfreaks.Minepacks.Bukkit.API.MinepacksPlugin;
import at.pcgamingfreaks.Minepacks.Bukkit.Command.CommandManager;
import at.pcgamingfreaks.Minepacks.Bukkit.Command.InventoryClearCommand;
import at.pcgamingfreaks.Minepacks.Bukkit.Database.Config;
import at.pcgamingfreaks.Minepacks.Bukkit.Database.Database;
import at.pcgamingfreaks.Minepacks.Bukkit.Database.Helper.WorldBlacklistMode;
import at.pcgamingfreaks.Minepacks.Bukkit.Database.Language;
import at.pcgamingfreaks.Minepacks.Bukkit.Listener.*;
import at.pcgamingfreaks.StringUtils;
import at.pcgamingfreaks.Updater.UpdateProviders.BukkitUpdateProvider;
import at.pcgamingfreaks.Updater.UpdateProviders.JenkinsUpdateProvider;
import at.pcgamingfreaks.Updater.UpdateProviders.UpdateProvider;
import at.pcgamingfreaks.Updater.UpdateResponseCallback;
import at.pcgamingfreaks.Version;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Locale;

public class Minepacks extends JavaPlugin implements MinepacksPlugin
{
	private static final int BUKKIT_PROJECT_ID = 83445;
	@SuppressWarnings("unused")
	private static final String JENKINS_URL = "https://ci.pcgamingfreaks.at", JENKINS_JOB_DEV = "Minepacks Dev", JENKINS_JOB_MASTER = "Minepacks";
	private static Minepacks instance = null;

	private Config config;
	private Language lang;
	private Database database;

	public Message messageNoPermission, messageInvalidBackpack, messageWorldDisabled, messageNotFromConsole, messageNotANumber;

	private int maxSize;
	private Collection<String> worldBlacklist;
	private WorldBlacklistMode worldBlacklistMode;
	private ItemsCollector collector;
	private CommandManager commandManager;
	private InventoryClearCommand inventoryClearCommand;
	private Collection<GameMode> gameModes;
	private CooldownManager cooldownManager = null;
	private ItemFilter itemFilter = null;
	private Sound openSound = null;

	public static Minepacks getInstance()
	{
		return instance;
	}

	@Override
	public boolean isRunningInStandaloneMode()
	{
		/*if[STANDALONE]
		return true;
		else[STANDALONE]*/
		return false;
		/*end[STANDALONE]*/
	}

	@Override
	public void onEnable()
	{
		// Check if running as standalone edition
		/*if[STANDALONE]
		getLogger().info("Starting Minepacks in standalone mode!");
		if(getServer().getPluginManager().isPluginEnabled("PCGF_PluginLib"))
		{
			getLogger().info("You do have the PCGF_PluginLib installed. You may consider switching to the default version of the plugin to reduce memory load and unlock additional features.");
		}
		else[STANDALONE]*/
		// Not standalone so we should check the version of the PluginLib
		if(at.pcgamingfreaks.PluginLib.Bukkit.PluginLib.getInstance().getVersion().olderThan(new Version(MagicValues.MIN_PCGF_PLUGIN_LIB_VERSION)))
		{
			getLogger().warning("You are using an outdated version of the PCGF PluginLib! Please update it!");
			setEnabled(false);
			return;
		}
		/*end[STANDALONE]*/


		//region Check compatibility with used minecraft version
		if(MCVersion.is(MCVersion.UNKNOWN) || !MCVersion.isUUIDsSupportAvailable() || MCVersion.isNewerThan(MCVersion.MC_NMS_1_15_R1))
		{
			this.warnOnVersionIncompatibility();
			this.setEnabled(false);
			return;
		}
		//endregion

		//region check if a plugin folder exists (was renamed from MinePacks to Minepacks with the V2.0 update)
		if(!getDataFolder().exists())
		{
			File oldPluginFolder = new File(getDataFolder().getParentFile(), "MinePacks");
			if(oldPluginFolder.exists() && !oldPluginFolder.renameTo(getDataFolder()))
			{
				getLogger().warning("Failed to rename the plugins data-folder.\n" +
						            "Please rename the \"MinePacks\" folder to \"Minepacks\" and restart the server, to move your data from Minepacks V1.X to Minepacks V2.X!");
			}
		}
		//endregion
		instance = this;
		config = new Config(this);
		lang = new Language(this);
		load();

		if(config.getAutoUpdate()) update(null);
		getLogger().info(StringUtils.getPluginEnabledMessage(getDescription().getName()));
	}

	@Override
	public void onDisable()
	{
		if(config == null) return;
		Updater updater = null;
		if(config.getAutoUpdate()) updater = update(null);
		unload();
		if(updater != null) updater.waitForAsyncOperation(); // Wait for updater to finish
		getLogger().info(StringUtils.getPluginDisabledMessage(getDescription().getName()));
		instance = null;
	}

	public @Nullable Updater update(@Nullable UpdateResponseCallback updateResponseCallback)
	{
		UpdateProvider updateProvider;
		if(getDescription().getVersion().contains("Release")) updateProvider = new BukkitUpdateProvider(BUKKIT_PROJECT_ID, getLogger());
		else
		{
			/*if[STANDALONE]
			updateProvider = new JenkinsUpdateProvider(JENKINS_URL, JENKINS_JOB_MASTER, getLogger(), ".*-Standalone.*");
			else[STANDALONE]*/
			updateProvider = new JenkinsUpdateProvider(JENKINS_URL, JENKINS_JOB_DEV, getLogger());
			/*end[STANDALONE]*/
		}
		Updater updater = new Updater(this, true, updateProvider);
		updater.update(updateResponseCallback);
		return updater;
	}

	private void load()
	{
		lang.load(config);
		database = Database.getDatabase(this);
		maxSize = config.getBackpackMaxSize();
		at.pcgamingfreaks.Minepacks.Bukkit.Backpack.setShrinkApproach(config.getShrinkApproach());
		at.pcgamingfreaks.Minepacks.Bukkit.Backpack.setTitle(config.getBPTitle(), config.getBPTitleOther());
		messageNotFromConsole  = lang.getMessage("NotFromConsole");
		messageNoPermission    = lang.getMessage("Ingame.NoPermission");
		messageInvalidBackpack = lang.getMessage("Ingame.InvalidBackpack");
		messageWorldDisabled   = lang.getMessage("Ingame.WorldDisabled");
		messageNotANumber      = lang.getMessage("Ingame.NaN");

		commandManager = new CommandManager(this);
		if(config.isInventoryManagementClearCommandEnabled()) inventoryClearCommand = new InventoryClearCommand(this);

		//region register events
		PluginManager pluginManager = getServer().getPluginManager();
		pluginManager.registerEvents(new BackpackEventListener(this), this);
		if(config.getDropOnDeath()) pluginManager.registerEvents(new DropOnDeath(this), this);
		if(config.isItemFilterEnabled())
		{
			itemFilter = new ItemFilter(this);
			pluginManager.registerEvents(itemFilter, this);
		}
		if(config.isShulkerboxesDisable()) pluginManager.registerEvents(new DisableShulkerboxes(this), this);
		if(config.isItemShortcutEnabled()) pluginManager.registerEvents(new ItemShortcut(this), this);
		//endregion
		if(config.getFullInvCollect()) collector = new ItemsCollector(this);
		worldBlacklist = config.getWorldBlacklist();
		worldBlacklistMode = (worldBlacklist.size() == 0) ? WorldBlacklistMode.None : config.getWorldBlacklistMode();

		gameModes = config.getAllowedGameModes();
		if(config.getCommandCooldown() > 0) cooldownManager = new CooldownManager(this);

		openSound = config.getOpenSound();
	}

	private void unload()
	{
		if(inventoryClearCommand != null)
		{
			inventoryClearCommand.close();
			inventoryClearCommand = null;
		}
		if(collector != null) collector.close();
		commandManager.close();
		if(collector != null) collector.cancel();
		if(database != null) database.close(); // Close the DB connection, we won't need them any longer
		HandlerList.unregisterAll(this); // Stop the listeners
		if(cooldownManager != null) cooldownManager.close();
		cooldownManager = null;
		getServer().getScheduler().cancelTasks(this); // Kill all running task
		itemFilter = null;
	}

	public void reload()
	{
		unload();
		config.reload();
		load();
	}

	public void warnOnVersionIncompatibility()
	{
		String name = Bukkit.getServer().getClass().getPackage().getName();
		String[] version = name.substring(name.lastIndexOf('.') + 2).split("_");
		getLogger().warning(ConsoleColor.RED + "################################" + ConsoleColor.RESET);
		getLogger().warning(ConsoleColor.RED + String.format("Your minecraft version (MC %1$s) is currently not compatible with this plugins version (%2$s). " +
				                                                     "Please check for updates!", version[0] + "." + version[1], getDescription().getVersion()) + ConsoleColor.RESET);
		getLogger().warning(ConsoleColor.RED + "################################" + ConsoleColor.RESET);
		Utils.blockThread(5);
	}

	public Config getConfiguration()
	{
		return config;
	}

	public Language getLanguage()
	{
		return lang;
	}

	public Database getDatabase()
	{
		return database;
	}

	@Override
	public void openBackpack(@NotNull final Player opener, @NotNull final OfflinePlayer owner, final boolean editable)
	{
		openBackpack(opener, owner, editable, null);
	}

	@Override
	public void openBackpack(@NotNull final Player opener, @Nullable final Backpack backpack, boolean editable)
	{
		openBackpack(opener, backpack, editable, null);
	}

	@Override
	public void openBackpack(@NotNull Player opener, @NotNull OfflinePlayer owner, boolean editable, @Nullable String title)
	{
		database.getBackpack(owner, new Callback<Backpack>()
		{
			@Override
			public void onResult(Backpack backpack)
			{
				openBackpack(opener, backpack, editable, title);
			}

			@Override
			public void onFail() {}
		});
	}

	@Override
	public void openBackpack(@NotNull Player opener, @Nullable Backpack backpack, boolean editable, @Nullable String title)
	{
		WorldBlacklistMode disabled = isDisabled(opener);
		if(disabled != WorldBlacklistMode.None)
		{
			switch(disabled)
			{
				case Message: messageWorldDisabled.send(opener); break;
				case MissingPermission: messageNoPermission.send(opener); break;
			}
			return;
		}
		if(backpack == null)
		{
			messageInvalidBackpack.send(opener);
			return;
		}
		//noinspection ObjectEquality
		if(opener.getOpenInventory().getTopInventory().getHolder() == backpack) return; // == is fine as there is only one instance of each backpack
		if(openSound != null)
		{
			opener.getWorld().playSound(opener.getLocation(), openSound, 1, 0);
		}
		backpack.open(opener, editable);
	}

	@Override
	public @Nullable Backpack getBackpackCachedOnly(@NotNull OfflinePlayer owner)
	{
		return database.getBackpack(owner);
	}

	@Override
	public void getBackpack(@NotNull OfflinePlayer owner, @NotNull Callback<Backpack> callback)
	{
		database.getBackpack(owner, callback);
	}

	@Override
	public void getBackpack(@NotNull final OfflinePlayer owner, @NotNull final Callback<Backpack> callback, boolean createNewIfNotExists)
	{
		database.getBackpack(owner, callback, createNewIfNotExists);
	}

	@Override
	public MinepacksCommandManager getCommandManager()
	{
		/*if[STANDALONE]
		return null;
		else[STANDALONE]*/
		return commandManager;
		/*end[STANDALONE]*/
	}

	public int getBackpackPermSize(Player player)
	{
		for(int i = maxSize; i > 1; i--)
		{
			if(player.hasPermission("backpack.size." + i)) return i * 9;
		}
		return 9;
	}

	public WorldBlacklistMode isDisabled(Player player)
	{
		if(worldBlacklistMode == WorldBlacklistMode.None || (worldBlacklistMode != WorldBlacklistMode.NoPlugin && player.hasPermission(Permissions.IGNORE_WORLD_BLACKLIST))) return WorldBlacklistMode.None;
		if(worldBlacklist.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) return worldBlacklistMode;
		return WorldBlacklistMode.None;
	}

	@Override
	public boolean isPlayerGameModeAllowed(Player player)
	{
		return gameModes.contains(player.getGameMode()) || player.hasPermission(Permissions.IGNORE_GAME_MODE);
	}

	public @Nullable CooldownManager getCooldownManager()
	{
		return cooldownManager;
	}

	@Override
	public @Nullable ItemFilter getItemFilter()
	{
		return itemFilter;
	}
}