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

package at.pcgamingfreaks.georgh.MinePacks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class Backpack implements InventoryHolder
{
	private OfflinePlayer owner;
	private HashMap<Player, Boolean> opened = new HashMap<>();
	private Inventory bp;
	private int size, ownerID;
	private String title;
	private boolean inWork, hasChanged;
	
	public Backpack(OfflinePlayer owner)
	{
		this(owner, 9);
	}
	
	public Backpack(OfflinePlayer owner, int size)
	{
		this(owner, size, -1);
	}

	public Backpack(OfflinePlayer owner, int Size, int ID)
	{
		this.owner = owner;
		title = String.format(MinePacks.BackpackTitle, owner.getName());
		bp = Bukkit.createInventory(this, Size, title);
		size = Size;
		ownerID = ID;
		inWork = false;
	}
	
	public Backpack(OfflinePlayer owner, ItemStack[] backpack, int ID)
	{
		this(owner, backpack.length, ID);
		bp.setContents(backpack);
	}
	
	public int getOwnerID()
	{
		return ownerID;
	}
	
	public void setOwnerID(int id)
	{
		ownerID = id;
	}
	
	public OfflinePlayer getOwner()
	{
		return owner;
	}
	
	public void open(Player p, boolean editable)
	{
		if(owner.isOnline())
		{
			Player player = owner.getPlayer();
			if(player != null)
			{
				int size = MinePacks.getInstance().getBackpackPermSize(player);
				if(size != bp.getSize())
				{
					List<ItemStack> items = setSize(size);
					for(ItemStack i : items)
					{
						if (i != null)
						{
							player.getWorld().dropItemNaturally(player.getLocation(), i);
						}
					}
				}
			}
		}
		opened.put(p, editable);
		p.openInventory(bp);
	}
	
	public void close(Player p)
	{
		opened.remove(p);
	}
	
	public boolean isOpen()
	{
		return !opened.isEmpty();
	}
	
	public boolean canEdit(Player p)
	{
		return opened.containsKey(p) && opened.get(p);
	}
	
	public boolean inUse()
	{
		return inWork;
	}

	@SuppressWarnings("unused")
	public int getSize()
	{
		return size;
	}
	
	public List<ItemStack> setSize(int newSize)
	{
		inWork = true;
		for(Entry<Player, Boolean> e : opened.entrySet())
		{
			e.getKey().closeInventory();
		}
		List<ItemStack> removedItems = new ArrayList<>();
		ItemStack[] itemStackArray;
		if(bp.getSize() > newSize)
		{
			int count = 0;
			itemStackArray = new ItemStack[newSize];
			for(ItemStack i : bp.getContents())
			{
				if(i != null)
				{
					if(count < newSize)
					{
						itemStackArray[count] = i;
						count++;
					}
					else
					{
						removedItems.add(i);
					}
				}
			}
		}
		else
		{
			itemStackArray = bp.getContents();
		}
		bp = Bukkit.createInventory(bp.getHolder(), newSize, title);
		for(int i = 0; i < itemStackArray.length; i++)
		{
			bp.setItem(i, itemStackArray[i]);
		}
		size = newSize;
		for(Entry<Player, Boolean> e : opened.entrySet())
		{
			e.getKey().openInventory(bp);
		}
		inWork = false;
		return removedItems;
	}

	/**
	 * Get the object's inventory.
	 *
	 * @return The inventory.
	 */
	public Inventory getInventory()
	{
		return bp;
	}

	@SuppressWarnings("unused")
	public boolean hasChanged()
	{
		return hasChanged;
	}

	public void setChanged()
	{
		hasChanged = true;
	}

	public void save()
	{
		if(hasChanged)
		{
			MinePacks.getInstance().DB.saveBackpack(this);
			hasChanged = false;
		}
	}
}