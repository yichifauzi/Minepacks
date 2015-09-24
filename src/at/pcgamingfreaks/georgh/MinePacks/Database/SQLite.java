/*
 *   Copyright (C) 2014-2015 GeorgH93
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

package at.pcgamingfreaks.georgh.MinePacks.Database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import at.pcgamingfreaks.georgh.MinePacks.MinePacks;

public class SQLite extends SQL
{
	public SQLite(MinePacks mp)
	{
		super(mp);
	}

	@Override
	protected void loadSettings()
	{
		// Set table and field names to fixed values to prevent users from destroying old databases.
		Field_PlayerID = "player_id";
		Field_Name = "name";
		Field_UUID = "uuid";
		Field_BPOwner = "owner";
		Field_BPITS = "itemstacks";
		Field_BPVersion = "version";
		Field_BPLastUpdate = "lastupdate";
		Table_Players = "backpack_players";
		Table_Backpacks = "backpacks";
		// Set fixed settings
		useUUIDSeparators = false;
		UpdatePlayer = true;
	}

	@Override
	protected void updateQuerysForDialect()
	{
		if(maxAge > 0)
		{
			Query_InsertBP = Query_InsertBP.replaceAll("\\) VALUES \\(\\?,\\?,\\?", "{FieldBPLastUpdate}) VALUES (?,?,?,DATE('now')");
		}
		Query_DeleteOldBackpacks = "DELETE FROM `{TableBackpacks}` WHERE `{FieldBPLastUpdate}` < DATE('now', '-{VarMaxAge} days')";
		Query_UpdateBP = Query_UpdateBP.replaceAll("\\{NOW\\}", "DATE('now')");
	}

	@Override
	protected Connection getConnection()
	{
		try
		{
			if(conn == null || conn.isClosed())
			{
				Class.forName("org.sqlite.JDBC"); // Throws an exception if the SQLite driver is not found.
				conn = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + File.separator + "backpack.db");
			}
		}
		catch (ClassNotFoundException | SQLException e)
		{
			e.printStackTrace();
		}
		return conn;
	}

	@Override
	protected void checkDB()
	{
		try
		{
			Statement stmt = getConnection().createStatement();
			stmt.execute("CREATE TABLE IF NOT EXISTS `backpack_players` (`player_id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` CHAR(16) NOT NULL UNIQUE" + ((useUUIDs) ? ", `uuid` CHAR(32) UNIQUE" : "") +");");
			if(useUUIDs)
			{
				try
				{
					stmt.execute("ALTER TABLE `backpack_players` ADD COLUMN `uuid` CHAR(32);");
				}
				catch(SQLException ignored) {}
			}
			stmt.execute("CREATE TABLE IF NOT EXISTS `backpacks` (`owner` INT UNSIGNED PRIMARY KEY, `itemstacks` BLOB, `version` INT DEFAULT 0);");
			try
			{
				stmt.execute("ALTER TABLE `backpacks` ADD COLUMN `version` INT DEFAULT 0;");
			}
			catch(SQLException ignored) {}
			if(maxAge > 0)
			{
				try
				{
					ResultSet rs = stmt.executeQuery("SELECT DATE('now');");
					rs.next();
					stmt.execute("ALTER TABLE `backpacks` ADD COLUMN `lastupdate` DATE DEFAULT '" + rs.getString(1) + "';");
				}
				catch(SQLException ignored) {}
			}
			stmt.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
}